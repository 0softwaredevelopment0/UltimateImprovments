package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Handles saving and loading the reactor from SQLite.
 */
public class ReactorPersistence {

    /**
     * Saves the current reactor state to the DB.
     */
    public static void saveToDb(ReactorState state) {
        Location loc = state.getReactorLocation();
        String id = state.getReactorId();
        if (loc == null || id == null) return;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO reactors
                (reactor_id, world, x, y, z,
                 core_temp, shield_press, spin,
                 core_case_temp, core_case_press, core_case_int,
                 energy_generated,
                 laser_started, laser_p1, laser_p2, laser_stab, laser_absorber,
                 fusion_particles, fusion_collected,
                 case_broken, case_temp, case_press, case_int,
                 structure_damaged)
                VALUES (?, ?, ?, ?, ?,
                        ?, ?, ?,
                        ?, ?, ?,
                        ?,
                        ?, ?, ?, ?, ?,
                        ?, ?,
                        ?, ?, ?, ?,
                        ?)
            """)) {

            ps.setString(1, id);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.setInt(6, state.getCoreTemp());
            ps.setDouble(7, state.getShieldPress());
            ps.setDouble(8, state.getSpin());
            ps.setInt(9, state.getCoreCaseTemp());
            ps.setInt(10, state.getCoreCasePress());
            ps.setInt(11, state.getCoreCaseInt());
            ps.setLong(12, state.getEnergyGenerated());
            ps.setInt(13, state.isLaserStarted() ? 1 : 0);
            double[] lp = state.getLaserPowers();
            ps.setDouble(14, lp.length > 0 ? lp[0] : 0);
            ps.setDouble(15, lp.length > 1 ? lp[1] : 0);
            ps.setDouble(16, lp.length > 2 ? lp[2] : 0);
            ps.setDouble(17, lp.length > 3 ? lp[3] : 0);
            ps.setDouble(18, state.getFusionParticles());
            ps.setDouble(19, state.getFusionCollected());
            ps.setInt(20, state.isCaseBroken() ? 1 : 0);
            ps.setInt(21, state.getCaseTemp());
            ps.setDouble(22, state.getCasePress());
            ps.setInt(23, state.getCaseIntegrity());
            ps.setInt(24, state.isStructureDamaged() ? 1 : 0);

            ps.executeUpdate();

            ConsoleLogger.info("[Reactor] Saved reactor " + id);
        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Save error: " + e.getMessage());
        }
    }

    /**
     * Loads ALL reactors from the DB (multi-reactor support).
     */
    public static java.util.List<ReactorState> loadAllFromDb() {
        java.util.List<ReactorState> out = new java.util.ArrayList<>();
        ReactorState state = new ReactorState();
        if (loadFromDb(state)) out.add(state);
        return out;
    }

    /**
     * Loads the reactor from the DB and fills the state.
     */
    public static boolean loadFromDb(ReactorState state) {
        java.util.List<ReactorState> all = loadAllRowsFromDb();
        if (all.isEmpty()) return false;
        ReactorState first = all.get(0);
        state.copyFrom(first);
        return true;
    }

    /**
     * Reads every valid reactor row from the DB into states (multi-reactor support).
     */
    public static java.util.List<ReactorState> loadAllRowsFromDb() {
        java.util.List<ReactorState> out = new java.util.ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM reactors");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                World world = Main.getInstance().getServer().getWorld(rs.getString("world"));
                if (world == null) continue;

                Location loc = LocationUtil.normalize(new Location(
                        world,
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                ));

                if (!ReactorStructure.isValid(loc, false)) {
                    ConsoleLogger.warn("[Reactor] Stored reactor at " + loc + " — structure invalid, skipping");
                    continue;
                }

                ReactorState state = new ReactorState();
                state.setReactorLocation(loc);
                try { state.setReactorId(rs.getString("reactor_id")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load reactor_id: " + e.getMessage());
                }
                state.setCoreTemp(rs.getInt("core_temp"));
                try { state.setShieldPress(rs.getDouble("shield_press")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load shield_press: " + e.getMessage());
                }
                try { state.setSpin(rs.getDouble("spin")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load spin: " + e.getMessage());
                }
                state.setCoreCaseTemp(rs.getInt("core_case_temp"));
                state.setCoreCasePress(rs.getInt("core_case_press"));
                state.setCoreCaseInt(rs.getInt("core_case_int"));
                try {
                    state.setFusionParticles(rs.getDouble("fusion_particles"));
                    state.setFusionCollected(rs.getDouble("fusion_collected"));
                } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load fusion state: " + e.getMessage());
                }
                try {
                    state.setCaseBroken(rs.getInt("case_broken") == 1);
                    state.setCaseTemp(rs.getInt("case_temp"));
                    state.setCasePress(rs.getDouble("case_press"));
                    state.setCaseIntegrity(rs.getInt("case_int"));
                } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load case state: " + e.getMessage());
                }

                try { state.setEnergyGenerated(rs.getLong("energy_generated")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load energy_generated: " + e.getMessage());
                }
                try { state.setLaserStarted(rs.getInt("laser_started") == 1); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load laser_started: " + e.getMessage());
                }
                try { state.setStructureDamaged(rs.getInt("structure_damaged") == 1); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load structure_damaged: " + e.getMessage());
                }
                try {
                    state.setLaserPowers(new double[] {
                            rs.getDouble("laser_p1"),
                            rs.getDouble("laser_p2"),
                            rs.getDouble("laser_stab"),
                            rs.getDouble("laser_absorber") });
                } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load laser powers: " + e.getMessage());
                }

                out.add(state);
                ConsoleLogger.info("[Reactor] Loaded reactor " + state.getReactorId());
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Load error: " + e.getMessage());
        }
        return out;
    }

    /**
     * Removes the reactor from the DB.
     */
    public static void deleteFromDb(String reactorId) {
        if (reactorId == null) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM reactors WHERE reactor_id = ?")) {
            ps.setString(1, reactorId);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Delete error: " + e.getMessage());
        }
    }

    /**
     * Saves all active reactors (called from ReactorManager.saveAll()).
     */
    public static void saveAll(ReactorState state) {
        if (state == null || !state.isValid() || state.getReactorLocation() == null) return;
        saveToDb(state);
    }
}
