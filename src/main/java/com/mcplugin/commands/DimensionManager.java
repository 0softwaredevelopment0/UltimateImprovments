package com.mcplugin.commands;

import com.mcplugin.database.DatabaseManager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class DimensionManager {

    // =========================
    // SAVE RETURN LOCATION
    // =========================
    public static void saveReturnLocation(Player player) {
        Location loc = player.getLocation();
        UUID uuid = player.getUniqueId();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO dimension_returns
                (uuid, world, x, y, z, yaw, pitch, has_return)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
             """)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // HAS RETURN LOCATION
    // =========================
    public static boolean hasReturnLocation(Player player) {
        UUID uuid = player.getUniqueId();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                SELECT has_return FROM dimension_returns
                WHERE uuid = ?
             """)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("has_return") == 1;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // =========================
    // GET RETURN LOCATION
    // =========================
    public static Location getReturnLocation(Player player) {
        UUID uuid = player.getUniqueId();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                SELECT world, x, y, z, yaw, pitch FROM dimension_returns
                WHERE uuid = ? AND has_return = 1
             """)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world == null) return null;

                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");

                    return new Location(world, x, y, z, yaw, pitch);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // =========================
    // REMOVE RETURN LOCATION
    // =========================
    public static void removeReturnLocation(Player player) {
        UUID uuid = player.getUniqueId();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                DELETE FROM dimension_returns WHERE uuid = ?
             """)) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
