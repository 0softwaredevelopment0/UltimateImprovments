package com.ultimateimprovments.mechanics.particle;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite persistence for {@link ParticleAcceleratorManager#engineEnergy}.
 * <p>
 * Без этой таблицы буферы энергии двигателей жили только в памяти и при
 * рестарте сервера всегда сбрасывались в 0 (через {@code putIfAbsent(pos, 0)} в
 * {@link ParticleAcceleratorManager#scanExistingAccelerators()}). Это приводило
 * к долгим re-charge после каждого /reload, краша или планового рестарта.
 * <p>
 * Ключ — пара (world_uid, block_key), совпадает со структурой {@code EnginePos}.
 */
public class ParticleEnergyDatabase {

    private static volatile boolean ready = false;

    public static boolean isReady() { return ready; }

    public static synchronized void initTables() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS particle_engine_energy (
                    world_uid   TEXT    NOT NULL,
                    block_key   INTEGER NOT NULL,
                    energy      INTEGER NOT NULL,
                    PRIMARY KEY (world_uid, block_key)
                );
            """);
            ready = true;
        } catch (Exception e) {
            ConsoleLogger.error("[ParticleEnergy] init failed: " + e.getMessage());
            ready = false;
        }
    }

    /**
     * Загружает ВСЕ записи энергии. Возвращает Map<UUID worldUid, Map<blockKey, energy>>.
     */
    public static synchronized Map<UUID, Map<Long, Integer>> loadAll() {
        Map<UUID, Map<Long, Integer>> result = new HashMap<>();
        if (!ready) initTables();
        if (!ready) return result;
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT world_uid, block_key, energy FROM particle_engine_energy")) {
            while (rs.next()) {
                try {
                    UUID worldUid = UUID.fromString(rs.getString("world_uid"));
                    long key = rs.getLong("block_key");
                    int energy = rs.getInt("energy");
                    result.computeIfAbsent(worldUid, k -> new HashMap<>()).put(key, energy);
                } catch (Exception ignored) {
                    // skip malformed row
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[ParticleEnergy] load failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Полная перезапись таблицы. Используется редко, проще делать upsert'ами.
     * Вызывается из shutdown() и из async flush-таска.
     */
    public static synchronized void saveAll(Map<UUID, Map<Long, Integer>> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        if (!ready) initTables();
        if (!ready) return;
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT OR REPLACE INTO particle_engine_energy (world_uid, block_key, energy) VALUES (?, ?, ?)")) {
                for (Map.Entry<UUID, Map<Long, Integer>> w : snapshot.entrySet()) {
                    String worldUid = w.getKey().toString();
                    for (Map.Entry<Long, Integer> e : w.getValue().entrySet()) {
                        ps.setString(1, worldUid);
                        ps.setLong(2, e.getKey());
                        ps.setInt(3, e.getValue());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
        } catch (Exception e) {
            ConsoleLogger.error("[ParticleEnergy] save failed: " + e.getMessage());
        }
    }

    /**
     * Upsert одной записи. Делается синхронно (некоторые методы, типа
     * {@code consumeEngineEnergy}, вызывают это часто).
     */
    public static synchronized void upsertOne(UUID worldUid, long blockKey, int energy) {
        if (!ready) initTables();
        if (!ready) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO particle_engine_energy (world_uid, block_key, energy) VALUES (?, ?, ?)")) {
            ps.setString(1, worldUid.toString());
            ps.setLong(2, blockKey);
            ps.setInt(3, energy);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[ParticleEnergy] upsert failed: " + e.getMessage());
        }
    }

    public static synchronized void deleteOne(UUID worldUid, long blockKey) {
        if (!ready) initTables();
        if (!ready) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM particle_engine_energy WHERE world_uid = ? AND block_key = ?")) {
            ps.setString(1, worldUid.toString());
            ps.setLong(2, blockKey);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[ParticleEnergy] delete failed: " + e.getMessage());
        }
    }
}
