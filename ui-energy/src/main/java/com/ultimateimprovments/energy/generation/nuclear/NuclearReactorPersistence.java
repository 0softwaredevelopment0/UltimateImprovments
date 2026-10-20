package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * SQLite persistence of the compact nuclear reactor state.
 * <p>
 * Every reactor is identified by its anchor (world + block coordinates), so a
 * world can host any number of them without collisions: the table primary key
 * is {@code (world, x, y, z)}. Fuel ticks, temperature, the biome limits, the
 * cooling buffer, the drain accumulator and the meltdown countdown survive a
 * restart; the table itself is created by {@code DatabaseInit}.
 */
public final class NuclearReactorPersistence {

    /** One persisted row, keyed by the reactor anchor. */
    private record StateRow(int temperature, int tempMin, double coolMult, int cooling,
                            int drainSeconds, double remainder, int meltdownSeconds,
                            int fuelTicks, boolean damaged) {}

    private NuclearReactorPersistence() {}

    // =========================
    // WRITE
    // =========================

    /** Inserts or updates one reactor row. Safe to call every few seconds. */
    public static void save(NuclearReactor reactor) {
        if (reactor == null) return;
        Location anchor = reactor.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO nuclear_reactors
                (world, x, y, z,
                 temperature, temp_min, natural_cool_mult, cooling,
                 cool_drain_seconds, cool_remainder, meltdown_seconds,
                 fuel_ticks, damaged)
                VALUES (?, ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?, ?,
                        ?, ?)
             """)) {

            ps.setString(1, anchor.getWorld().getName());
            ps.setInt(2, anchor.getBlockX());
            ps.setInt(3, anchor.getBlockY());
            ps.setInt(4, anchor.getBlockZ());
            ps.setInt(5, reactor.getTemperature());
            ps.setInt(6, reactor.getTempMin());
            ps.setDouble(7, reactor.getNaturalCoolMult());
            ps.setInt(8, reactor.getCooling());
            ps.setInt(9, reactor.getCoolDrainSeconds());
            ps.setDouble(10, reactor.getCoolRemainder());
            ps.setInt(11, reactor.getMeltdownSeconds());
            ps.setInt(12, reactor.getFuelTicksLeft());
            ps.setInt(13, reactor.isDamaged() ? 1 : 0);
            ps.executeUpdate();

        } catch (Exception e) {
            ConsoleLogger.warn("[NuclearReactor] Failed to save state at "
                    + anchor.getBlockX() + " " + anchor.getBlockY() + " " + anchor.getBlockZ()
                    + ": " + e.getMessage());
        }
    }

    /** Saves every live reactor (periodic flush and shutdown). */
    public static void saveAll() {
        for (NuclearReactor reactor : NuclearReactorManager.getReactors()) {
            save(reactor);
        }
    }

    /** Removes the row of a disassembled / detonated reactor. */
    public static void delete(Location anchor) {
        if (anchor == null || anchor.getWorld() == null) return;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                DELETE FROM nuclear_reactors
                WHERE world = ? AND x = ? AND y = ? AND z = ?
             """)) {

            ps.setString(1, anchor.getWorld().getName());
            ps.setInt(2, anchor.getBlockX());
            ps.setInt(3, anchor.getBlockY());
            ps.setInt(4, anchor.getBlockZ());
            ps.executeUpdate();

        } catch (Exception e) {
            ConsoleLogger.warn("[NuclearReactor] Failed to delete state at "
                    + anchor.getBlockX() + " " + anchor.getBlockY() + " " + anchor.getBlockZ()
                    + ": " + e.getMessage());
        }
    }

    // =========================
    // LOAD
    // =========================

    /**
     * Applies the persisted state to every reactor restored from the structure
     * markers. Called from {@code NuclearReactorManager.init()} after the
     * registry is rebuilt.
     */
    public static void loadAll() {
        Map<String, StateRow> rows = new HashMap<>();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                SELECT world, x, y, z, temperature, temp_min, natural_cool_mult,
                       cooling, cool_drain_seconds, cool_remainder, meltdown_seconds,
                       fuel_ticks, damaged
                FROM nuclear_reactors
             """);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                rows.put(key(rs.getString("world"),
                                rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                        new StateRow(
                                rs.getInt("temperature"),
                                rs.getInt("temp_min"),
                                rs.getDouble("natural_cool_mult"),
                                rs.getInt("cooling"),
                                rs.getInt("cool_drain_seconds"),
                                rs.getDouble("cool_remainder"),
                                rs.getInt("meltdown_seconds"),
                                rs.getInt("fuel_ticks"),
                                rs.getInt("damaged") != 0));
            }

        } catch (Exception e) {
            ConsoleLogger.warn("[NuclearReactor] Failed to load state: " + e.getMessage());
            return;
        }

        for (NuclearReactor reactor : NuclearReactorManager.getReactors()) {
            Location anchor = reactor.getAnchor();
            if (anchor == null) continue;

            StateRow row = rows.get(key(anchor));
            // A legacy reactor (assembled before the thermal model) has no
            // row — its biome limits are rolled on the next loaded tick.
            if (row == null) continue;

            reactor.setTemperature(row.temperature());
            reactor.setTempMin(row.tempMin());
            reactor.setNaturalCoolMult(row.coolMult());
            reactor.setCooling(row.cooling());
            reactor.setCoolDrainSeconds(row.drainSeconds());
            reactor.setCoolRemainder(row.remainder());
            reactor.setMeltdownSeconds(row.meltdownSeconds());
            reactor.setFuelTicksLeft(row.fuelTicks());
            reactor.setDamaged(row.damaged());
            reactor.setThermalReady(true);
        }
    }

    // =========================
    // HELPERS
    // =========================

    private static String key(Location anchor) {
        return key(anchor.getWorld().getName(),
                anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
    }

    private static String key(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }
}
