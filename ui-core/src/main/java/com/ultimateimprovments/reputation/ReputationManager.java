package com.ultimateimprovments.reputation;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Two-scale reputation system (staff-issued):
 * <ol>
 *   <li><b>Numeric reputation</b> — an integer value adjusted by staff
 *       ({@code /ui rep give/set}), report verdicts (confirmed → −N) and
 *       punishments (warn/mute/ban → −N each; every value is a config toggle,
 *       0 disables the source).</li>
 *   <li><b>Status</b> — a Discord Account Standing status issued separately
 *       by a moderator, independent of the numeric scale: All good, Limited,
 *       Very limited, At risk, Suspended (+ {@code NONE} when nothing is set).</li>
 * </ol>
 * All DB work is asynchronous; chat output is marshalled back to the main thread.
 */
public final class ReputationManager {

    // =========================================================================
    // STATUS SCALE (Discord Account Standing)
    // =========================================================================

    /**
     * Discord Account Standing statuses for the second (status) scale:
     * All good → Limited → Very limited → At risk → Suspended.
     * Issued separately by a moderator, independent of the numeric scale.
     */
    public enum Status {
        NONE("None", "<dark_gray>—"),
        ALL_GOOD("All good", "<green>✔"),
        LIMITED("Limited", "<yellow>⚠"),
        VERY_LIMITED("Very limited", "<gold>⚠"),
        AT_RISK("At risk", "<red>⛔"),
        SUSPENDED("Suspended", "<dark_red>☠");

        private final String display;
        private final String icon;

        Status(String display, String icon) {
            this.display = display;
            this.icon = icon;
        }

        /** Localized display name (messages.reputation.status.<key>). */
        public String display() {
            return MessagesManager.getString("reputation.status." + name().toLowerCase(), display);
        }

        /** Colored icon in miniMessage. */
        public String iconMini() { return icon; }

        /** Icon + display name in one miniMessage string. */
        public String fullMini() { return icon + " " + display(); }

        /** Parses a config/argument value into a status (null if unknown). */
        public static Status fromString(String s) {
            if (s == null) return null;
            return switch (s.toLowerCase().replace("_", "").replace(" ", "")) {
                case "allgood", "good", "1" -> ALL_GOOD;
                case "limited", "2" -> LIMITED;
                case "verylimited", "3" -> VERY_LIMITED;
                case "atrisk", "risk", "4" -> AT_RISK;
                case "suspended", "5" -> SUSPENDED;
                case "none", "reset", "clear", "0" -> NONE;
                default -> null;
            };
        }
    }

    // =========================================================================
    // ENTRY TYPES
    // =========================================================================

    /** One row of the numeric reputation log. */
    public record RepLogEntry(int id, String actorName, int amount, String source,
                              String reason, long createdAt) {}

    /** Current numeric value + status of a player. */
    public record RepData(int rep, Status status) {}

    // =========================================================================
    // ASYNC HELPERS
    // =========================================================================

    private static void runAsync(Runnable r) {
        var sched = Bukkit.getScheduler();
        sched.runTaskAsynchronously(Main.getInstance(), r);
    }

    private static void runMain(Runnable r) {
        var sched = Bukkit.getScheduler();
        sched.runTask(Main.getInstance(), r);
    }

    // =========================================================================
    // PUBLIC READ API
    // =========================================================================

    /** Loads rep + status (async), result on the main thread. Never null. */
    public static void get(String uuid, Consumer<RepData> callback) {
        runAsync(() -> {
            RepData data = new RepData(getSync(uuid), statusSync(uuid));
            runMain(() -> callback.accept(data));
        });
    }

