package com.ultimateimprovments.mechanics.security.serverlockdown;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.UIGuard;
import com.ultimateimprovments.database.StateStore;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ServerLockdownManager — emergency server lockout against join floods
 * (bot attacks and similar).
 * <p>
 * While the lockdown is active, NEW connections are refused
 * ({@link PlayerLoginEvent}, same hook the maintenance/ban systems use —
 * permissions are available there), but players who are already online are
 * NEVER kicked. A player who was on the server when the lockdown was enabled
 * (or who rejoined during it) may leave and come back within a grace window
 * ({@code server_lockdown.grace_time}, default 10s) after quitting — after the
 * window expires they are treated like everyone else and blocked until the
 * lockdown is lifted. Players with the {@code ui.serverlockdown.bypass}
 * permission (FALSE by default) always get through.
 * <p>
 * Managed by {@code /ui server lockdown} (same flags as the console lockdown:
 * status / on / off / timed, optional {@code -t <10s|5m|2h|1d>} delay). The
 * status command shows per-session statistics: how many connections were
 * blocked and who is currently inside the grace window.
 * <p>
 * State is persisted in SQLite ({@link StateStore}, namespaces
 * {@code server_lockdown}, {@code server_lockdown_grace} and
 * {@code server_lockdown_blocked}) so the lockdown, its pending schedule, the
 * timed auto-off, the grace marks and the blocked-joins counter survive a
 * server restart. Scheduled changes (from {@code -t} flags and {@code timed})
 * are re-armed from the DB on every startup/reload.
 * <p>
 * Kill-switch: {@code server_lockdown.kill_switch = true} + restart //ui
 * reload wipes the saved state from the DB, lifts the lockdown and resets the
 * switch back to false in the config.
 */
public final class ServerLockdownManager implements Listener {

    /** StateStore namespace for the lockdown state keys. */
    private static final String NS = "server_lockdown";
    /** StateStore namespace for the per-player grace marks (grandfathered players). */
    private static final String GRACE_NS = "server_lockdown_grace";
    /** StateStore namespace for the per-session blocked-join statistics. */
    private static final String BLOCKED_NS = "server_lockdown_blocked";

    /** Ring buffer size for stored blocked-join records (oldest are overwritten). */
    private static final int BLOCKED_MAX_ENTRIES = 200;

    // Built-in default alert messages (English) — used when the config has no
    // thresholds at all. Server owners may redefine/add/remove thresholds in
    // messages[_en].server_lockdown.alerts ("<threshold>" = MiniMessage text).
    private static final String DEFAULT_ALERT_10 =
            "<red>🔒</red> <white>Server lockdown:</white> <yellow>%count%</yellow> <gray>joins blocked this session.</gray>";
    private static final String DEFAULT_ALERT_100 =
            "<red>🔒</red> <white>Server lockdown:</white> <yellow>%count%</yellow> <red>joins blocked — this looks like a bot attack!</red>";
    private static final String DEFAULT_ALERT_1000 =
            "<dark_red>🔒 CRITICAL:</dark_red> <white>Server lockdown has blocked </white><yellow>%count%</yellow> <white>joins this session!</white>";

    private static ServerLockdownManager instance;

    /** Current effective state (mirrors DB key {@code active}). */
    private boolean active = false;

    /** Pending scheduled change: "on" or "off" (null — nothing scheduled). */
    private String scheduledAction = null;

    /** Epoch millis when the pending change must be applied. */
    private long scheduledAt = 0L;

    /** Bukkit task for the pending scheduled change. */
    private BukkitTask scheduledTask = null;

    /** Periodic task that drops expired grace marks (runs while the lockdown is active). */
    private BukkitTask cleanupTask = null;

    /** Cached grace window in millis (from {@code server_lockdown.grace_time}). */
    private long graceMillis;

    /** Cached alert thresholds (sorted ascending) from {@code messages.*.server_lockdown.alerts}. */
    private List<AlertThreshold> alertThresholds = List.of();

    /** One alert rule: when the session blocked count reaches {@code threshold}, send {@code message} once. */
    public record AlertThreshold(int threshold, String message) {}

    // =========================
    // LIFECYCLE
    // =========================

