package com.ultimateimprovments.mechanics.security.consolelockdown;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.UIGuard;
import com.ultimateimprovments.database.StateStore;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * ConsoleLockdownManager — emergency console lockout.
 * <p>
 * While the lockdown is active, EVERY command issued by the console executor
 * (server console and RCON) is cancelled. This is an anti-tamper measure: if an
 * attacker gains console access, they cannot run anything, including
 * {@code /ui console lockdown off} — the lockdown can only be lifted by a player
 * with the {@code ui.command.console} permission or via the config kill-switch
 * ({@code console_lockdown.kill_switch = true} + restart / /ui reload).
 * <p>
 * State is persisted in SQLite ({@link StateStore}, namespace
 * {@code console_lockdown}) so the lockdown, its pending schedule and the timed
 * auto-off survive a server restart. Scheduled changes (from {@code -t} flags and
 * {@code timed}) are re-armed from the DB on every startup/reload.
 */
public final class ConsoleLockdownManager implements Listener {

    /** StateStore namespace for all lockdown keys. */
    private static final String NS = "console_lockdown";

    private static ConsoleLockdownManager instance;

    /** Current effective state (mirrors DB key {@code active}). */
    private boolean active = false;

    /** Pending scheduled change: "on" or "off" (null — nothing scheduled). */
    private String scheduledAction = null;

    /** Epoch millis when the pending change must be applied. */
    private long scheduledAt = 0L;

    /** Bukkit task for the pending scheduled change. */
    private BukkitTask scheduledTask = null;

    private ConsoleLockdownManager() {}

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
        instance = new ConsoleLockdownManager();
        UIGuard guard = UIGuard.getInstance();
        guard.getServer().getPluginManager().registerEvents(instance, guard);
        instance.applyKillSwitch();
        instance.restoreFromDb();
    }

    public static ConsoleLockdownManager getInstance() {
        return instance;
    }

    /**
     * Config kill-switch ({@code console_lockdown.kill_switch}, default false).
     * When set to true by an admin, the next startup or /ui reload wipes the
     * saved lockdown state from the DB, lifts the lockdown and resets the
     * switch back to false in the config file.
     */
    private void applyKillSwitch() {
        if (!Main.getInstance().getConfig().getBoolean("console_lockdown.kill_switch", false)) {
            return;
        }
        active = false;
        cancelScheduledTask();
        wipeDbState();
        Main.getInstance().getConfig().set("console_lockdown.kill_switch", false);
        Main.getInstance().saveConfig();
        ConsoleLogger.warn("[ConsoleLockdown] KILL-SWITCH triggered: lockdown state wiped, "
                + "console_lockdown.kill_switch reset to false.");
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
        ConsoleLogger.info("[ConsoleLockdown] Restored state from DB: active=" + active
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

    /** Enables the lockdown immediately (cancels any pending change). */
    public void enable() {
        cancelScheduledTask();
        active = true;
        persistActive();
        ConsoleLogger.warn("[ConsoleLockdown] ENABLED — all console commands are now cancelled.");
        notifyAdmins("console_lockdown.enabled",
                "<red>🔒</red> <white>Console lockdown </white><green>ENABLED</green>"
                        + "<gray> — all console commands are cancelled.</gray>");
    }

    /** Disables the lockdown immediately (cancels any pending change). */
    public void disable() {
        cancelScheduledTask();
        active = false;
        persistActive();
        ConsoleLogger.info("[ConsoleLockdown] DISABLED — console commands work again.");
        notifyAdmins("console_lockdown.disabled",
                "<green>✔</green> <white>Console lockdown </white><red>DISABLED</red>"
                        + "<gray> — console commands work again.</gray>");
    }

    /** Schedules the lockdown to be enabled at {@code atMillis} (epoch ms). */
    public void scheduleEnable(long atMillis) {
        schedule("on", atMillis);
        notifyAdmins("console_lockdown.scheduled_on",
                "<yellow>⏰</yellow> <white>Console lockdown will be enabled in </white><yellow>%time%</yellow>",
                atMillis);
    }

    /** Schedules the lockdown to be disabled at {@code atMillis} (epoch ms). */
    public void scheduleDisable(long atMillis) {
        schedule("off", atMillis);
        notifyAdmins("console_lockdown.scheduled_off",
                "<yellow>⏰</yellow> <white>Console lockdown will be disabled in </white><yellow>%time%</yellow>",
                atMillis);
    }

    /**
     * Enables the lockdown now and schedules its automatic disable after
     * {@code durationMillis} (/ui console lockdown timed).
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
        scheduledTask = org.bukkit.Bukkit.getScheduler().runTaskLater(UIGuard.getInstance(),
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
    }

    // =========================
    // CONSOLE COMMAND BLOCKING
    // =========================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (!active) return;
        CommandSender sender = event.getSender();
        if (!(sender instanceof ConsoleCommandSender)
                && !(sender instanceof RemoteConsoleCommandSender)) {
            return;
        }
        event.setCancelled(true);
        ConsoleLogger.warn("[ConsoleLockdown] Blocked console command: " + event.getCommand());
        sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse(
                "<red>🔒 Console lockdown is active — this command was cancelled.</red>"));
    }

    // =========================
    // MESSAGING / TIME HELPERS
    // =========================

    /**
     * Notifies ADMINS about lockdown changes (players with the alert
     * permissions — {@code ui.alerts} etc. — plus a console log line).
     * Message keys live in both config language sections
     * ({@code messages.console_lockdown.*} / {@code messages_en.console_lockdown.*}).
     */
    private void notifyAdmins(String key, String def) {
        AlertBroadcast.send(MessagesManager.getString(key, def));
        ConsoleLogger.info("[ConsoleLockdown] " + plain(def));
    }

    private void notifyAdmins(String key, String def, long atMillis) {
        long remaining = Math.max(0L, atMillis - System.currentTimeMillis());
        String msg = MessagesManager.getString(key, def).replace("%time%", formatDuration(remaining));
        AlertBroadcast.send(msg);
        ConsoleLogger.info("[ConsoleLockdown] " + plain(def).replace("%time%", formatDuration(remaining)));
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
