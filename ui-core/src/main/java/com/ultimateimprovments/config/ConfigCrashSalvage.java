package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🧯 ConfigCrashSalvage — "single-config salvage": only the broken lines are commented out.
 * <p>
 * When config.yml fails to parse (a YAML syntax error in a section), instead of deleting
 * the whole file (old behavior) or removing/backing-up a whole section, we comment out ONLY
 * the offending line(s) and re-parse:
 * <ol>
 *   <li>Try to parse the file.</li>
 *   <li>On error, locate the problem line and comment it out (prefix {@code # }).</li>
 *   <li>Repeat until the file parses (handles several consecutive errors).</li>
 *   <li>The commented-out parameter (and its whole subtree) is ignored — the default value
 *       from the reference config (in the UI-Core JAR) is used instead via {@link ConfigRepairManager}.</li>
 * </ol>
 * There is no {@code config-broken} folder and no whole-file deletion any more — the user's
 * data is always kept, only the problem lines are hidden behind a comment.
 * <p>
 * If the file really cannot be stabilised (theoretical case) we return {@code false}, but the
 * caller no longer recreates the file from the JAR with data loss: it just continues, falling
 * back to defaults from the reference config.
 */
public final class ConfigCrashSalvage {

    /** Legacy backup-folder name — kept for compatibility, no longer used. */
    public static final String BACKUP_DIR_NAME = "config-broken";

    private static final int MAX_ROUNDS = 1000;
    private static final Pattern LINE_IN_MESSAGE = Pattern.compile("(?i)line\\s+(\\d+)");

    private ConfigCrashSalvage() {}

    /** Result of a salvage operation. */
    public static final class Result {
        /** true — the file now parses (problem lines commented out). */
        public final boolean success;
        /** Commented line numbers (1-based). */
        public final List<Integer> commentedLines;
        /** Human-readable result description. */
        public final String message;

        Result(boolean success, List<Integer> commentedLines, String message) {
            this.success = success;
            this.commentedLines = commentedLines;
            this.message = message;
        }
    }

    // ========================================================================
    // ENTRY POINTS
    // ========================================================================

    /**
     * Tries to fix {@code plugins/<plugin>/config.yml} after a failed load by commenting
     * out only the problem lines. Never deletes the file and never creates backups.
     *
     * @return true if the file now parses (even if lines had to be commented out)
     */
    public static boolean salvage(Main plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        Result result = salvageFile(configFile, msg -> ConsoleLogger.warn("[ConfigSalvage] " + msg));

        for (int line : result.commentedLines) {
            ConsoleLogger.warn("[ConfigSalvage] \u26A0 Commented broken line " + (line + 1)
                    + " — the default value from the reference config will be used");
        }
        if (result.success) {
            ConsoleLogger.info("[ConfigSalvage] \u2714 " + result.message);
        } else {
            ConsoleLogger.warn("[ConfigSalvage] ✗ " + result.message
                    + " — falling back to defaults from the jar reference");
        }
        return result.success;
    }

    /**
     * Pure salvage logic — no Bukkit/Main, testable in JUnit.
     * <p>
     * Comments out every line that prevents parsing and saves the file.
     *
     * @param configFile the config file
     * @param log        log-string consumer (can be a no-op)
     */
    public static Result salvageFile(File configFile, Consumer<String> log) {
        if (!configFile.exists()) {
            return new Result(false, new ArrayList<>(), "config.yml does not exist");
        }

        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new Result(false, new ArrayList<>(), "cannot read config.yml: " + e.getMessage());
        }

        // Already parses — nothing to salvage
        Throwable[] lastError = new Throwable[1];
        if (parses(lines, lastError)) {
            return new Result(true, new ArrayList<>(), "config.yml is valid, nothing to salvage");
        }

        List<Integer> commented = new ArrayList<>();
        int rounds = 0;

        while (true) {
            if (++rounds > MAX_ROUNDS) {
                return new Result(false, commented,
                        "could not stabilize config.yml after " + MAX_ROUNDS + " attempts");
            }

            if (parses(lines, lastError)) break;

            int errorLine = findErrorLine(lastError[0]);
            // 0-based; if we cannot determine the line, start from the last line of the file
            if (errorLine < 0) errorLine = lines.isEmpty() ? 0 : lines.size() - 1;
            if (errorLine >= lines.size()) errorLine = lines.size() - 1;

            String raw = lines.get(errorLine);
            // If already commented, step upward to the nearest not-yet-touched line
            if (isCommentOrBlank(raw)) {
                int cursor = errorLine;
                boolean found = false;
                while (cursor > 0) {
                    cursor--;
                    if (!isCommentOrBlank(lines.get(cursor))) { found = true; break; }
                }
                if (!found) {
                    return new Result(false, commented, "cannot stabilize — no commentable line near the error");
                }
                errorLine = cursor;
            }

            String line = lines.get(errorLine);
            lines.set(errorLine, "# " + line);
            commented.add(errorLine + 1);
            log.accept("Commented broken line " + (errorLine + 1) + ": " + line.trim());
        }

        if (!commented.isEmpty()) {
            try {
                Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return new Result(false, commented, "cannot write salvaged config.yml: " + e.getMessage());
            }
        }

        String summary = commented.isEmpty()
                ? "config.yml is valid, nothing to salvage"
                : "commented " + commented.size() + " broken line(s) — defaults will be used for them";
        log.accept(summary);
        return new Result(true, commented, summary);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static boolean isCommentOrBlank(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    /**
     * Tries to determine the (1-based) error line number from the exception chain:
     * SnakeYAML {@code MarkedYAMLException.getProblemMark().getLine()}, a {@code getLineNumber()}
     * getter (some Paper wrappers), or a regex over the message text.
     *
     * @return the line number (1-based) or -1 if it could not be determined
     */
    static int findErrorLine(Throwable error) {
        if (error == null) return -1;

        // 1) reflection getters over the whole cause chain
        for (Throwable t = error; t != null; t = t.getCause()) {
            Integer line = reflectionLine(t);
            if (line != null && line > 0) return line;
        }
        // 2) message text: "...line 12, column 3..."
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() == null) continue;
            Matcher m = LINE_IN_MESSAGE.matcher(t.getMessage());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    // try the next message
                }
            }
        }
        return -1;
    }

    private static Integer reflectionLine(Throwable t) {
        // 1) getLineNumber() — e.g. Paper/wrapper exceptions
        try {
            Object val = t.getClass().getMethod("getLineNumber").invoke(t);
            if (val instanceof Number n) return n.intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no such method
        }
        // 2) SnakeYAML MarkedYAMLException → getProblemMark().getLine() (0-based)
        try {
            Class<?> cls = Class.forName("org.yaml.snakeyaml.error.MarkedYAMLException");
            if (cls.isInstance(t)) {
                Object mark = cls.getMethod("getProblemMark").invoke(t);
                if (mark != null) {
                    Object line = mark.getClass().getMethod("getLine").invoke(mark);
                    if (line instanceof Number n) return n.intValue() + 1;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // SnakeYAML not on the classpath — fine, we have the message fallback
        }
        return null;
    }

    /**
     * Tries to parse the lines as a YAML config. On error the exception is stored in {@code lastError[0]}.
     * <p>
     * IMPORTANT: we use {@code loadFromString} (throws {@code InvalidConfigurationException}),
     * not {@code loadConfiguration} — Paper-26 silently swallows the parse error (logs it and
     * returns an empty config), which would make a broken file look "valid".
     */
    private static boolean parses(List<String> lines, Throwable[] lastError) {
        try {
            new YamlConfiguration().loadFromString(String.join("\n", lines));
            lastError[0] = null;
            return true;
        } catch (Throwable t) {
            lastError[0] = t;
            return false;
        }
    }
}