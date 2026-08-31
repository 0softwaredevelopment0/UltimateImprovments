package com.ultimateimprovments.op;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OP Manager — persistent operator list stored in SQLite.
 * <p>
 * Manages the authoritative list of who should have OP.
 * Commands: /ui op, /ui deop, /ui oplist.
 */
public final class OpManager {

    /** Confirmation timeout in milliseconds (30 seconds). */
    public static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private static final Map<String, PendingAction> pendingActions = new ConcurrentHashMap<>();

    private OpManager() {}

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init() {
        createTable();
        ConsoleLogger.info("[OpManager] Initialized.");
    }

    private static void createTable() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS op_list (
                    player_name TEXT PRIMARY KEY,
                    added_by TEXT,
                    added_at INTEGER
                )
            """);
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpManager] Failed to create table: " + e.getMessage());
        }
    }

    public static void shutdown() {
        pendingActions.clear();
    }

    // ════════════════════════════════════════
    // OP LIST MANAGEMENT
    // ════════════════════════════════════════
    public static boolean add(String playerName, String addedBy) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO op_list (player_name, added_by, added_at) VALUES (?, ?, ?)")) {
            st.setString(1, lower);
            st.setString(2, addedBy);
            st.setLong(3, System.currentTimeMillis());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpManager] Failed to add: " + lower + " — " + e.getMessage());
            return false;
        }
    }

    public static boolean remove(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM op_list WHERE player_name = ?")) {
            st.setString(1, lower);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpManager] Failed to remove: " + lower + " — " + e.getMessage());
            return false;
        }
    }

    public static boolean isInList(String playerName) {
        String lower = playerName.toLowerCase().trim();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT 1 FROM op_list WHERE player_name = ?")) {
            st.setString(1, lower);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Returns the full sorted OP list (lowercase names).
     */
    public static List<String> getList() {
        List<String> result = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT player_name FROM op_list ORDER BY player_name")) {
            while (rs.next()) {
                result.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpManager] Failed to get list: " + e.getMessage());
        }
        return result;
    }

    public static int getCount() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM op_list")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            ConsoleLogger.warn("[OpManager] Failed to count: " + e.getMessage());
        }
        return 0;
    }

    // ════════════════════════════════════════
    // CONFIRMATION SYSTEM
    // ════════════════════════════════════════

    /**
     * Stores a pending action for a sender.
     */
    public static void setPending(String senderId, String action, String target) {
        pendingActions.put(senderId.toLowerCase(), new PendingAction(action, target, System.currentTimeMillis()));
    }

    /**
     * Consumes and returns the pending action if valid (exists and not expired).
     * Returns null if no valid pending action found.
     */
    public static PendingAction consumePending(String senderId, String expectedAction) {
        String key = senderId.toLowerCase();
        PendingAction pending = pendingActions.get(key);
        if (pending == null) return null;
        if (!pending.action().equalsIgnoreCase(expectedAction)) return null;
        if (System.currentTimeMillis() - pending.createdAt() > CONFIRM_TIMEOUT_MS) {
            pendingActions.remove(key);
            return null;
        }
        pendingActions.remove(key);
        return pending;
    }

    /**
     * Returns the sender identifier for confirmation tracking.
     * Players use UUID, console uses "console".
     */
    public static String getSenderId(org.bukkit.command.CommandSender sender) {
        if (sender instanceof org.bukkit.entity.Player p) {
            return p.getUniqueId().toString();
        }
        return sender.getName().toLowerCase();
    }

    /**
     * Cleans up expired pending actions. Call periodically if desired.
     */
    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingActions.entrySet().removeIf(e ->
                now - e.getValue().createdAt() > CONFIRM_TIMEOUT_MS);
    }

    /**
     * Pending action record.
     */
    public record PendingAction(String action, String target, long createdAt) {}
}
