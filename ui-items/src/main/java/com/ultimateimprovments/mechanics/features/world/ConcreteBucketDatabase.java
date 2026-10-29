package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 💾 SQLite persistence for poured concrete-bucket water.
 * <p>
 * Poured water lives in memory only: the gray {@code PALE_GARDEN} tint and the
 * 60-second hardening timer would be lost on a restart, leaving tinted water
 * that never hardens (and a biome change that is never rolled back). Every
 * tracked block is therefore stored in {@code concrete_bucket_water} together
 * with the biome it had before the pour and its pour group's start time.
 */
public final class ConcreteBucketDatabase {

    static final String TABLE = "concrete_bucket_water";

    private ConcreteBucketDatabase() {}

    /** True when the shared SQLite connection is usable. */
    public static boolean isAvailable() {
        return DatabaseManager.isConnected();
    }

    /** Block coordinates + world name — the DB primary key. */
    public record BlockPos(String world, int x, int y, int z) {}

    /** A tracked water block, as stored in the DB. */
    public record StoredWater(BlockPos pos, String originalBiome, String groupId, long startTime) {}

    // =========================
    // LOAD
    // =========================
    public static List<StoredWater> loadAll() {
        List<StoredWater> rows = new ArrayList<>();
        if (!DatabaseManager.isConnected()) return rows;

        String sql = "SELECT world, x, y, z, original_biome, group_id, start_time FROM " + TABLE;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {

            while (rs.next()) {
                rows.add(new StoredWater(
                        new BlockPos(
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z")),
                        rs.getString("original_biome"),
                        rs.getString("group_id"),
                        rs.getLong("start_time")));
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[ConcreteBucket] Failed to load tracked water from DB: " + e.getMessage());
        }
        return rows;
    }

    // =========================
    // SAVE
    // =========================
    public static void insertAll(Collection<StoredWater> rows) {
        if (rows.isEmpty() || !DatabaseManager.isConnected()) return;

        String sql = "INSERT OR REPLACE INTO " + TABLE
                + " (world, x, y, z, original_biome, group_id, start_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {

            for (StoredWater row : rows) {
                st.setString(1, row.pos().world());
                st.setInt(2, row.pos().x());
                st.setInt(3, row.pos().y());
                st.setInt(4, row.pos().z());
                st.setString(5, row.originalBiome());
                st.setString(6, row.groupId());
                st.setLong(7, row.startTime());
                st.addBatch();
            }
            st.executeBatch();

        } catch (Exception e) {
            ConsoleLogger.warn("[ConcreteBucket] Failed to save tracked water to DB: " + e.getMessage());
        }
    }

    // =========================
    // DELETE
    // =========================
    public static void deleteAll(Collection<BlockPos> positions) {
        if (positions.isEmpty() || !DatabaseManager.isConnected()) return;

        String sql = "DELETE FROM " + TABLE + " WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {

            for (BlockPos pos : positions) {
                st.setString(1, pos.world());
                st.setInt(2, pos.x());
                st.setInt(3, pos.y());
                st.setInt(4, pos.z());
                st.addBatch();
            }
            st.executeBatch();

        } catch (Exception e) {
            ConsoleLogger.warn("[ConcreteBucket] Failed to delete tracked water from DB: " + e.getMessage());
        }
    }
}
