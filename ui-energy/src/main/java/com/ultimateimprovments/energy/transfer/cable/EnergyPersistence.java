package com.ultimateimprovments.energy.transfer.cable;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.energy.storage.battery.BatteryPool;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQLite persistence for stored node energy (batteries).
 * <p>
 * Node energy lives in RAM ({@link CableNode}); positions live in
 * {@code structure_markers}. This table restores the energy values after a
 * restart or a soft {@code /ui reload}: a full rewrite (DELETE + INSERT in
 * one transaction) runs every save interval and on shutdown, and
 * {@link #load()} puts the values back into the freshly rebuilt nodes.
 * Rows for nodes that no longer exist (disassembled batteries) are dropped
 * by the next rewrite, so the table stays in sync with the world.
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
     * Restores energy into the battery nodes of the (already rebuilt) cable
     * network. Called once after the managers init — nodes created later
     * (new batteries) simply start empty.
     */
    public static void load() {
        int restored = 0;
        // One multiblock battery = one pool. Old rows were written per block
        // (each block kept its own charge), so the values are SUMMED per pool
        // and clamped to the cluster capacity — that migrates an existing
        // world without losing a single volt. New saves write one row per
        // pool, which the same sum handles unchanged.
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
                    pooledEnergy.merge(pool, energy,
                            (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
                    restored++;
                    continue;
                }

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

        if (rows.isEmpty()) {
            // Nothing to store — still clear the table so stale rows from
            // drained/disassembled batteries don't resurrect after a restart.
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement del = con.prepareStatement("DELETE FROM energy_storage")) {
                del.executeUpdate();
            } catch (SQLException e) {
                ConsoleLogger.warn("[EnergyPersistence] Failed to clear energy: " + e.getMessage());
            }
            return;
        }

        try (Connection con = DatabaseManager.getConnection()) {
            boolean oldAutoCommit = con.getAutoCommit();
            try {
                con.setAutoCommit(false);
                try (PreparedStatement del = con.prepareStatement("DELETE FROM energy_storage")) {
                    del.executeUpdate();
                }
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
}
