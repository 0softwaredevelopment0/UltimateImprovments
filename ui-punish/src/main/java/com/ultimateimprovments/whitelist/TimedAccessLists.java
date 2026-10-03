package com.ultimateimprovments.whitelist;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.punish.PunishmentMessages;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * TimedAccessLists — shared timed-scheduling engine for the four access lists:
 * whitelist, blacklist, opwhitelist and opblacklist (/ui &lt;list&gt; ...).
 * <p>
 * Two kinds of deferred operations are supported:
 * <ul>
 *   <li><b>Delayed</b> — apply a change after a delay (the optional
 *       {@code -t <time>} flag on add/remove/on/off).</li>
 *   <li><b>Temp</b> ({@code *-temp} subcommands) — apply instantly, then
 *       automatically revert after a delay. The {@code -t} flag is
 *       <b>required</b> for temp commands.</li>
 * </ul>
 * Time format: {@code <N>s|m|h|d} (seconds, minutes, hours, days); both
 * {@code -t 5m} and {@code -t5m} forms are accepted (same as /ui server lockdown).
 * <p>
 * <b>Opposite actions cancel pending schedules</b>: an action with an ADD
 * effect cancels pending REMOVE schedules for the same player (and vice
 * versa); ENABLE cancels pending DISABLE for the list state (and vice versa).
 * A newer schedule for the same target replaces the older one, so per target
 * at most one pending operation exists at any time.
 * <p>
 * <b>Persistence</b>: every pending operation is mirrored in the SQLite table
 * {@code timed_list_tasks} and survives restarts — {@link #restore(Main)}
 * re-arms all rows on startup (overdue operations execute immediately).
 * <p>
 * Feedback messages come from {@code messages[_en].punishment.timed.*}
 * (multiline MiniMessage lists, placeholders: %player%, %duration%, %list%).
 */
public final class TimedAccessLists {

    private TimedAccessLists() {}

    // =========================
    // ACCESS LIST BRIDGE
    // =========================

    /** The four managed access lists, bridged to their managers. */
    public enum AccessList {
        WHITELIST {
            @Override public boolean contains(String name) { return WhitelistManager.isWhitelisted(name); }
            @Override public boolean add(String name) { return WhitelistManager.add(name); }
            @Override public boolean remove(String name) { return WhitelistManager.remove(name); }
            @Override public boolean setEnabled(boolean value) { return WhitelistManager.setEnabled(value); }
            @Override public boolean isEnabled() { return WhitelistManager.isEnabled(); }
            @Override public java.util.List<String> getNames() { return WhitelistManager.getWhitelistNames(); }
            @Override public String title() { return "Whitelist"; }
            @Override public boolean dangerList() { return false; }
            @Override public boolean showsOpTag() { return false; }
        },
        BLACKLIST {
            @Override public boolean contains(String name) { return BlacklistManager.isBlacklisted(name); }
            @Override public boolean add(String name) { return BlacklistManager.add(name); }
            @Override public boolean remove(String name) { return BlacklistManager.remove(name); }
            @Override public boolean setEnabled(boolean value) { return BlacklistManager.setEnabled(value); }
            @Override public boolean isEnabled() { return BlacklistManager.isEnabled(); }
            @Override public java.util.List<String> getNames() { return BlacklistManager.getBlacklistNames(); }
            @Override public String title() { return "Blacklist"; }
            @Override public boolean dangerList() { return true; }
            @Override public boolean showsOpTag() { return false; }
        },
        OPWHITELIST {
            @Override public boolean contains(String name) { return OpWhitelistManager.isWhitelisted(name); }
            @Override public boolean add(String name) { return OpWhitelistManager.add(name); }
            @Override public boolean remove(String name) { return OpWhitelistManager.remove(name); }
            @Override public boolean setEnabled(boolean value) { return OpWhitelistManager.setEnabled(value); }
            @Override public boolean isEnabled() { return OpWhitelistManager.isEnabled(); }
            @Override public java.util.List<String> getNames() { return OpWhitelistManager.getWhitelistNames(); }
            @Override public String title() { return "OP whitelist"; }
            @Override public boolean dangerList() { return false; }
            @Override public boolean showsOpTag() { return true; }
        },
        OPBLACKLIST {
            @Override public boolean contains(String name) { return OpBlacklistManager.isBlacklisted(name); }
            @Override public boolean add(String name) { return OpBlacklistManager.add(name); }
            @Override public boolean remove(String name) { return OpBlacklistManager.remove(name); }
            @Override public boolean setEnabled(boolean value) { return OpBlacklistManager.setEnabled(value); }
            @Override public boolean isEnabled() { return OpBlacklistManager.isEnabled(); }
            @Override public java.util.List<String> getNames() { return OpBlacklistManager.getBlacklistNames(); }
            @Override public String title() { return "OP blacklist"; }
            @Override public boolean dangerList() { return true; }
            @Override public boolean showsOpTag() { return true; }
        };

        public abstract boolean contains(String name);
        public abstract boolean add(String name);
        public abstract boolean remove(String name);
        public abstract boolean setEnabled(boolean value);
        public abstract boolean isEnabled();
        public abstract java.util.List<String> getNames();

        /** Display title for headers and instant messages ("Whitelist", "OP blacklist", ...). */
        public abstract String title();

        /** True for blacklist-style lists (red online dots in the list view). */
        public abstract boolean dangerList();

        /** True for OP lists (the list view shows the current [OP] tag). */
        public abstract boolean showsOpTag();

        /** Config/log key of the list ("whitelist", "opblacklist", ...). */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Localized display name for the %list% message placeholder. */
        public String displayName() {
            return PunishmentMessages.listDisplayName(key());
        }

        /** Resolves a list by its config key, or null when unknown. */
        public static AccessList byKey(String key) {
            if (key == null) return null;
            for (AccessList list : values()) {
                if (list.key().equals(key)) return list;
            }
            return null;
        }
    }

    // =========================
    // PENDING SCHEDULES
    // =========================

    /** One pending deferred operation: its Bukkit task and the effect it will apply. */
    private record PendingEntry(BukkitTask task, String effect) {}

    /**
     * Pending tasks by key: {@code <list>:p:<name>} (player scope, at most one
     * per player thanks to opposite-cancel + replace) or {@code <list>:state}.
     */
    private static final Map<String, PendingEntry> PENDING = new ConcurrentHashMap<>();

    private static String playerKey(AccessList list, String player) {
        return list.key() + ":p:" + player.toLowerCase(Locale.ROOT).trim();
    }

    private static String stateKey(AccessList list) {
        return list.key() + ":state";
    }

    /** Registers (or replaces) a pending task and mirrors it into the DB. */
    private static void schedule(AccessList list, String scope, String target,
                                 String effect, long delayMs) {
        String key = scope.equals("player") ? playerKey(list, target) : stateKey(list);

        PendingEntry prev = PENDING.remove(key);
        if (prev != null) prev.task().cancel();

        long executeAt = System.currentTimeMillis() + delayMs;
        persistTask(list.key(), scope, target, effect, executeAt);
        armTask(list, scope, target, effect, Math.max(50L, delayMs));
    }

    /** Arms an in-memory task for an already-persisted DB row (used by restore). */
    private static void armTask(AccessList list, String scope, String target,
                                String effect, long delayMs) {
        String key = scope.equals("player") ? playerKey(list, target) : stateKey(list);

        long delayTicks = Math.max(1L, delayMs / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            PENDING.remove(key);
            removeTaskRow(list.key(), scope, target);
            executeEffect(list, scope, target, effect);
        }, delayTicks);
        PENDING.put(key, new PendingEntry(task, effect));
    }

    /** Applies a stored effect and logs the outcome. */
    private static void executeEffect(AccessList list, String scope, String target, String effect) {
        boolean ok;
        String what;
        switch (effect) {
            case "add" -> {
                ok = list.add(target);
                what = "add " + target;
            }
            case "remove" -> {
                ok = list.remove(target);
                what = "remove " + target;
            }
            case "enable" -> {
                ok = list.setEnabled(true);
                what = "ENABLE";
            }
            case "disable" -> {
                ok = list.setEnabled(false);
                what = "DISABLE";
            }
            default -> {
                return;
            }
        }
        ConsoleLogger.info("[" + list.title() + "] Scheduled " + what
                + (ok ? " done." : " skipped (already applied)."));
    }

    /** Cancels a pending entry (task + memory + DB row), if present. */
    private static void cancelEntry(AccessList list, String scope, String target) {
        String key = scope.equals("player") ? playerKey(list, target) : stateKey(list);
        PendingEntry entry = PENDING.remove(key);
        if (entry != null) entry.task().cancel();
        removeTaskRow(list.key(), scope, target);
    }

    /**
     * Cancels the pending player operation when its effect opposes the given
     * intent (an ADD intent cancels a pending REMOVE and vice versa).
     * Called on every player-targeted command so the latest intent wins.
     */
    public static void cancelOpposingPlayer(AccessList list, String player, boolean addIntent) {
        if (player == null || player.isBlank()) return;
        String opposing = addIntent ? "remove" : "add";
        PendingEntry entry = PENDING.get(playerKey(list, player));
        if (entry != null && entry.effect().equals(opposing)) {
            cancelEntry(list, "player", player);
        }
    }

    /**
     * Cancels the pending state operation when its effect opposes the given
     * intent (an ENABLE intent cancels a pending DISABLE and vice versa).
     */
    public static void cancelOpposingState(AccessList list, boolean enableIntent) {
        String opposing = enableIntent ? "disable" : "enable";
        PendingEntry entry = PENDING.get(stateKey(list));
        if (entry != null && entry.effect().equals(opposing)) {
            cancelEntry(list, "state", "*");
        }
    }

    /** True when a pending state operation exists for the list. */
    public static boolean hasStatePending(AccessList list) {
        return PENDING.containsKey(stateKey(list));
    }

    // =========================
    // PUBLIC SCHEDULERS (used by AccessListCommands)
    // =========================

    /** Schedules a player add/remove after the delay. */
    public static void schedulePlayer(AccessList list, String player, boolean addEffect, long delayMs) {
        schedule(list, "player", player.toLowerCase(Locale.ROOT).trim(),
                addEffect ? "add" : "remove", delayMs);
    }

    /** Schedules a list enable/disable after the delay. */
    public static void scheduleState(AccessList list, boolean enableEffect, long delayMs) {
        schedule(list, "state", "*", enableEffect ? "enable" : "disable", delayMs);
    }

    // =========================
    // PERSISTENCE (SQLite: timed_list_tasks)
    // =========================

    private static void persistTask(String listKey, String scope, String target,
                                    String effect, long executeAt) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO timed_list_tasks (list_name, scope, target, effect, execute_at) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            st.setString(1, listKey);
            st.setString(2, scope);
            st.setString(3, target);
            st.setString(4, effect);
            st.setLong(5, executeAt);
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING,
                    "[TimedLists] Failed to persist scheduled task", e);
        }
    }

    private static void removeTaskRow(String listKey, String scope, String target) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM timed_list_tasks WHERE list_name = ? AND scope = ? AND target = ?")) {
            st.setString(1, listKey);
            st.setString(2, scope);
            st.setString(3, target);
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING,
                    "[TimedLists] Failed to remove scheduled task row", e);
        }
    }

    /**
     * Re-arms all persisted schedules after (re)start. Must be called after the
     * managers are initialized. Rebuilds the in-memory map from the DB (the DB
     * is the source of truth), so it is safe to call on every soft reload.
     * Overdue operations execute on the next tick.
     */
    public static void restore(Main plugin) {
        for (PendingEntry entry : PENDING.values()) {
            entry.task().cancel();
        }
        PENDING.clear();

        int restored = 0;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT list_name, scope, target, effect, execute_at FROM timed_list_tasks");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                AccessList list = AccessList.byKey(rs.getString("list_name"));
                String scope = rs.getString("scope");
                String target = rs.getString("target");
                String effect = rs.getString("effect");
                long executeAt = rs.getLong("execute_at");
                if (list == null || scope == null || target == null || effect == null) continue;

                long delay = executeAt - System.currentTimeMillis();
                if (delay < 50L) {
                    ConsoleLogger.info("[" + list.title() + "] Scheduled " + effect
                            + (scope.equals("player") ? " " + target : "")
                            + " is overdue — executing now.");
                    delay = 50L;
                }
                armTask(list, scope, target, effect, delay);
                restored++;
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING,
                    "[TimedLists] Failed to restore scheduled tasks", e);
        }

        if (restored > 0) {
            ConsoleLogger.info("[TimedLists] Restored " + restored + " scheduled operation(s) from the DB.");
        }
    }

    // =========================
    // TIME PARSING
    // =========================

    /**
     * Parses a delay like "30s", "5m", "2h", "7d" into millis.
     *
     * @return the delay in millis, or -1 when the format is invalid
     */
    public static long parseDelayMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        String lower = timeStr.toLowerCase(Locale.ROOT).trim();

        char unit = lower.charAt(lower.length() - 1);
        String numStr = lower.substring(0, lower.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (amount <= 0) return -1;

        return switch (unit) {
            case 's' -> amount * 1000L;
            case 'm' -> amount * 60_000L;
            case 'h' -> amount * 3_600_000L;
            case 'd' -> amount * 86_400_000L;
            default -> -1;
        };
    }

    /**
     * Extracts the optional {@code -t <time>} flag from args.
     *
     * @param fromIndex first arg index to scan (3 for player commands, 2 for on/off)
     * @return the delay in millis, 0 when the flag is absent, or null on a
     *         malformed flag (an error message has been sent to the sender)
     */
    public static Long parseTimeFlag(CommandSender sender, String[] args, int fromIndex) {
        for (int i = fromIndex; i < args.length; i++) {
            String lower = args[i].toLowerCase(Locale.ROOT);
            if (lower.equals("-t")) {
                if (i + 1 >= args.length) {
                    sendInvalidTime(sender);
                    return null;
                }
                long millis = parseDelayMillis(args[i + 1]);
                if (millis <= 0) {
                    sendInvalidTime(sender);
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-t") && lower.length() > 2) {
                long millis = parseDelayMillis(lower.substring(2));
                if (millis <= 0) {
                    sendInvalidTime(sender);
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-")) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Unknown flag: </white><yellow>" + args[i] + "</yellow>"));
                return null;
            }
        }
        return 0L;
    }

    private static void sendInvalidTime(CommandSender sender) {
        sender.sendMessage(timedMessage(null, "invalid_time", null, null));
    }

    // =========================
    // MESSAGES
    // =========================

    /** Builds a localized timed-operation message from messages[_en].punishment.timed.<key>. */
    public static Component timedMessage(AccessList list, String key, String player, Long delayMs) {
        String listName = list != null ? list.displayName() : "";
        String duration = delayMs != null ? PunishmentManager.formatRemaining(delayMs) : "";
        return PunishmentMessages.buildTimedListMessage(key, listName, player, duration);
    }
}
