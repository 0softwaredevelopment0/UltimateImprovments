package com.mcplugin.infrastructure.database;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player settings persisted in SQLite.
 * <p>
 * Table: player_settings
 *   uuid TEXT PRIMARY KEY,
 *   bossbar_enabled INTEGER DEFAULT 1,
 *   scoreboard_enabled INTEGER DEFAULT 1
 */
public class PlayerSettingsDB {

    private static final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    private PlayerSettingsDB() {}

    // =========================
    // DATA CLASS
    // =========================

    public record PlayerSettings(
            UUID uuid,
            boolean bossbarEnabled,
            boolean scoreboardEnabled,
            boolean pingEnabled
    ) {
        public PlayerSettings withBossbar(boolean val) {
            return new PlayerSettings(uuid, val, scoreboardEnabled, pingEnabled);
        }
        public PlayerSettings withScoreboard(boolean val) {
            return new PlayerSettings(uuid, bossbarEnabled, val, pingEnabled);
        }
        public PlayerSettings withPing(boolean val) {
            return new PlayerSettings(uuid, bossbarEnabled, scoreboardEnabled, val);
        }
    }

    // =========================
    // INIT — create table + load all
    // =========================

    public static void init() {
        createTable();
        loadAll();
    }

    private static void createTable() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_settings (" +
                     "uuid TEXT PRIMARY KEY," +
                     "bossbar_enabled INTEGER DEFAULT 1," +
                     "scoreboard_enabled INTEGER DEFAULT 1," +
                     "ping_enabled INTEGER DEFAULT 1" +
                     ")")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Create table failed: " + e.getMessage());
        }
    }

    private static void loadAll() {
        cache.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid, bossbar_enabled, scoreboard_enabled, ping_enabled FROM player_settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    boolean bb = rs.getInt("bossbar_enabled") == 1;
                    boolean sb = rs.getInt("scoreboard_enabled") == 1;
                    boolean ping = rs.getInt("ping_enabled") == 1;
                    cache.put(uuid, new PlayerSettings(uuid, bb, sb, ping));
                } catch (IllegalArgumentException ignored) {}
            }
            ConsoleLogger.info("[PlayerSettings] Loaded " + cache.size() + " player settings from DB");
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Load failed: " + e.getMessage());
        }
    }

    // =========================
    // GET / SET
    // =========================

    public static PlayerSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, u ->
                new PlayerSettings(u, true, true, true));
    }

    public static boolean isBossbarEnabled(UUID uuid) {
        return get(uuid).bossbarEnabled();
    }

    public static boolean isScoreboardEnabled(UUID uuid) {
        return get(uuid).scoreboardEnabled();
    }

    public static boolean isPingEnabled(UUID uuid) {
        return get(uuid).pingEnabled();
    }

    /**
     * Toggle bossbar. Returns the new state.
     */
    public static boolean toggleBossbar(UUID uuid) {
        PlayerSettings cur = get(uuid);
        boolean newVal = !cur.bossbarEnabled();
        cache.put(uuid, cur.withBossbar(newVal));
        saveSetting(uuid, "bossbar_enabled", newVal);
        return newVal;
    }

    /**
     * Toggle scoreboard. Returns the new state.
     */
    public static boolean toggleScoreboard(UUID uuid) {
        PlayerSettings cur = get(uuid);
        boolean newVal = !cur.scoreboardEnabled();
        cache.put(uuid, cur.withScoreboard(newVal));
        saveSetting(uuid, "scoreboard_enabled", newVal);
        return newVal;
    }

    /**
     * Toggle ping sound. Returns the new state.
     */
    public static boolean togglePing(UUID uuid) {
        PlayerSettings cur = get(uuid);
        boolean newVal = !cur.pingEnabled();
        cache.put(uuid, cur.withPing(newVal));
        saveSetting(uuid, "ping_enabled", newVal);
        return newVal;
    }

    /**
     * Explicitly set bossbar state.
     */
    public static void setBossbarEnabled(UUID uuid, boolean enabled) {
        PlayerSettings cur = get(uuid);
        cache.put(uuid, cur.withBossbar(enabled));
        saveSetting(uuid, "bossbar_enabled", enabled);
    }

    /**
     * Explicitly set scoreboard state.
     */
    public static void setScoreboardEnabled(UUID uuid, boolean enabled) {
        PlayerSettings cur = get(uuid);
        cache.put(uuid, cur.withScoreboard(enabled));
        saveSetting(uuid, "scoreboard_enabled", enabled);
    }

    /**
     * Explicitly set ping sound state.
     */
    public static void setPingEnabled(UUID uuid, boolean enabled) {
        PlayerSettings cur = get(uuid);
        cache.put(uuid, cur.withPing(enabled));
        saveSetting(uuid, "ping_enabled", enabled);
    }

    // =========================
    // DB PERSISTENCE
    // =========================

    private static void saveSetting(UUID uuid, String column, boolean value) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO player_settings (uuid, " + column + ") VALUES (?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET " + column + " = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, value ? 1 : 0);
            ps.setInt(3, value ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Save failed: " + e.getMessage());
        }
    }

    /**
     * Remove a player's settings (on unlink etc.)
     */
    public static void remove(UUID uuid) {
        cache.remove(uuid);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM player_settings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Delete failed: " + e.getMessage());
        }
    }
}
