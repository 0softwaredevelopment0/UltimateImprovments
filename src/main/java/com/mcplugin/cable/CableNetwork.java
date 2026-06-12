package com.mcplugin.cable;

import com.mcplugin.Main;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.database.DatabaseManager;

import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.*;

public class CableNetwork {

    // =========================
    // MEMORY CACHE
    // =========================
    private static final Map<Location, CableNode> nodes = new HashMap<>();

    // =========================
    // INIT
    // =========================
    public static void init() {
        nodes.clear();
        load();
    }

    // =========================
    // ADD NODE
    // =========================
    public static void addNode(Location loc) {

        loc = LocationUtil.normalize(loc);

        if (nodes.containsKey(loc)) return;

        CableNode node = new CableNode(loc);
        nodes.put(loc, node);

        saveNode(node);
    }

    // =========================
    // REMOVE NODE
    // =========================
    public static void removeNode(Location loc) {

        loc = LocationUtil.normalize(loc);

        CableNode node = nodes.remove(loc);

        if (node != null) {

            for (Location connected : node.getConnections()) {

                CableNode other = nodes.get(connected);

                if (other != null) {
                    other.disconnect(loc);
                }
            }
        }

        deleteNode(loc);
    }

    // =========================
    // GET
    // =========================
    public static CableNode getNode(Location loc) {
        return nodes.get(LocationUtil.normalize(loc));
    }

    public static boolean exists(Location loc) {
        return nodes.containsKey(LocationUtil.normalize(loc));
    }

    public static Collection<CableNode> getAllNodes() {
        return nodes.values();
    }

    // =========================
    // SAVE ALL
    // =========================
    public static void save() {
        for (CableNode node : nodes.values()) {
            saveNode(node);
        }
    }

    // =========================
    // SAVE NODE
    // =========================
    public static void saveNode(CableNode node) {

        Connection con = DatabaseManager.getConnection();

        try {

            Location loc = node.getLocation();

            PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO cables
                (world, x, y, z, energy, type)
                VALUES (?, ?, ?, ?, ?, ?)
            """);

            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            ps.setInt(5, node.getEnergy());
            ps.setString(6, node.getType().name());

            ps.executeUpdate();
            ps.close();

            PreparedStatement del = con.prepareStatement("""
                DELETE FROM cable_connections
                WHERE world = ? AND x = ? AND y = ? AND z = ?
            """);

            del.setString(1, loc.getWorld().getName());
            del.setInt(2, loc.getBlockX());
            del.setInt(3, loc.getBlockY());
            del.setInt(4, loc.getBlockZ());

            del.executeUpdate();
            del.close();

            PreparedStatement ins = con.prepareStatement("""
                INSERT INTO cable_connections
                (world, x, y, z, to_world, to_x, to_y, to_z)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """);

            for (Location conn : node.getConnections()) {

                ins.setString(1, loc.getWorld().getName());
                ins.setInt(2, loc.getBlockX());
                ins.setInt(3, loc.getBlockY());
                ins.setInt(4, loc.getBlockZ());

                ins.setString(5, conn.getWorld().getName());
                ins.setInt(6, conn.getBlockX());
                ins.setInt(7, conn.getBlockY());
                ins.setInt(8, conn.getBlockZ());

                ins.addBatch();
            }

            ins.executeBatch();
            ins.close();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("Cable save error: " + e.getMessage());
        }
    }

    // =========================
    // DELETE NODE
    // =========================
    private static void deleteNode(Location loc) {

        Connection con = DatabaseManager.getConnection();

        try {

            PreparedStatement ps = con.prepareStatement("""
                DELETE FROM cables
                WHERE world=? AND x=? AND y=? AND z=?
            """);

            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());

            ps.executeUpdate();
            ps.close();

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("Cable delete error: " + e.getMessage());
        }
    }

    // =========================
    // LOAD
    // =========================
    private static void load() {

        Connection con = DatabaseManager.getConnection();

        try {

            PreparedStatement ps = con.prepareStatement("""
                SELECT * FROM cables
            """);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                World world = Main.getInstance()
                        .getServer()
                        .getWorld(rs.getString("world"));

                if (world == null) continue;

                Location loc = LocationUtil.normalize(new Location(
                        world,
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                ));

                CableNode node = new CableNode(loc);
                node.setEnergy(rs.getInt("energy"));

                String typeStr = rs.getString("type");
                if (typeStr != null) {
                    try {
                        node.setType(NodeType.valueOf(typeStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                nodes.put(loc, node);
            }

            rs.close();
            ps.close();

            PreparedStatement ps2 = con.prepareStatement("""
                SELECT * FROM cable_connections
            """);

            ResultSet rs2 = ps2.executeQuery();

            while (rs2.next()) {

                World w1 = Main.getInstance().getServer().getWorld(rs2.getString("world"));
                World w2 = Main.getInstance().getServer().getWorld(rs2.getString("to_world"));

                if (w1 == null || w2 == null) continue;

                Location from = LocationUtil.normalize(new Location(
                        w1,
                        rs2.getInt("x"),
                        rs2.getInt("y"),
                        rs2.getInt("z")
                ));

                Location to = LocationUtil.normalize(new Location(
                        w2,
                        rs2.getInt("to_x"),
                        rs2.getInt("to_y"),
                        rs2.getInt("to_z")
                ));

                CableNode a = nodes.get(from);
                if (a == null) continue;

                a.connect(to);
            }

            rs2.close();
            ps2.close();

            Main.getInstance().getLogger().info(
                    "[CableNetwork] Loaded " + nodes.size() + " nodes from SQLite"
            );

        } catch (Exception e) {
            Main.getInstance().getLogger().severe("Cable load error: " + e.getMessage());
        }
    }
}