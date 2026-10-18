package com.ultimateimprovments.config;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * ConfigReadGuard — bounded time for reading/parsing one config file.
 * <p>
 * A config file that has grown huge (a runaway writer bloated a TOML to
 * megabytes) used to stall the whole server startup: toml4j ground through
 * the file for minutes on the main thread. Every config read now goes
 * through {@link #read(File, Supplier)} — the parse runs on a daemon worker
 * thread with the {@code config.read_timeout_sec} budget (default 5s); when
 * the budget is blown, {@link ReadTimeoutException} is raised and the caller
 * treats the file as broken, falling into the normal recovery (back up as
 * {@code UI-<Addon>-broken-<N>.toml} + regenerate from the bundled template).
 * <p>
 * Note: the abandoned worker cannot be force-killed — a CPU-bound parser
 * finishes (or spins) in the background as a daemon and dies with the JVM;
 * the main thread never waits for it.
 */
public final class ConfigReadGuard {

    /** Raised when a config read exceeded the configured budget. */
    public static final class ReadTimeoutException extends RuntimeException {
        ReadTimeoutException(String message) { super(message); }
    }

    /** Built-in budget for the very first read (before the config can speak). */
    private static final int DEFAULT_TIMEOUT_SEC = 5;

    private static volatile int timeoutSec = DEFAULT_TIMEOUT_SEC;

    private ConfigReadGuard() {}

    /** Current read budget in seconds ({@code config.read_timeout_sec}, default 5). */
    public static int timeoutSec() { return timeoutSec; }

    /** Updates the budget, clamped to 1..300 seconds. */
    public static void setTimeoutSec(int seconds) {
        timeoutSec = Math.max(1, Math.min(300, seconds));
    }

    /**
     * Runs a blocking read/parse with the configured budget.
     *
     * @throws ConfigReadGuard.ReadTimeoutException when the parse exceeds the budget
     * @throws RuntimeException                      when the parse itself failed (wrapping the cause)
     */
    public static <T> T read(File file, Supplier<T> parse) {
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ConfigRead-" + file.getName());
            t.setDaemon(true);
            return t;
        });
        try {
            Future<T> future = worker.submit(parse::get);
            try {
                return future.get(timeoutSec, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new ReadTimeoutException("config read timed out after " + timeoutSec
                        + "s (file size: " + String.format("%.1f", file.length() / 1024.0 / 1024.0)
                        + " MB): " + file.getName());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new RuntimeException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while reading " + file.getName(), e);
            }
        } finally {
            worker.shutdownNow();
        }
    }
}
