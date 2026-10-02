package com.ultimateimprovments.database;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

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
 *   scoreboard_enabled INTEGER DEFAULT 1,
 *   ping_enabled INTEGER DEFAULT 1,
 *   wireless_bind_enabled INTEGER DEFAULT 0 (bind is opt-in: /ui wirelessbind on)
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
            boolean pingEnabled,
            boolean wirelessBindEnabled
    ) {
        public PlayerSettings withBossbar(boolean val) {
            return new PlayerSettings(uuid, val, scoreboardEnabled, pingEnabled, wirelessBindEnabled);
        }
        public PlayerSettings withScoreboard(boolean val) {
            return new PlayerSettings(uuid, bossbarEnabled, val, pingEnabled, wirelessBindEnabled);
        }
        public PlayerSettings withPing(boolean val) {
            return new PlayerSettings(uuid, bossbarEnabled, scoreboardEnabled, val, wirelessBindEnabled);
        }
        public PlayerSettings withWirelessBind(boolean val) {
            return new PlayerSettings(uuid, bossbarEnabled, scoreboardEnabled, pingEnabled, val);
        }
    }

    // =========================
    // INIT — create table + load all
    // =========================

    public static void init() {
        createTable();
        loadAll();
        resetWirelessBindDefaultOnce();
    }

    private static void createTable() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_settings (" +
                     "uuid TEXT PRIMARY KEY," +
                     "bossbar_enabled INTEGER DEFAULT 1," +
                     "scoreboard_enabled INTEGER DEFAULT 1," +
                     "ping_enabled INTEGER DEFAULT 1," +
                     "wireless_bind_enabled INTEGER DEFAULT 0" +
                     ")")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Create table failed: " + e.getMessage());
        }
        // Migrations: tables created before ping/wireless-bind columns existed.
        // CREATE TABLE IF NOT EXISTS does not add columns to an existing table.
        migrateAddColumn("ping_enabled", 1);
        migrateAddColumn("wireless_bind_enabled", 0);
    }

    private static void migrateAddColumn(String column, int defaultValue) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "ALTER TABLE player_settings ADD COLUMN " + column + " INTEGER DEFAULT " + defaultValue)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    private static void loadAll() {
        cache.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid, bossbar_enabled, scoreboard_enabled, ping_enabled, wireless_bind_enabled FROM player_settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    boolean bb = rs.getInt("bossbar_enabled") == 1;
                    boolean sb = rs.getInt("scoreboard_enabled") == 1;
                    boolean ping = rs.getInt("ping_enabled") == 1;
                    boolean wb = rs.getInt("wireless_bind_enabled") == 1;
                    cache.put(uuid, new PlayerSettings(uuid, bb, sb, ping, wb));
                } catch (IllegalArgumentException ignored) {}
            }
            ConsoleLogger.info("[PlayerSettings] Loaded " + cache.size() + " player settings from DB");
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Load failed: " + e.getMessage());
        }
    }

    /**
     * One-time migration: until now the wireless bind default was ON in the
     * DDL although the documented default was OFF ("default: off" in
     * /ui wirelessbind and /ui help) — rows auto-created with the old default
     * armed shift+RMB binding for players who never used the feature. Every
     * stored value is flipped back to 0 exactly once (guarded by a marker in
     * ui_state); players who want the bind re-enable it with
     * {@code /ui wirelessbind on}.
     */
    private static void resetWirelessBindDefaultOnce() {
        final String markerKey = "wireless_bind_default_reset";
        if ("1".equals(StateStore.get("player_settings", markerKey))) {
            return;
        }
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE player_settings SET wireless_bind_enabled = 0")) {
            int rows = ps.executeUpdate();
            for (PlayerSettings settings : cache.values()) {
                if (settings.wirelessBindEnabled()) {
                    cache.put(settings.uuid(), settings.withWirelessBind(false));
                }
            }
            StateStore.put("player_settings", markerKey, "1");
            if (rows > 0) {
                ConsoleLogger.info("[PlayerSettings] Wireless bind reset to OFF (default change): "
                        + rows + " row(s) — players re-enable it with /ui wirelessbind on.");
            }
        } catch (SQLException e) {
            ConsoleLogger.error("[PlayerSettings] Wireless bind default reset failed: " + e.getMessage());
        }
    }

    // =========================
    // GET / SET
    // =========================

    public static PlayerSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, u ->
                new PlayerSettings(u, true, true, true, false));
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

    public static boolean isWirelessBindEnabled(UUID uuid) {
        return get(uuid).wirelessBindEnabled();
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
     * Toggle wireless redstone binding. Returns the new state.
     */
    public static boolean toggleWirelessBind(UUID uuid) {
        PlayerSettings cur = get(uuid);
        boolean newVal = !cur.wirelessBindEnabled();
        cache.put(uuid, cur.withWirelessBind(newVal));
        saveSetting(uuid, "wireless_bind_enabled", newVal);
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

    /**
     * Explicitly set wireless bind state.
     */
    public static void setWirelessBindEnabled(UUID uuid, boolean enabled) {
        PlayerSettings cur = get(uuid);
        cache.put(uuid, cur.withWirelessBind(enabled));
        saveSetting(uuid, "wireless_bind_enabled", enabled);
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