    private ServerLockdownManager() {}

    /**
     * Initializes (or re-initializes after /ui reload) the manager:
     * applies the kill-switch, then restores the persisted state and
     * re-arms any pending scheduled change.
     */
    public static void init() {
        if (instance != null) {
            instance.cancelScheduledTask();
            instance.stopCleanupTask();
        }
        instance = new ServerLockdownManager();
        UIGuard guard = UIGuard.getInstance();
        guard.getServer().getPluginManager().registerEvents(instance, guard);
        instance.reloadGraceConfig();
        instance.applyKillSwitch();
        instance.restoreFromDb();
    }

    public static ServerLockdownManager getInstance() {
        return instance;
    }

    /** Re-reads the grace window and the alert thresholds (called on init and reload). */
    public void reloadGraceConfig() {
        String raw = Main.getInstance().getConfig().getString("server_lockdown.grace_time", "10s");
        long parsed = parseTimeToMillis(raw);
        graceMillis = parsed > 0 ? parsed : 10_000L;
        reloadAlertThresholds();
    }

    /**
     * Reads the alert thresholds from {@code messages[_en].server_lockdown.alerts}
     * (keys = session blocked-count thresholds, values = MiniMessage texts).
     * Falls back to the built-in defaults (10/100/1000) when the section is
     * missing or empty.
     */
    private void reloadAlertThresholds() {
        String lang = Main.getInstance().getConfig().getString("messages.lang", "en");
        String root = "en".equalsIgnoreCase(lang) ? "messages_en" : "messages";
        ConfigurationSection sec = Main.getInstance().getConfig()
                .getConfigurationSection(root + ".server_lockdown.alerts");
        List<AlertThreshold> out = new ArrayList<>();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int threshold;
                try {
                    threshold = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    ConsoleLogger.warn("[ServerLockdown] Ignoring alert threshold '" + key
                            + "' — not a number.");
                    continue;
                }
                String message = sec.getString(key);
                if (threshold > 0 && message != null && !message.isBlank()) {
                    out.add(new AlertThreshold(threshold, message));
                }
            }
        }
        if (out.isEmpty()) {
            out = List.of(new AlertThreshold(10, DEFAULT_ALERT_10),
                    new AlertThreshold(100, DEFAULT_ALERT_100),
                    new AlertThreshold(1000, DEFAULT_ALERT_1000));
        }
        out.sort(Comparator.comparingInt(AlertThreshold::threshold));
        alertThresholds = out;
    }

    /**
     * Config kill-switch ({@code server_lockdown.kill_switch}, default false).
     * When set to true by an admin, the next startup or /ui reload wipes the
     * saved lockdown state (including grace marks and blocked stats) from the
     * DB, lifts the lockdown and resets the switch back to false in the config.
     */
    private void applyKillSwitch() {
        if (!Main.getInstance().getConfig().getBoolean("server_lockdown.kill_switch", false)) {
            return;
        }
        active = false;
        cancelScheduledTask();
        wipeDbState();
        Main.getInstance().getConfig().set("server_lockdown.kill_switch", false);
        Main.getInstance().saveConfig();
        ConsoleLogger.warn("[ServerLockdown] KILL-SWITCH triggered: lockdown state wiped, "
                + "server_lockdown.kill_switch reset to false.");
    }

    /** Restores the active flag and the pending schedule from the DB. */
    private void restoreFromDb() {
        active = "true".equalsIgnoreCase(StateStore.get(NS, "active"));

        String action = StateStore.get(NS, "scheduled_action");
        String atStr = StateStore.get(NS, "scheduled_at");
        if (action == null || atStr == null) {
            // No pending schedule — just restart the cleanup if a lockdown
            // session survived the restart.
            if (active) startCleanupTask();
            return;
        }
        long at;
        try {
            at = Long.parseLong(atStr);
        } catch (NumberFormatException e) {
            StateStore.remove(NS, "scheduled_action");
            StateStore.remove(NS, "scheduled_at");
            return;
        }

        if (!action.equals("on") && !action.equals("off")) {
            StateStore.remove(NS, "scheduled_action");
            StateStore.remove(NS, "scheduled_at");
            return;
        }

        if (at <= System.currentTimeMillis()) {
            // The deadline passed while the server was down — apply now.
            scheduledAction = action;
            scheduledAt = at;
            applyScheduledChange();
            return;
        }

        scheduledAction = action;
        scheduledAt = at;
        armScheduledTask(at - System.currentTimeMillis());
        if (active) startCleanupTask();
        ConsoleLogger.info("[ServerLockdown] Restored state from DB: active=" + active
                + ", scheduled " + action + " in "
                + formatDuration(at - System.currentTimeMillis()) + ".");
    }

    // =========================
    // GRACE CLEANUP TASK
    // =========================

    /**
     * Starts the periodic grace-mark cleanup (every second). Runs only while
     * the lockdown is active — on disable the whole grace namespace is wiped
     * anyway.
     */
    private void startCleanupTask() {
        stopCleanupTask();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(UIGuard.getInstance(),
                this::cleanupExpiredGrace, 20L, 20L);
    }

    private void stopCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    /** Drops every grace mark whose window has already expired. */
    private void cleanupExpiredGrace() {
        if (!active) return;
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, String> e : StateStore.getAll(GRACE_NS).entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("grace_") || "0".equals(e.getValue())) {
                continue; // still "online" (has not quit since the lockdown started)
            }
            long quitAt;
            try {
                quitAt = Long.parseLong(e.getValue());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (now - quitAt > graceMillis) {
                try {
                    dropGrace(UUID.fromString(key.substring("grace_".length())));
                    removed++;
                } catch (IllegalArgumentException ignored) {
                    // malformed key — leave it, it cannot match any join
                }
            }
        }
        if (removed > 0) {
            ConsoleLogger.info("[ServerLockdown] Grace cleanup: dropped " + removed
                    + " expired mark(s).");
        }
    }

    // =========================
    // STATE
    // =========================

    public boolean isActive() {
        return active;
    }

    /** Pending action ("on"/"off") or null. */
    public String getScheduledAction() {
        return scheduledAction;
    }

    /** Epoch millis of the pending change, or 0 when nothing is scheduled. */
    public long getScheduledAt() {
        return scheduledAction != null ? scheduledAt : 0L;
    }

    /** Grace window in millis (from {@code server_lockdown.grace_time}). */
    public long getGraceMillis() {
        return graceMillis;
    }

    /** Enables the lockdown immediately (cancels any pending change). */
    public void enable() {
        cancelScheduledTask();
        active = true;
        persistActive();
        // New lockdown session: statistics start from zero.
        StateStore.clearNamespace(BLOCKED_NS);
        markCurrentPlayers();
        startCleanupTask();
        ConsoleLogger.warn("[ServerLockdown] ENABLED — new connections are now blocked ("
                + Bukkit.getOnlinePlayers().size() + " players online are grandfathered).");
        notifyAdmins("server_lockdown.enabled",
                "<red>🔒</red> <white>Server lockdown </white><green>ENABLED</green>"
                        + "<gray> — new connections are blocked.</gray>");
    }

    /** Disables the lockdown immediately (cancels any pending change). */
    public void disable() {
        cancelScheduledTask();
        stopCleanupTask();
        active = false;
        persistActive();
        StateStore.clearNamespace(GRACE_NS);
        StateStore.clearNamespace(BLOCKED_NS);
        ConsoleLogger.info("[ServerLockdown] DISABLED — new connections are allowed again.");
        notifyAdmins("server_lockdown.disabled",
                "<green>✔</green> <white>Server lockdown </white><red>DISABLED</red>"
                        + "<gray> — new connections are allowed again.</gray>");
    }

    /** Schedules the lockdown to be enabled at {@code atMillis} (epoch ms). */
    public void scheduleEnable(long atMillis) {
        schedule("on", atMillis);
        notifyAdmins("server_lockdown.scheduled_on",
                "<yellow>⏰</yellow> <white>Server lockdown will be enabled in </white><yellow>%time%</yellow>",
                atMillis);
    }

    /** Schedules the lockdown to be disabled at {@code atMillis} (epoch ms). */
    public void scheduleDisable(long atMillis) {
        schedule("off", atMillis);
        notifyAdmins("server_lockdown.scheduled_off",
                "<yellow>⏰</yellow> <white>Server lockdown will be disabled in </white><yellow>%time%</yellow>",
                atMillis);
    }

    /**
     * Enables the lockdown now and schedules its automatic disable after
     * {@code durationMillis} (/ui server lockdown timed).
     */
    public void enableTimed(long durationMillis) {
        enable();
        scheduleDisable(System.currentTimeMillis() + durationMillis);
    }

    /** Cancels the pending scheduled change (keeps the current active flag). */
    public void cancelScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
        scheduledAction = null;
        scheduledAt = 0L;
        StateStore.remove(NS, "scheduled_action");
        StateStore.remove(NS, "scheduled_at");
    }

    // =========================
    // SCHEDULING INTERNALS
    // =========================

    private void schedule(String action, long atMillis) {
        cancelScheduledTask();
        scheduledAction = action;
        scheduledAt = atMillis;
        StateStore.put(NS, "scheduled_action", action);
        StateStore.put(NS, "scheduled_at", String.valueOf(atMillis));
        armScheduledTask(atMillis - System.currentTimeMillis());
    }

    private void armScheduledTask(long delayMillis) {
        long delayTicks = Math.max(1L, delayMillis / 50L);
        scheduledTask = Bukkit.getScheduler().runTaskLater(UIGuard.getInstance(),
                this::applyScheduledChange, delayTicks);
    }

    /** Applies the pending scheduled change (called by the task or on restore). */
    private void applyScheduledChange() {
        scheduledTask = null;
        if ("on".equals(scheduledAction)) {
            scheduledAction = null;
            scheduledAt = 0L;
            enable();
        } else if ("off".equals(scheduledAction)) {
            scheduledAction = null;
            scheduledAt = 0L;
            disable();
        }
    }

    // =========================
    // PERSISTENCE
    // =========================

    private void persistActive() {
        StateStore.put(NS, "active", String.valueOf(active));
    }

    private void wipeDbState() {
        StateStore.clearNamespace(NS);
        StateStore.clearNamespace(GRACE_NS);
        StateStore.clearNamespace(BLOCKED_NS);
    }

    // =========================
    // GRACE MARKS (grandfathered players)
    // =========================

    /**
     * Marks every currently online player as grandfathered: they may leave and
     * rejoin during the lockdown (within the grace window after each quit).
     * The mark value is the epoch millis of the player's last quit, or
     * {@code 0} while they have not quit since the lockdown was enabled
     * (meaning the grace window has not started yet). The player's name is
     * stored alongside for the status screen.
     */
    private void markCurrentPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            StateStore.put(GRACE_NS, graceKey(player.getUniqueId()), "0");
            StateStore.put(GRACE_NS, graceNameKey(player.getUniqueId()), player.getName());
        }
    }

    private static String graceKey(UUID uuid) {
        return "grace_" + uuid;
    }

    private static String graceNameKey(UUID uuid) {
        return "name_" + uuid;
    }

    /**
     * Checks (and lazy-expires) the grace mark of a connecting player.
     *
     * @return true when the player may join despite the active lockdown
     */
    private boolean hasGrace(UUID uuid) {
        String raw = StateStore.get(GRACE_NS, graceKey(uuid));
        if (raw == null) {
            return false;
        }
        if ("0".equals(raw)) {
            // Was online at lockdown time (or at the last restart) and has
            // not quit since — the grace window has not started yet.
            return true;
        }
        long quitAt;
        try {
            quitAt = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            dropGrace(uuid);
            return false;
        }
        if (System.currentTimeMillis() - quitAt <= graceMillis) {
            return true;
        }
        // Grace window expired — drop the mark, the player is "new" now.
        dropGrace(uuid);
        return false;
    }

    private void dropGrace(UUID uuid) {
        StateStore.remove(GRACE_NS, graceKey(uuid));
        StateStore.remove(GRACE_NS, graceNameKey(uuid));
    }

    /** One entry of the status "grace" section: quit and still inside the window. */
    public record GraceEntry(String name, UUID uuid, long quitAt, long remainingMillis) {}

    /**
     * Players who quit during the lockdown and whose grace window is still
     * running (newest quit first). Players who never quit since the lockdown
     * was enabled are NOT listed — they are simply online.
     */
    public List<GraceEntry> getActiveGraceEntries() {
        long now = System.currentTimeMillis();
        List<GraceEntry> out = new ArrayList<>();
        Map<String, String> all = StateStore.getAll(GRACE_NS);
        for (Map.Entry<String, String> e : all.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("grace_") || "0".equals(e.getValue())) {
                continue;
            }
            long quitAt;
            try {
                quitAt = Long.parseLong(e.getValue());
            } catch (NumberFormatException ex) {
                continue;
            }
            long elapsed = now - quitAt;
            if (elapsed > graceMillis) {
                continue; // expired, will be lazily dropped on join
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(key.substring("grace_".length()));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String name = all.get(graceNameKey(uuid));
            out.add(new GraceEntry(name != null ? name : uuid.toString(), uuid, quitAt,
                    graceMillis - elapsed));
        }
        out.sort((a, b) -> Long.compare(b.quitAt, a.quitAt));
        return out;
    }

    // =========================
    // BLOCKED-JOIN STATISTICS (per lockdown session)
    // =========================

    /** One blocked-join record shown in the status "blocked" section. */
    public record BlockedAttempt(String name, String ip, long at) {}

    /** @return how many joins were blocked during the current lockdown session. */
    public long getBlockedCount() {
        String raw = StateStore.get(BLOCKED_NS, "total");
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void recordBlockedJoin(String name, String ip) {
        long total = getBlockedCount();
        int slot = (int) (total % BLOCKED_MAX_ENTRIES); // ring buffer: oldest overwritten
        StateStore.put(BLOCKED_NS, "s" + slot, name + "|" + ip + "|" + System.currentTimeMillis());
        StateStore.put(BLOCKED_NS, "total", String.valueOf(total + 1));
        checkAlertThresholds(total + 1);
    }

    /**
     * Fires the admin alert ({@code ui.alerts} / OP via AlertBroadcast) for
     * every threshold the session blocked counter has just reached exactly
     * (once per threshold per session — the counter only grows).
     */
    private void checkAlertThresholds(long sessionCount) {
        for (AlertThreshold t : alertThresholds) {
            if (sessionCount != t.threshold()) continue;
            String msg = t.message().replace("%count%", String.valueOf(t.threshold()));
            AlertBroadcast.send(msg);
            ConsoleLogger.warn("[ServerLockdown] Threshold alert ("
                    + t.threshold() + " blocked): " + plain(msg));
        }
    }

    /**
     * Blocked-join records of the current session, newest first. Only the last
     * {@value #BLOCKED_MAX_ENTRIES} records are kept (ring buffer) — the total
     * counter keeps growing.
     */
    public List<BlockedAttempt> getBlockedAttempts() {
        long total = getBlockedCount();
        int stored = (int) Math.min(total, BLOCKED_MAX_ENTRIES);
        List<BlockedAttempt> out = new ArrayList<>(stored);
        for (int i = 1; i <= stored; i++) {
            long index = total - i; // newest first
            String raw = StateStore.get(BLOCKED_NS, "s" + (index % BLOCKED_MAX_ENTRIES));
            if (raw == null) continue;
            int p1 = raw.indexOf('|');
            int p2 = p1 >= 0 ? raw.indexOf('|', p1 + 1) : -1;
            if (p1 < 0 || p2 < 0) continue;
            try {
                out.add(new BlockedAttempt(raw.substring(0, p1), raw.substring(p1 + 1, p2),
                        Long.parseLong(raw.substring(p2 + 1))));
            } catch (NumberFormatException ignored) {
                // corrupted record — skip
            }
        }
        return out;
    }

    // =========================
    // JOIN BLOCKING + QUIT TRACKING
    // =========================

    // PlayerLoginEvent is deprecated in Paper 26.3 (async pre-login is the modern
    // hook) — but permissions are only available on this event, which the bypass
    // check needs. Same hook the maintenance/ban systems use.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!active) return;

        Player player = event.getPlayer();
        if (player.hasPermission(com.ultimateimprovments.core.Permissions.UI_SERVERLOCKDOWN_BYPASS)) {
            return;
        }
        if (hasGrace(player.getUniqueId())) {
            ConsoleLogger.info("[ServerLockdown] Grace join allowed: " + player.getName());
            return;
        }

        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                com.ultimateimprovments.util.MessageUtil.parse(kickMessageRaw()
                        .replace("%player%", player.getName())));
        recordBlockedJoin(player.getName(), event.getAddress() != null
                ? event.getAddress().getHostAddress() : "unknown");
        ConsoleLogger.warn("[ServerLockdown] Blocked join: " + player.getName()
                + " (" + (event.getAddress() != null
                ? event.getAddress().getHostAddress() : "unknown") + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!active) return;
        UUID uuid = event.getPlayer().getUniqueId();
        // Only refresh the quit time when the player is grandfathered — the
        // grace window starts running from this moment.
        if (StateStore.get(GRACE_NS, graceKey(uuid)) != null) {
            StateStore.put(GRACE_NS, graceKey(uuid), String.valueOf(System.currentTimeMillis()));
            StateStore.put(GRACE_NS, graceNameKey(uuid), event.getPlayer().getName());
        }
    }

    /**
     * Builds the configurable kick message (punish-style: a list of config
     * lines joined with "\n", both language sections supported). Falls back to
     * a built-in default when the list is empty.
     */
    private static String kickMessageRaw() {
        String lang = Main.getInstance().getConfig().getString("messages.lang", "en");
        String root = "en".equalsIgnoreCase(lang) ? "messages_en" : "messages";
        List<String> lines = Main.getInstance().getConfig()
                .getStringList(root + ".server_lockdown.kick_message");
        if (lines.isEmpty()) {
            lines = List.of(
                    "<red>🔒 Server is under lockdown — new connections are temporarily disabled.</red>",
                    "<gray>Please try again later.</gray>");
        }
        return String.join("\n", lines);
    }

    // =========================
    // MESSAGING / TIME HELPERS
    // =========================

    /**
     * Notifies ADMINS about lockdown changes (players with the alert
     * permission {@code ui.alerts} / legacy alerts / OP — see
     * {@link AlertBroadcast} — plus a console log line). Message keys live in
     * both config language sections
     * ({@code messages.server_lockdown.*} / {@code messages_en.server_lockdown.*}).
     */
    private void notifyAdmins(String key, String def) {
        AlertBroadcast.send(com.ultimateimprovments.config.MessagesManager.getString(key, def));
        ConsoleLogger.info("[ServerLockdown] " + plain(def));
    }

    private void notifyAdmins(String key, String def, long atMillis) {
        long remaining = Math.max(0L, atMillis - System.currentTimeMillis());
        String msg = com.ultimateimprovments.config.MessagesManager.getString(key, def)
                .replace("%time%", formatDuration(remaining));
        AlertBroadcast.send(msg);
        ConsoleLogger.info("[ServerLockdown] " + plain(def).replace("%time%", formatDuration(remaining)));
    }

    /** Strips MiniMessage tags for the plain console log line. */
    private static String plain(String miniMessage) {
        return miniMessage.replaceAll("<[^>]+>", "").replace("🔒", "").trim();
    }

    /** Formats a duration in millis as a compact human string (e.g. "1d 2h", "5m 30s"). */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /** Formats an epoch millis timestamp as "yyyy-MM-dd HH:mm:ss". */
    public static String formatTimestamp(long millis) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(millis));
    }

    /**
     * Parses a time value in the {@code <number><s|m|h|d>} format (e.g. "10s", "5m").
     *
     * @return duration in millis, or -1 when the format is invalid
     */
    public static long parseTimeToMillis(String timeStr) {
        if (timeStr == null) return -1L;
        String lower = timeStr.toLowerCase().trim();
        if (lower.length() < 2) return -1L;
        String digits = lower.substring(0, lower.length() - 1);
        char unit = lower.charAt(lower.length() - 1);
        long value;
        try {
            value = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1L;
        }
        if (value < 0) return -1L;
        return switch (unit) {
            case 's' -> value * 1000L;
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            case 'd' -> value * 86_400_000L;
            default -> -1L;
        };
    }
}
