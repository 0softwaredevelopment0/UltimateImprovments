package com.ultimateimprovments.mechanics.benchmark;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.UIGuard;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * StressTestManager — the engine behind {@code /ui stresstest start|stop}.
 * <p>
 * A run has two phases:
 * <ul>
 *   <li><b>WARMUP</b> ({@code stresstest.warmup_seconds}) — the load generator
 *       is prepared but not ticking yet; MSPT/TPS/RAM samples taken here form
 *       the baseline the report is compared against;</li>
 *   <li><b>RUNNING</b> — a 1-tick repeating task drives
 *       {@link StressLoad#tick()} every {@code stresstest.interval_ticks} and
 *       samples the server every second until the run is stopped by the
 *       command, by {@code stresstest.max_duration_seconds} or by a reload /
 *       shutdown.</li>
 * </ul>
 * Everything the load generators touch (spawned entities, block snapshots,
 * chunk tickets) is released by {@link StressLoad#stop()}, which is always
 * called — on an explicit stop, on a timeout, on an error and on disable.
 */
public final class StressTestManager {

    /** Prefix used for every console line this manager logs. */
    private static final String LOG_PREFIX = "[StressTest] ";

    /** Why a run ended — mapped to {@code stresstest.reason.<id>} messages. */
    public enum Reason {
        /** {@code /ui stresstest stop} */
        MANUAL("manual"),
        /** {@code stresstest.max_duration_seconds} elapsed */
        DURATION("duration"),
        /** Plugin/module disabled (server shutdown, {@code /ui reload}) */
        DISABLE("disable"),
        /** The load generator threw */
        ERROR("error");

        private final String id;

        Reason(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private enum Phase { IDLE, WARMUP, RUNNING }

    /** One per-second server sample. */
    private record Sample(double mspt, double tps, double ram, int entities) {}

    private static StressTestManager instance;

    private Phase phase = Phase.IDLE;
    private StressTestType type;
    private StressPower power;
    private StressLoad load;
    private CommandSender initiator;
    private Location anchor;

    private BukkitTask task;
    private int tickCounter;
    private int warmupLeft;
    private int intervalTicks;
    private long maxDurationMillis;
    private long runStartedAt;
    private long loadStartedAt;

    private final List<Sample> baseline = new ArrayList<>();
    private final List<Sample> running = new ArrayList<>();

    private StressTestManager() {}

    // ============================================================
    // LIFECYCLE (module hooks)
    // ============================================================

    /** Called by the {@code StressTest} module on init. */
    public static void init() {
        instance = new StressTestManager();
        instance.scheduleLeftoverSweep();
    }

    public static StressTestManager getInstance() {
        return instance;
    }

    /** Called by the {@code StressTest} module on disable/reload. */
    public static void shutdown() {
        StressTestManager current = instance;
        instance = null;
        if (current != null) {
            current.finish(Reason.DISABLE, true, null);
        }
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /** @return true while a run is in the warmup or running phase */
    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    /**
     * Starts a new run: prepares the load generator, collects the baseline
     * during the warmup and then starts producing load.
     *
     * @return true when the run was accepted (already-running/failed starts
     *         report their own message to the sender)
     */
    public boolean start(CommandSender sender, StressTestType newType, StressPower newPower) {
        if (phase != Phase.IDLE) {
            send(sender, "stresstest.already_running",
                    "<yellow>⚠</yellow> <white>A stress test is already running (</white><yellow>"
                            + "%type% / %power%" + "</yellow><white>). Stop it first: </white>"
                            + "<white>/ui stresstest stop</white>",
                    "%type%", typeId(), "%power%", powerId());
            return false;
        }

        Location newAnchor = resolveAnchor(sender);
        if (newAnchor == null) {
            send(sender, "stresstest.start_failed",
                    "<red>❌</red> <white>Could not start the stress test: </white><gray>%error%</gray>",
                    "%error%", "no world loaded");
            return false;
        }

        StressLoad newLoad = newType == StressTestType.ENTITY ? new EntityLoad(newPower)
                : newType == StressTestType.BLOCK ? new BlockLoad(newPower)
                : newType == StressTestType.CHUNK ? new ChunkLoad(newPower)
                : new SelectorLoad(newPower);
        try {
            newLoad.start(newAnchor);
        } catch (Throwable t) {
            try {
                newLoad.stop();
            } catch (Throwable ignored) {
                // Best-effort rollback of a half-prepared generator.
            }
            ConsoleLogger.error(LOG_PREFIX + "Failed to start " + newType.id() + "/" + newPower.id()
                    + ": " + t);
            send(sender, "stresstest.start_failed",
                    "<red>❌</red> <white>Could not start the stress test: </white><gray>%error%</gray>",
                    "%error%", String.valueOf(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            return false;
        }

        FileConfiguration cfg = cfg();
        this.type = newType;
        this.power = newPower;
        this.load = newLoad;
        this.initiator = sender;
        this.anchor = newAnchor;
        this.tickCounter = 0;
        this.runStartedAt = System.currentTimeMillis();
        this.intervalTicks = clamp(cfg.getInt("stresstest.interval_ticks", 1), 1, 20);
        int warmupSeconds = clamp(cfg.getInt("stresstest.warmup_seconds", 3), 0, 60);
        this.warmupLeft = warmupSeconds * 20;
        this.maxDurationMillis = clamp(cfg.getInt("stresstest.max_duration_seconds", 300), 0, 86400) * 1000L;
        this.baseline.clear();
        this.running.clear();

        JavaPlugin plugin = ownerPlugin();
        if (plugin == null) {
            newLoad.stop();
            resetAfterFailedStart();
            send(sender, "stresstest.start_failed",
                    "<red>❌</red> <white>Could not start the stress test: </white><gray>%error%</gray>",
                    "%error%", "UI-Guard is not loaded");
            return false;
        }

        try {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    onTick();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } catch (Throwable t) {
            // The scheduler refused the task (plugin disabling) — roll the
            // whole run back, otherwise the generator would leak un-stopped.
            ConsoleLogger.error(LOG_PREFIX + "Scheduler rejected the run task: " + t);
            try {
                newLoad.stop();
            } catch (Throwable ignored) {
                // Best-effort rollback.
            }
            resetAfterFailedStart();
            send(sender, "stresstest.start_failed",
                    "<red>❌</red> <white>Could not start the stress test: </white><gray>%error%</gray>",
                    "%error%", "scheduler rejected the run task");
            return false;
        }

        if (warmupLeft <= 0) {
            beginLoad();
        } else {
            phase = Phase.WARMUP;
            send(sender, "stresstest.warmup",
                    "<yellow>⏳</yellow> <white>Stress test </white><yellow>%type% / %power%"
                            + "</white><gray>: measuring the baseline for </gray><yellow>%seconds%s</yellow><gray>…</gray>",
                    "%type%", type.id(), "%power%", power.id(), "%seconds%", String.valueOf(warmupSeconds));
        }

        alertStarted(sender);
        return true;
    }

    /**
     * Stops the active run and reports the outcome to the original initiator
     * and to {@code caller} (when it is a different sender).
     */
    public void stop(CommandSender caller) {
        finish(Reason.MANUAL, false, caller);
    }

    /** Stops the active run (no-op when nothing is running). */
    public void stop() {
        finish(Reason.MANUAL, false, null);
    }

    /** Reports the outcome of a stop attempt to the given sender. */
    public void reportNotRunning(CommandSender sender) {
        send(sender, "stresstest.not_running",
                "<yellow>⚠</yellow> <white>No stress test is running.</white>");
    }

    // ============================================================
    // TICK DRIVING
    // ============================================================

    private void onTick() {
        if (phase == Phase.IDLE) {
            return;
        }
        tickCounter++;
        if (tickCounter % 20 == 0) {
            sample();
        }

        if (phase == Phase.WARMUP) {
            if (--warmupLeft <= 0) {
                beginLoad();
            }
            return;
        }

        if (intervalTicks <= 1 || tickCounter % intervalTicks == 0) {
            try {
                load.tick();
            } catch (Throwable t) {
                ConsoleLogger.error(LOG_PREFIX + "Load generator failed: " + t);
                // consoleOnly=false: the initiator must learn the run died.
                finish(Reason.ERROR, false, null);
                return;
            }
        }

        if (maxDurationMillis > 0 && System.currentTimeMillis() - loadStartedAt >= maxDurationMillis) {
            ConsoleLogger.warn(LOG_PREFIX + "Auto-stopped after reaching stresstest.max_duration_seconds.");
            finish(Reason.DURATION, false, null);
        }
    }

    /** Warmup is over — the load generator starts producing work. */
    private void beginLoad() {
        phase = Phase.RUNNING;
        loadStartedAt = System.currentTimeMillis();
        send(initiator, "stresstest.started",
                "<green>🔥</green> <white>Stress test </white><yellow>%type% / %power%"
                        + "</white> <green>is producing load</green><gray> — stop it with </gray>"
                        + "<white>/ui stresstest stop</white>",
                "%type%", type.id(), "%power%", power.id());
    }

    /** Takes one per-second sample of the current phase. */
    private void sample() {
        double tps = 20.0D;
        double[] all = Bukkit.getTPS();
        if (all != null && all.length > 0) {
            tps = Math.min(all[0], 20.0D);
        }
        double mspt = Bukkit.getAverageTickTime();
        Runtime runtime = Runtime.getRuntime();
        double ram = (runtime.totalMemory() - runtime.freeMemory()) * 100.0D / runtime.maxMemory();
        Sample s = new Sample(mspt, tps, ram, countEntities());
        (phase == Phase.WARMUP ? baseline : running).add(s);
    }

    // ============================================================
    // FINISH + REPORT
    // ============================================================

    /**
     * Ends the current run: cancels the task, stops the generator (restoring
     * every world change) and reports the benchmark result.
     *
     * @param consoleOnly when true the report is only written to the console
     *                    (used for shutdown/reload, when the initiator may be gone)
     * @param extra       an additional sender that gets the report too (e.g. a
     *                    second admin who ran {@code /ui stresstest stop});
     *                    skipped when it is the initiator itself or null
     */
    private void finish(Reason reason, boolean consoleOnly, CommandSender extra) {
        if (phase == Phase.IDLE) {
            return;
        }
        Phase endedPhase = phase;
        phase = Phase.IDLE;

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (endedPhase == Phase.RUNNING) {
            sample();
        }
        // Entity count must be captured BEFORE the generator removes its own
        // entities, otherwise the report's "Entities" column is meaningless.
        int endEntities = countEntities();

        StressLoad finished = load;
        load = null;
        long work = 0;
        if (finished != null) {
            try {
                work = finished.work();
            } catch (Throwable ignored) {
                // A broken counter must not hide the cleanup below.
            }
            try {
                finished.stop();
            } catch (Throwable t) {
                ConsoleLogger.error(LOG_PREFIX + "Cleanup failed: " + t);
            }
        }

        boolean reportEnabled = cfg().getBoolean("stresstest.log_report", true);
        List<String> lines = buildReport(reason, work, endEntities);
        if (reportEnabled) {
            for (String line : lines) {
                ConsoleLogger.info(LOG_PREFIX + stripMini(line));
            }
        }
        if (!consoleOnly) {
            deliverReport(initiator, lines);
            if (extra != null && extra != initiator) {
                deliverReport(extra, lines);
            }
        }

        phase = Phase.IDLE;
        type = null;
        power = null;
        initiator = null;
        anchor = null;
        warmupLeft = 0;
        intervalTicks = 1;
        maxDurationMillis = 0;
        runStartedAt = 0L;
        loadStartedAt = 0L;
        baseline.clear();
        running.clear();
    }

    /** Rolls every run field back after a start that never began ticking. */
    private void resetAfterFailedStart() {
        phase = Phase.IDLE;
        task = null;
        load = null;
        type = null;
        power = null;
        initiator = null;
        anchor = null;
        tickCounter = 0;
        warmupLeft = 0;
        intervalTicks = 1;
        maxDurationMillis = 0;
        runStartedAt = 0L;
        loadStartedAt = 0L;
        baseline.clear();
        running.clear();
    }

    /** Sends the report lines to one recipient, tolerating a gone recipient. */
    private static void deliverReport(CommandSender to, List<String> lines) {
        if (to == null) return;
        for (String line : lines) {
            try {
                to.sendMessage(MessageUtil.parse(line));
            } catch (Throwable ignored) {
                // The recipient may have disconnected between stop and report.
            }
        }
    }

    /** Assembles the benchmark report as a list of MiniMessage lines. */
    private List<String> buildReport(Reason reason, long work, int endEntities) {
        String typeLabel = typeId();
        String powerLabel = powerId();
        // Load duration when the load phase was reached, total run duration
        // otherwise (a stop during the warmup must not report 0.0s).
        long startedAt = loadStartedAt > 0 ? loadStartedAt : runStartedAt;
        double durationSeconds = startedAt > 0
                ? (System.currentTimeMillis() - startedAt) / 1000.0D
                : 0.0D;
        String reasonText = msg("stresstest.reason." + reason.id(), "<gray>" + reason.id() + "</gray>");

        List<String> lines = new ArrayList<>();
        lines.add(msg("stresstest.report_header",
                "<gray>══ Stress test: </gray><yellow>%type% / %power%</yellow>"
                        + "<gray> — </gray><yellow>%duration%s</yellow><gray> (</gray>%reason%<gray>) ══</gray>",
                "%type%", typeLabel, "%power%", powerLabel,
                "%duration%", String.format(Locale.ROOT, "%.1f", durationSeconds),
                "%reason%", reasonText));

        lines.add(msg("stresstest.report_stats",
                "<gray>MSPT:</gray> <white>%mspt_base%</white> <dark_gray>→</dark_gray> <yellow>%mspt_avg%"
                        + "</yellow><gray> avg / </gray><red>%mspt_max%</red><gray> max"
                        + "</gray>   <gray>TPS:</gray> <white>%tps_base%</white> <dark_gray>→</dark_gray>"
                        + " <yellow>%tps_avg%</yellow><gray> avg / </gray><red>%tps_min%</red><gray> min</gray>",
                "%mspt_base%", stat(baseline, Field.MSPT, Stat.AVG, Format.DECIMAL),
                "%mspt_avg%", stat(running, Field.MSPT, Stat.AVG, Format.DECIMAL),
                "%mspt_max%", stat(running, Field.MSPT, Stat.MAX, Format.DECIMAL),
                "%tps_base%", stat(baseline, Field.TPS, Stat.AVG, Format.DECIMAL),
                "%tps_avg%", stat(running, Field.TPS, Stat.AVG, Format.DECIMAL),
                "%tps_min%", stat(running, Field.TPS, Stat.MIN, Format.DECIMAL)));

        lines.add(msg("stresstest.report_memory",
                "<gray>RAM:</gray> <white>%ram_base%</white> <dark_gray>→</dark_gray> <yellow>%ram_avg%"
                        + "</yellow><gray> avg / </gray><red>%ram_max%</red><gray> max</gray>"
                        + "   <gray>Entities:</gray> <white>%entities%</white>",
                "%ram_base%", stat(baseline, Field.RAM, Stat.AVG, Format.PERCENT),
                "%ram_avg%", stat(running, Field.RAM, Stat.AVG, Format.PERCENT),
                "%ram_max%", stat(running, Field.RAM, Stat.MAX, Format.PERCENT),
                "%entities%", String.valueOf(endEntities)));

        String unit = msg("stresstest.units." + typeLabel, "<white>work units</white>");
        lines.add(msg("stresstest.report_work",
                "<gray>Work:</gray> <yellow>%work%</yellow> <white>%unit%</white>",
                "%work%", String.valueOf(work), "%unit%", unit));

        return lines;
    }

    /** Which server metric a statistic is computed over. */
    private enum Field { MSPT, TPS, RAM }

    /** Which statistic of a metric to compute. */
    private enum Stat { AVG, MIN, MAX }

    /** How to render a formatted value. */
    private enum Format { DECIMAL, PERCENT }

    /**
     * Formats one statistic of a sample list.
     *
     * @return the formatted value, or {@code "n/a"} when there is no sample
     *         (a run stopped during the warmup has no baseline, a run stopped
     *         before the first second has no running samples)
     */
    private static String stat(List<Sample> samples, Field field, Stat stat, Format format) {
        if (samples.isEmpty()) return "n/a";
        double sum = 0.0D;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Sample sample : samples) {
            double value = value(sample, field);
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double result = switch (stat) {
            case AVG -> sum / samples.size();
            case MIN -> min;
            case MAX -> max;
        };
        if (format == Format.PERCENT) {
            return String.format(Locale.ROOT, "%.0f%%", result);
        }
        return String.format(Locale.ROOT, "%.2f", result);
    }

    private static double value(Sample sample, Field field) {
        return switch (field) {
            case MSPT -> sample.mspt();
            case TPS -> sample.tps();
            case RAM -> sample.ram();
        };
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /** The composite (per-addon TOML routed) configuration. */
    public static FileConfiguration cfg() {
        return Main.getInstance().getConfig();
    }

    /** Plugin that owns the scheduler tasks and chunk tickets of a run. */
    public static JavaPlugin ownerPlugin() {
        JavaPlugin guard = UIGuard.getInstance();
        return guard != null ? guard : Main.getInstance();
    }

    /**
     * Message helper: reads {@code path} from the composite config (RU/EN with
     * fallback) and applies {@code %placeholder%} replacements.
     */
    public static String msg(String path, String def, String... pairs) {
        String value = MessagesManager.getString(path, def);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            value = value.replace(pairs[i], pairs[i + 1]);
        }
        return value;
    }

    /** Parses a config message and sends it to the given sender. */
    public static void send(CommandSender to, String path, String def, String... pairs) {
        if (to == null) return;
        to.sendMessage(MessageUtil.parse(msg(path, def, pairs)));
    }

    /** Notifies {@code ui.alerts} holders that a stress test has begun. */
    private void alertStarted(CommandSender sender) {
        if (!cfg().getBoolean("stresstest.alert_on_start", true)) return;
        String name = sender instanceof Player player ? player.getName() : "console";
        AlertBroadcast.send(msg("stresstest.alert_started",
                "<red>⚠</red> <white>Stress test </white><yellow>%type% / %power%"
                        + "</white> <white>started by </white><yellow>%player%</yellow>",
                "%player%", name, "%type%", typeId(), "%power%", powerId()));
    }

    /** Location the load is anchored to: the sender, else any player, else spawn. */
    private static Location resolveAnchor(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player.getLocation();
        }
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
    }

    private static int countEntities() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getEntities().size();
        }
        return total;
    }

    private String typeId() {
        return type != null ? type.id() : "-";
    }

    private String powerId() {
        return power != null ? power.id() : "-";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Removes MiniMessage tags so the console report stays readable. */
    private static String stripMini(String input) {
        return input.replaceAll("<[^>]*>", "");
    }

    /**
     * Sweeps leftover stress-test entities a couple of seconds after startup —
     * they can only exist when a previous run was killed before {@code stop()}
     * could run (crash, {@code kill -9}).
     */
    private void scheduleLeftoverSweep() {
        JavaPlugin plugin = ownerPlugin();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            StressTestManager current = instance;
            if (current == null || current.isActive()) {
                return;
            }
            int removed = EntityLoad.sweepAllWorlds();
            if (removed > 0) {
                ConsoleLogger.info(LOG_PREFIX + "Removed " + removed
                        + " leftover stress-test entity(ies) from a previous run.");
            }
        }, 40L);
    }
}
