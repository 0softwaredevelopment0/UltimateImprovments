package com.mcplugin.energy.machines.workbench;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.*;
import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.data.type.Crafter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.HashSet;
import java.util.Set;

public class EnergyWorkbenchManager {

    // =========================
    // CACHE
    // =========================
    private static final Set<Location> workbenches = new HashSet<>();

    // =========================
    // INIT
    // =========================
    public static void init() {

        workbenches.clear();
        load();
    }

    // =========================
    // LOAD FROM DB
    // =========================
    private static void load() {

        workbenches.clear();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM workbenches"
             );
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                World world = Main.getInstance()
                        .getServer()
                        .getWorld(rs.getString("world"));

                if (world == null) continue;

                Location loc = new Location(
                        world,
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                );

                workbenches.add(LocationUtil.normalize(loc));
            }

            Main.getInstance().getLogger().info(
                    "[EnergyWorkbenchManager] Loaded " + workbenches.size() + " workbenches"
            );

        } catch (Exception e) {
            Main.getInstance().getLogger().severe(
                    "[EnergyWorkbenchManager] Load failed: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    // =========================
    // ADD
    // =========================
    public static void add(Location loc) {

        if (loc == null) return;

        loc = LocationUtil.normalize(loc);

        if (workbenches.contains(loc)) return;

        workbenches.add(loc);

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO workbenches (world, x, y, z) VALUES (?, ?, ?, ?)"
             )) {

            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());

            ps.executeUpdate();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe(
                    "[EnergyWorkbenchManager] Add failed: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    // =========================
    // REMOVE
    // =========================
    public static void remove(Location loc) {

        if (loc == null) return;

        loc = LocationUtil.normalize(loc);

        workbenches.remove(loc);

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM workbenches WHERE world = ? AND x = ? AND y = ? AND z = ?"
             )) {

            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());

            ps.executeUpdate();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe(
                    "[EnergyWorkbenchManager] Remove failed: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    // =========================
    // EXISTS
    // =========================
    public static boolean exists(Location loc) {

        if (loc == null) return false;

        return workbenches.contains(LocationUtil.normalize(loc));
    }

    // =========================
    // GET ALL
    // =========================
    public static Set<Location> getAll() {
        return workbenches;
    }

    // =========================
    // MAINTAIN LOCK — блокируем авто-крафт по редстоуну
    // Crafter.setTriggered(true) предотвращает ванильный авто-крафт при редстоун-сигнале.
    // Вызывать периодически, т.к. triggered сбрасывается блоком после импульса.
    // =========================
    public static void maintainLocks() {
        for (Location loc : workbenches) {
            try {
                if (loc.getBlock().getType() != Material.CRAFTER) continue;
                if (loc.getBlock().getBlockData() instanceof Crafter crafter) {
                    if (!crafter.isTriggered()) {
                        crafter.setTriggered(true);
                        loc.getBlock().setBlockData(crafter, false);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}