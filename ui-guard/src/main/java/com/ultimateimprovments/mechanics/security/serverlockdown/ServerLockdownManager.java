package com.ultimateimprovments.mechanics.security.serverlockdown;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.UIGuard;
import com.ultimateimprovments.database.StateStore;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * ServerLockdownManager — emergency server lockout against join floods
 * (bot attacks and similar).
 * <p>
 * While the lockdown is active, NEW connections are refused
 * ({@link AsyncPlayerPreLoginEvent}), but players who are already online are
 * NEVER kicked. A player who was on the server when the lockdown was enabled
 * (or who rejoined during it) may leave and come back within a grace window
 * ({@code server_lockdown.grace_time}, default 10s) after quitting — after the
 * window expires they are treated like everyone else and blocked until the
 * lockdown is lifted.
 * <p>
 * Managed by {@code /ui server lockdown} (same flags as the console lockdown:
 * status / on / off / timed, optional {@code -t <10s|5m|2h|1d>} delay).
 * <p>
 * State is persisted in SQLite ({@link StateStore}, namespace
 * {@code server_lockdown}, grace marks in {@code server_lockdown_grace}) so
 * the lockdown, its pending schedule, the timed auto-off and the grace marks
 * survive a server restart. Scheduled changes (from {@code -t} flags and
 * {@code timed}) are re-armed from the DB on every startup/reload.
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

    private static ServerLockdownManager instance;

    /** Current effective state (mirrors DB key {@code active}). */
    private boolean active = false;

    /** Pending scheduled change: "on" or "off" (null — nothing scheduled). */
    private String scheduledAction = null;

    /** Epoch millis when the pending change must be applied. */
    private long scheduledAt = 0L;

    /** Bukkit task for the pending scheduled change. */
    private BukkitTask scheduledTask = null;

    /** Cached grace window in millis (from {@code server_lockdown.grace_time}). */
    private long graceMillis;

    private ServerLockdownManager() {}

    // =========================
    // LIFECYCLE
    // =========================

    /**
     * Initializes (or re-initializes after /ui reload) the manager:
     * applies the kill-switch, then restores the persisted state and
     * re-arms any pending scheduled change.
     */
    public static void init() {
        if (instance != null) {
            instance.cancelScheduledTask();
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

    /** Re-reads the grace window from the config (called on init and reload). */
    public void reloadGraceConfig() {
        String raw = Main.getInstance().getConfig().getString("server_lockdown.grace_time", "10s");
        long parsed = parseTimeToMillis(raw);
        graceMillis = parsed > 0 ? parsed : 10_000L;
    }

    /**
     * Config kill-switch ({@code server_lockdown.kill_switch}, default false).
     * When set to true by an admin, the next startup or /ui reload wipes the
     * saved lockdown state (including grace marks) from the DB, lifts the
     * lockdown and resets the switch back to false in the config file.
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
        ConsoleLogger.info("[ServerLockdown] Restored state from DB: active=" + active
                + ", scheduled " + action + " in "
                + formatDuration(at - System.currentTimeMillis()) + ".");
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
        markCurrentPlayers();
        ConsoleLogger.warn("[ServerLockdown] ENABLED — new connections are now blocked ("
                + Bukkit.getOnlinePlayers().size() + " players online are grandfathered).");
        notifyAdmins("server_lockdown.enabled",
                "<red>🔒</red> <white>Server lockdown </white><green>ENABLED</green>"
                        + "<gray> — new connections are blocked.</gray>");
    }

    /** Disables the lockdown immediately (cancels any pending change). */
    public void disable() {
        cancelScheduledTask();
        active = false;
        persistActive();
        StateStore.clearNamespace(GRACE_NS);
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
    }

    // =========================
    // GRACE MARKS (grandfathered players)
    // =========================

    /**
     * Marks every currently online player as grandfathered: they may leave and
     * rejoin during the lockdown (within the grace window after each quit).
     * The mark value is the epoch millis of the player's last quit, or
     * {@code 0} while they have not quit since the lockdown was enabled
     * (meaning the grace window has not started yet).
     */
    private void markCurrentPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            StateStore.put(GRACE_NS, graceKey(player.getUniqueId()), "0");
        }
    }

    private static String graceKey(java.util.UUID uuid) {
        return "grace_" + uuid;
    }

    /**
     * Checks (and lazy-expires) the grace mark of a connecting player.
     *
     * @return true when the player may join despite the active lockdown
     */
    private boolean hasGrace(java.util.UUID uuid) {
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
            StateStore.remove(GRACE_NS, graceKey(uuid));
            return false;
        }
        if (System.currentTimeMillis() - quitAt <= graceMillis) {
            return true;
        }
        // Grace window expired — drop the mark, the player is "new" now.
        StateStore.remove(GRACE_NS, graceKey(uuid));
        return false;
    }

    // =========================
    // JOIN BLOCKING + QUIT TRACKING
    // =========================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!active) return;
        if (hasGrace(event.getUniqueId())) {
            ConsoleLogger.info("[ServerLockdown] Grace join allowed: " + event.getName());
            return;
        }
        String kickMessage = MessagesManager.getString("server_lockdown.kick_message",
                "<red>🔒 Server is under lockdown — new connections are temporarily disabled.</red>"
                        + "\\n<gray>Please try again later.</gray>");
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MessageUtil.parse(kickMessage));
        ConsoleLogger.warn("[ServerLockdown] Blocked join: " + event.getName()
                + " (" + event.getAddress().getHostAddress() + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!active) return;
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        // Only refresh the quit time when the player is grandfathered — the
        // grace window starts running from this moment.
        if (StateStore.get(GRACE_NS, graceKey(uuid)) != null) {
            StateStore.put(GRACE_NS, graceKey(uuid), String.valueOf(System.currentTimeMillis()));
        }
    }

    // =========================
    // MESSAGING / TIME HELPERS
    // =========================

    /**
     * Notifies ADMINS about lockdown changes (players with the alert
     * permissions — {@code ui.alerts} etc. — plus a console log line).
     * Message keys live in both config language sections
     * ({@code messages.server_lockdown.*} / {@code messages_en.server_lockdown.*}).
     */
    private void notifyAdmins(String key, String def) {
        AlertBroadcast.send(MessagesManager.getString(key, def));
        ConsoleLogger.info("[ServerLockdown] " + plain(def));
    }

    private void notifyAdmins(String key, String def, long atMillis) {
        long remaining = Math.max(0L, atMillis - System.currentTimeMillis());
        String msg = MessagesManager.getString(key, def).replace("%time%", formatDuration(remaining));
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
