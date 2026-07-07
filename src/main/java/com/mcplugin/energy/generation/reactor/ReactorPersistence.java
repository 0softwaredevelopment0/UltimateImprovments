package com.mcplugin.energy.generation.reactor;

import com.mcplugin.core.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Отвечает за сохранение и загрузку реактора из SQLite.
 */
public class ReactorPersistence {

    /**
     * Сохраняет текущее состояние реактора в БД.
     */
    public static void saveToDb(ReactorState state) {
        Location loc = state.getReactorLocation();
        String id = state.getReactorId();
        if (loc == null || id == null) return;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO reactors
                (reactor_id, world, x, y, z,
                 core_temp, core_press, core_sh_int,
                 core_case_temp, core_case_press, core_case_int,
                 recipe_time, self_destruct,
                 reactor_wear, energy_generated)
                VALUES (?, ?, ?, ?, ?,
                        ?, ?, ?,
                        ?, ?, ?,
                        ?, ?,
                        ?, ?)
            """)) {

            ps.setString(1, id);
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.setInt(6, state.getCoreTemp());
            ps.setInt(7, state.getCorePress());
            ps.setInt(8, state.getCoreShInt());
            ps.setInt(9, state.getCoreCaseTemp());
            ps.setInt(10, state.getCoreCasePress());
            ps.setInt(11, state.getCoreCaseInt());
            ps.setInt(12, state.getRecipeTime());
            ps.setInt(13, state.isSelfDestruct() ? 1 : 0);
            ps.setInt(14, state.getReactorWear());
            ps.setLong(15, state.getEnergyGenerated());

            ps.executeUpdate();

            ConsoleLogger.info("[Reactor] Saved reactor " + id);
        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Save error: " + e.getMessage());
        }
    }

    /**
     * Загружает реактор из БД и заполняет состояние.
     */
    public static boolean loadFromDb(ReactorState state) {
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

                state.setReactorLocation(loc);
                state.setCoreTemp(rs.getInt("core_temp"));
                state.setCorePress(rs.getInt("core_press"));
                state.setCoreShInt(rs.getInt("core_sh_int"));
                state.setCoreCaseTemp(rs.getInt("core_case_temp"));
                state.setCoreCasePress(rs.getInt("core_case_press"));
                state.setCoreCaseInt(rs.getInt("core_case_int"));
                state.setRecipeTime(rs.getInt("recipe_time"));
                state.setSelfDestruct(rs.getInt("self_destruct") == 1);

                try { state.setReactorWear(rs.getInt("reactor_wear")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load reactor_wear: " + e.getMessage());
                }
                try { state.setEnergyGenerated(rs.getLong("energy_generated")); } catch (Exception e) {
                    ConsoleLogger.warn("[Reactor] Failed to load energy_generated: " + e.getMessage());
                }

                ConsoleLogger.info("[Reactor] Loaded reactor " + state.getReactorId());
                return true;
            }

        } catch (Exception e) {
            ConsoleLogger.error("[Reactor] Load error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Удаляет реактор из БД.
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
     * Сохраняет все активные реакторы (вызывается из ReactorManager.saveAll()).
     */
    public static void saveAll(ReactorState state) {
        if (state == null || !state.isValid() || state.getReactorLocation() == null) return;
        saveToDb(state);
    }
}
