package com.ultimateimprovments.database;

import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic namespaced key/value persistence over the {@code ui_state} table.
 * <p>
 * Used to keep small in-memory user data (chat preferences, anti-cheat check
 * state, etc.) across a server restart, so nothing is lost on shutdown.
 * <p>
 * Follows the same synchronous pattern as the rest of the database layer
 * (blocking, expected to be called from the main thread or async-safely).
 */
public final class StateStore {

    private StateStore() {}

    /** Reads a single value. Returns {@code null} if absent. */
    public static String get(String namespace, String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT state_value FROM ui_state WHERE namespace = ? AND state_key = ?")) {
            st.setString(1, namespace);
            st.setString(2, key);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[StateStore] get failed: " + e.getMessage());
        }
        return null;
    }

    /** Writes (insert-or-replace) a single value. */
    public static void put(String namespace, String key, String value) {
        if (value == null) {
            remove(namespace, key);
            return;
        }
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO ui_state (namespace, state_key, state_value, updated_at) VALUES (?, ?, ?, ?)")) {
            st.setString(1, namespace);
            st.setString(2, key);
            st.setString(3, value);
            st.setLong(4, System.currentTimeMillis());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[StateStore] put failed: " + e.getMessage());
        }
    }

    /** Removes a single value. */
    public static void remove(String namespace, String key) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM ui_state WHERE namespace = ? AND state_key = ?")) {
            st.setString(1, namespace);
            st.setString(2, key);
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[StateStore] remove failed: " + e.getMessage());
        }
    }

    /** Clears all values of a namespace. */
    public static void clearNamespace(String namespace) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM ui_state WHERE namespace = ?")) {
            st.setString(1, namespace);
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[StateStore] clearNamespace failed: " + e.getMessage());
        }
    }

    /** Loads all values of a namespace into a map. */
    public static Map<String, String> getAll(String namespace) {
        Map<String, String> out = new HashMap<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT state_key, state_value FROM ui_state WHERE namespace = ?")) {
            st.setString(1, namespace);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getString(2));
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[StateStore] getAll failed: " + e.getMessage());
        }
        return out;
    }
}