package com.ultimateimprovments.energy.transfer.cable;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.energy.storage.battery.BatteryPool;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQLite persistence for stored node energy (batteries).
 * <p>
 * Energy lives in the shared {@link BatteryPool} of a battery multiblock
 * (positions live in {@code structure_markers}). This table restores the
 * values after a restart or a soft {@code /ui reload}: a rewrite
 * (DELETE + INSERT in one transaction) runs every save interval and on
 * shutdown, and {@link #load(Collection)} puts the values back into the
 * freshly created pools.
 * <p>
 * The rewrite only clears the rows of worlds that are LOADED right now (a
 * battery in a world that is not loaded yet — Multiverse, delayed world
 * load — cannot be read from RAM, so its rows must survive until that world
 * loads and the battery manager restores them). Rows for batteries that no
 * longer exist in a loaded world are dropped by the same rewrite.
 */
public final class EnergyPersistence {

    private EnergyPersistence() {}

    // =========================
    // INIT
    // =========================
    public static void init() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS energy_storage (
                         world  TEXT    NOT NULL,
                         x      INTEGER NOT NULL,
                         y      INTEGER NOT NULL,
                         z      INTEGER NOT NULL,
                         energy INTEGER NOT NULL,
                         PRIMARY KEY (world, x, y, z)
                     );
                     """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[EnergyPersistence] Failed to create table: " + e.getMessage());
        }
    }

    // =========================
    // LOAD
    // =========================

    /**
     * Restores every battery in the table (startup path).
     * Prefer {@link #load(Collection)} with the freshly rebuilt pools when
     * only part of the world set was (re)built.
     */
    public static void load() {
        load(null);
    }

    /**
     * Restores energy into the battery pools of the (already rebuilt) cable
     * network.
     * <p>
     * One multiblock battery = one pool. Old rows were written per block
     * (each block kept its own charge), so the values are SUMMED per pool and
     * clamped to the cluster capacity — that migrates an existing world
     * without losing a single volt. New saves write one row per pool, which
     * the same sum handles unchanged.
     *
     * @param onlyPools when non-null, rows whose battery belongs to any other
     *                  pool are ignored — a cluster rebuilt later (a world
     *                  loaded after startup) restores its own charge without
     *                  overwriting live values of already running batteries.
     */
    public static void load(Collection<BatteryPool> onlyPools) {
        if (onlyPools != null && onlyPools.isEmpty()) return;

        int restored = 0;
        Map<BatteryPool, Integer> pooledEnergy = new IdentityHashMap<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT world, x, y, z, energy FROM energy_storage");
             ResultSet rs = st.executeQuery()) {

            while (rs.next()) {
                String worldUid = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int energy = rs.getInt("energy");
                if (energy <= 0) continue;

                CableNode node = CableNetwork.getNodeByKey(worldUid, LocationUtil.toKey(x, y, z));
                if (node == null || node.getType() != NodeType.BATTERY) continue;

                BatteryPool pool = node.getBatteryPool();
                if (pool != null) {
                    if (onlyPools != null && !onlyPools.contains(pool)) continue;
                    pooledEnergy.merge(pool, energy,
                            (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
                    restored++;
                    continue;
                }

                // Legacy node without a shared pool (single-block battery).
                node.setMaxEnergy(Math.max(node.getMaxEnergy(), EnergyFlow.batteryCapacity()));
                node.setEnergy(Math.min(energy, node.getMaxEnergy()));
                restored++;
            }

            for (Map.Entry<BatteryPool, Integer> entry : pooledEnergy.entrySet()) {
                BatteryPool pool = entry.getKey();
                pool.setEnergy(Math.min(entry.getValue(), pool.getMaxEnergy()));
            }

            if (restored > 0) {
                ConsoleLogger.info("[EnergyPersistence] Restored energy for " + restored + " battery blocks.");
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[EnergyPersistence] Failed to load energy: " + e.getMessage());
        }
    }

    // =========================
    // SAVE — full rewrite in one transaction
    // =========================
    public static void saveAll() {
        // Snapshot first (node iteration must not hold the DB write).
        record Row(String world, int x, int y, int z, int energy) {}
        List<Row> rows = new ArrayList<>();
        // One row per storage unit: a whole multiblock battery writes its
        // pool once (through its first block) instead of duplicating the same
        // total on every block.
        Set<BatteryPool> savedPools = Collections.newSetFromMap(new IdentityHashMap<>());

        CableNetwork.forEachNode(node -> {
            if (node == null || node.getType() != NodeType.BATTERY) return;
            if (node.getEnergy() <= 0) return;
            BatteryPool pool = node.getBatteryPool();
            if (pool != null && !savedPools.add(pool)) return;
            Location loc = node.getLocation();
            if (loc == null || loc.getWorld() == null) return;
            rows.add(new Row(
                    loc.getWorld().getUID().toString(),
                    node.getBlockX(), node.getBlockY(), node.getBlockZ(),
                    node.getEnergy()));
        });

        // Only the worlds that are loaded right now can be read from RAM.
        // Their rows are rewritten in full; rows of not-yet-loaded worlds stay
        // untouched so a late world (Multiverse) still restores its batteries.
        List<String> loadedWorlds = loadedWorldUids();

        if (rows.isEmpty()) {
            // Nothing to store — still clear the loaded worlds so stale rows
            // from drained/disassembled batteries don't resurrect.
            try (Connection con = DatabaseManager.getConnection()) {
                deleteRows(con, loadedWorlds);
            } catch (SQLException e) {
                ConsoleLogger.warn("[EnergyPersistence] Failed to clear energy: " + e.getMessage());
            }
            return;
        }

        try (Connection con = DatabaseManager.getConnection()) {
            boolean oldAutoCommit = con.getAutoCommit();
            try {
                con.setAutoCommit(false);
                deleteRows(con, loadedWorlds);
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT OR REPLACE INTO energy_storage (world, x, y, z, energy) VALUES (?, ?, ?, ?, ?)")) {
                    for (Row row : rows) {
                        ins.setString(1, row.world());
                        ins.setInt(2, row.x());
                        ins.setInt(3, row.y());
                        ins.setInt(4, row.z());
                        ins.setInt(5, row.energy());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                con.commit();
            } catch (SQLException e) {
                try { con.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { con.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[EnergyPersistence] Failed to save energy: " + e.getMessage());
        }
    }

    /** UIDs of the worlds that are loaded right now. */
    private static List<String> loadedWorldUids() {
        List<String> uids = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            uids.add(world.getUID().toString());
        }
        return uids;
    }

    /** Deletes the energy rows of the given worlds (all rows if none listed). */
    private static void deleteRows(Connection con, List<String> worldUids) throws SQLException {
        if (worldUids.isEmpty()) return;
        try (PreparedStatement del = con.prepareStatement("DELETE FROM energy_storage WHERE world = ?")) {
            for (String worldUid : worldUids) {
                del.setString(1, worldUid);
                del.addBatch();
            }
            del.executeBatch();
        }
    }
}