    /** Loads the last N log entries (async). */
    public static void getHistory(String uuid, int limit, Consumer<List<RepLogEntry>> callback) {
        runAsync(() -> {
            List<RepLogEntry> list = new ArrayList<>();
            String sql = """
                    SELECT id, actor_name, amount, source, reason, created_at
                    FROM reputation_log WHERE target_uuid = ?
                    ORDER BY id DESC LIMIT ?
                    """;
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new RepLogEntry(rs.getInt("id"), rs.getString("actor_name"),
                                rs.getInt("amount"), rs.getString("source"),
                                rs.getString("reason"), rs.getLong("created_at")));
                    }
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[Reputation] getHistory failed: " + e.getMessage());
            }
            runMain(() -> callback.accept(list));
        });
    }

    /** Loads the top-N by numeric reputation (async). Rows: name, rep. */
    public static void getTop(int limit, Consumer<List<String[]>> callback) {
        runAsync(() -> {
            List<String[]> list = new ArrayList<>();
            // player_name of the latest log row wins; players without log rows
            // fall back to their uuid tail (rare — only /ui rep set without give).
            String sql = """
                    SELECT COALESCE(
                        (SELECT actor_name FROM reputation_log g
                          WHERE g.target_uuid = r.player_uuid ORDER BY g.id DESC LIMIT 1),
                        substr(r.player_uuid, 1, 8)) AS name,
                        r.rep
                    FROM player_reputation r
                    ORDER BY r.rep DESC LIMIT ?
                    """;
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new String[]{rs.getString("name"), String.valueOf(rs.getInt("rep"))});
                    }
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[Reputation] getTop failed: " + e.getMessage());
            }
            runMain(() -> callback.accept(list));
        });
    }

    // =========================================================================
    // WRITE API
    // =========================================================================

    /**
     * Changes numeric reputation by the given delta (asynchronous).
     *
     * @param targetUuid target player uuid
     * @param targetName target player name (for the log and notify)
     * @param amount     delta, may be negative
     * @param source     "manual" | "report" | "punishment_warn" | "punishment_mute"
     *                   | "punishment_ban" | "set"
     * @param reason     free-form reason (may be empty)
     * @param actorName  who issued the change (staff name or "Server")
     * @param notify     notify staff with {@code ui.rep.notify}
     * @param callback   main-thread callback with the new value (null on DB error)
     */
    public static void change(String targetUuid, String targetName, int amount,
                              String source, String reason, String actorName,
                              boolean notify, Consumer<Integer> callback) {
        runAsync(() -> {
            Integer newVal = changeSync(targetUuid, targetName, amount, source, reason, actorName);
            final Integer fin = newVal;
            if (callback != null) runMain(() -> callback.accept(fin));
            if (notify && fin != null) {
                String path = amount >= 0 ? "reputation.notify.give" : "reputation.notify.take";
                String defGive = "<green>▲</green> <gray>Reputation </gray><yellow>%player%</yellow><gray>: +%amount%</gray> <dark_gray>(%actor%)</dark_gray>";
                String defTake = "<red>▼</red> <gray>Reputation </gray><yellow>%player%</yellow><gray>: %amount%</gray> <dark_gray>(%actor%)</dark_gray>";
                String def = amount >= 0 ? defGive : defTake;
                broadcastStaff(MessagesManager.getString(path, def)
                        .replace("%player%", targetName)
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%actor%", actorName));
            }
        });
    }

    /** Sets an absolute value (admin correction, "set" source). */
    public static void set(String targetUuid, String targetName, int value,
                           String reason, String actorName,
                           boolean notify, Consumer<Integer> callback) {
        runAsync(() -> {
            Integer oldVal = getSync(targetUuid);
            int old = oldVal == null ? 0 : oldVal;
            int amount = value - old;
            int newVal = upsertSync(targetUuid, value, null);
            logChange(targetUuid, targetName, amount, "set", reason, actorName);
            final Integer fin = newVal;
            if (callback != null) runMain(() -> callback.accept(fin));
            if (notify && fin != null) {
                String def = "<white>◆</white> <gray>Reputation </gray><yellow>%player%</yellow><gray>: %old% → %new%</gray> <dark_gray>(%actor%)</dark_gray>";
                broadcastStaff(MessagesManager.getString("reputation.notify.set", def)
                        .replace("%player%", targetName)
                        .replace("%old%", String.valueOf(old))
                        .replace("%new%", String.valueOf(newVal))
                        .replace("%actor%", actorName));
            }
        });
    }

    /**
     * Sets the Discord-style status (async). Used by /ui rep status.
     */
    public static void setStatus(String targetUuid, Status status, String actorName,
                                 boolean notify, Consumer<Status> callback) {
        runAsync(() -> {
            Status applied = upsertStatusSync(targetUuid, status);
            final Status fin = applied;
            if (callback != null) runMain(() -> callback.accept(fin));
            if (notify && fin != null) {
                String def = "<white>◆</white> <gray>Status </gray><yellow>%player%</yellow><gray>: %status%</gray> <dark_gray>(%actor%)</dark_gray>";
                String targetName = nameOf(targetUuid);
                broadcastStaff(MessagesManager.getString("reputation.notify.status", def)
                        .replace("%player%", targetName)
                        .replace("%status%", fin.fullMini())
                        .replace("%actor%", actorName));
            }
        });
    }

    /**
     * Source-based change for hooks: report verdicts and punishments.
     * Reads the amount from the config ({@code reputation.report_confirmed},
     * {@code reputation.punish_warn/mute/ban}); amount 0 → no-op.
     *
     * @return true if a change was scheduled
     */
    public static boolean changeBySource(String targetUuid, String targetName, String source,
                                         String reason) {
        FileConfiguration cfg = Main.getInstance().getConfig();
        String key = switch (source) {
            case "report" -> "reputation.report_confirmed";
            case "punish_warn" -> "reputation.punish_warn";
            case "punish_mute" -> "reputation.punish_mute";
            case "punish_ban" -> "reputation.punish_ban";
            default -> null;
        };
        if (key == null) return false;
        int amount = cfg.getInt(key, 0);
        if (amount == 0) return false;
        change(targetUuid, targetName, amount, source, reason, "Server", false, null);
        return true;
    }

    // =========================================================================
    // SYNC CORE (async thread only)
    // =========================================================================

    /** Applies the delta, logs it; returns the new value (async thread only). */
    private static Integer changeSync(String targetUuid, String targetName, int amount,
                                      String source, String reason, String actorName) {
        int oldVal = getSync(targetUuid);
        int newVal = oldVal + amount;
        upsertSync(targetUuid, newVal, null);
        logChange(targetUuid, targetName, amount, source, reason, actorName);
        return newVal;
    }

    /** Reads the current numeric value (async thread only). */
    private static int getSync(String uuid) {
        String sql = "SELECT rep FROM player_reputation WHERE player_uuid = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("rep");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Reputation] getSync failed: " + e.getMessage());
        }
        return 0;
    }

    /** Reads the current status (async thread only). */
    private static Status statusSync(String uuid) {
        String sql = "SELECT status FROM player_reputation WHERE player_uuid = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Status s = Status.fromString(rs.getString("status"));
                    return s == null ? Status.NONE : s;
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Reputation] statusSync failed: " + e.getMessage());
        }
        return Status.NONE;
    }

    /** Insert-or-update the numeric value; returns the stored value. */
    private static int upsertSync(String uuid, int rep, Status ignored) {
        String sql = """
                INSERT INTO player_reputation (player_uuid, rep, status, updated_at)
                VALUES (?, ?, 'NONE', strftime('%s','now'))
                ON CONFLICT(player_uuid) DO UPDATE SET
                    rep = excluded.rep,
                    updated_at = strftime('%s','now')
                """;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setInt(2, rep);
            ps.executeUpdate();
            return rep;
        } catch (Exception e) {
            ConsoleLogger.warn("[Reputation] upsertSync failed: " + e.getMessage());
            return rep;
        }
    }

    /** Insert-or-update only the status; returns the applied status. */
    private static Status upsertStatusSync(String uuid, Status status) {
        String sql = """
                INSERT INTO player_reputation (player_uuid, rep, status, updated_at)
                VALUES (?, 0, ?, strftime('%s','now'))
                ON CONFLICT(player_uuid) DO UPDATE SET
                    status = excluded.status,
                    updated_at = strftime('%s','now')
                """;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, status.name());
            ps.executeUpdate();
            return status;
        } catch (Exception e) {
            ConsoleLogger.warn("[Reputation] upsertStatusSync failed: " + e.getMessage());
            return status;
        }
    }

    /** Appends a row to the reputation log (async thread only). */
    private static void logChange(String targetUuid, String targetName, int amount,
                                  String source, String reason, String actorName) {
        String sql = """
                INSERT INTO reputation_log
                    (target_uuid, target_name, actor_uuid, actor_name, amount, source, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, strftime('%s','now'))
                """;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, targetUuid);
            ps.setString(2, targetName);
            ps.setString(3, "");
            ps.setString(4, actorName);
            ps.setInt(5, amount);
            ps.setString(6, source);
            ps.setString(7, reason == null ? "" : reason);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Reputation] logChange failed: " + e.getMessage());
        }
    }

    /** Best-effort current name for a uuid (log rows first, then online). */
    private static String nameOf(String uuid) {
        try {
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));
            if (p != null) return p.getName();
        } catch (IllegalArgumentException ignored) {
            // not a valid uuid — return as-is
        }
        String sql = "SELECT target_name FROM reputation_log WHERE target_uuid = ? AND target_name != '' ORDER BY id DESC LIMIT 1";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("target_name");
            }
        } catch (Exception ignored) {
            // fall through to uuid tail
        }
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    /** Sends a message to every online staff member with ui.rep.notify. */
    private static void broadcastStaff(String miniMessage) {
        Component comp = MessageUtil.parse(miniMessage);
        runMain(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("ui.rep.notify")) p.sendMessage(comp);
            }
        });
    }
}
