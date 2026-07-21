package com.mcplugin.mechanics.protection;

import com.mcplugin.core.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite-персистенция для «Блоков защиты».
 * <p>
 * Таблицы:
 * <ul>
 *   <li>{@code protection_blocks} — UUID, world, x/y/z, owner, radius, integrity, points, enabled,
 *       radius_upgrade_count, repair_count</li>
 *   <li>{@code protection_whitelist} — (block_id, player_uuid) — игроки, имеющие доступ</li>
 * </ul>
 */
public class ProtectionDatabase {

    private static volatile boolean ready = false;

    public static boolean isReady() { return ready; }

    // =========================
    // INIT TABLE
    // =========================
    public static synchronized void initTables() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS protection_blocks (
                    id              TEXT PRIMARY KEY,
                    world           TEXT NOT NULL,
                    x               INTEGER NOT NULL,
                    y               INTEGER NOT NULL,
                    z               INTEGER NOT NULL,
                    owner_uuid      TEXT NOT NULL,
                    radius          INTEGER NOT NULL,
                    integrity       REAL NOT NULL,
                    points          INTEGER NOT NULL,
                    enabled         INTEGER NOT NULL,
                    radius_upgrade_count INTEGER NOT NULL DEFAULT 0,
                    repair_count    INTEGER NOT NULL DEFAULT 0,
                    created_at      INTEGER NOT NULL
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS protection_whitelist (
                    block_id        TEXT NOT NULL,
                    player_uuid     TEXT NOT NULL,
                    PRIMARY KEY (block_id, player_uuid),
                    FOREIGN KEY (block_id) REFERENCES protection_blocks(id) ON DELETE CASCADE
                );
            """);

            ready = true;
            ConsoleLogger.info("[ProtectionBlock] DB tables ready.");
        } catch (Exception e) {
            ConsoleLogger.error("[ProtectionBlock] DB init failed: " + e.getMessage());
            ready = false;
        }
    }

    // =========================
    // SAVE BLOCK (insert or replace)
    // =========================
    public static synchronized void saveBlock(ProtectionBlock block) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO protection_blocks
                (id, world, x, y, z, owner_uuid, radius, integrity, points,
                 enabled, radius_upgrade_count, repair_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            ps.setString(1, block.getId().toString());
            ps.setString(2, block.getWorld().getName());
            ps.setInt(3, block.getX());
            ps.setInt(4, block.getY());
            ps.setInt(5, block.getZ());
            ps.setString(6, block.getOwner() != null ? block.getOwner().toString() : "");
            ps.setInt(7, block.getRadius());
            ps.setDouble(8, block.getIntegrity());
            ps.setInt(9, block.getPoints());
            ps.setInt(10, block.isEnabled() ? 1 : 0);
            ps.setInt(11, block.getRadiusUpgradeCount());
            ps.setInt(12, block.getRepairCount());
            ps.setLong(13, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[ProtectionBlock] Save failed: " + e.getMessage());
        }
    }

    // =========================
    // SAVE WHITELIST (replace all entries for block)
    // =========================
    public static synchronized void saveWhitelist(ProtectionBlock block) {
        try (Connection con = DatabaseManager.getConnection()) {
            // Delete existing
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM protection_whitelist WHERE block_id = ?")) {
                del.setString(1, block.getId().toString());
                del.executeUpdate();
            }
            // Insert all
            try (PreparedStatement ins = con.prepareStatement(
                    "INSERT INTO protection_whitelist (block_id, player_uuid) VALUES (?, ?)")) {
                for (UUID pid : block.getWhitelist()) {
                    ins.setString(1, block.getId().toString());
                    ins.setString(2, pid.toString());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        } catch (Exception e) {
            ConsoleLogger.error("[ProtectionBlock] Whitelist save failed: " + e.getMessage());
        }
    }

    // =========================
    // LOAD ALL BLOCKS ON STARTUP
    // =========================
    public static synchronized List<LoadedBlock> loadAllBlocks() {
        List<LoadedBlock> list = new ArrayList<>();
        if (!ready) return list;
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM protection_blocks");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    String worldName = rs.getString("world");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    UUID owner = rs.getString("owner_uuid") != null && !rs.getString("owner_uuid").isEmpty()
                            ? UUID.fromString(rs.getString("owner_uuid")) : null;
                    int radius = rs.getInt("radius");
                    double integrity = rs.getDouble("integrity");
                    int points = rs.getInt("points");
                    boolean enabled = rs.getInt("enabled") == 1;
                    int radiusUp = rs.getInt("radius_upgrade_count");
                    int repair = rs.getInt("repair_count");

                    List<UUID> wl = new ArrayList<>();
                    try (PreparedStatement ps2 = con.prepareStatement(
                            "SELECT player_uuid FROM protection_whitelist WHERE block_id = ?")) {
                        ps2.setString(1, id.toString());
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                try { wl.add(UUID.fromString(rs2.getString(1))); }
                                catch (Exception ignored) {}
                            }
                        }
                    }
                    list.add(new LoadedBlock(id, worldName, x, y, z, owner,
                            radius, integrity, points, enabled, radiusUp, repair, wl));
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[ProtectionBlock] Load all failed: " + e.getMessage());
        }
        return list;
    }

    // =========================
    // DELETE BLOCK (cascade whitelist)
    // =========================
    public static synchronized void deleteBlock(UUID id) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM protection_blocks WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[ProtectionBlock] Delete failed: " + e.getMessage());
        }
    }

    /** Вспомогательная POJO для загруженного блока до воссоздания ProtectionBlock с Location. */
    public record LoadedBlock(
            UUID id, String worldName, int x, int y, int z, UUID owner,
            int radius, double integrity, int points, boolean enabled,
            int radiusUpgradeCount, int repairCount, List<UUID> whitelist
    ) {}
}
