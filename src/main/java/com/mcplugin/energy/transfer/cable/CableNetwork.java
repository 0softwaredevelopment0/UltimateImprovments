package com.mcplugin.energy.transfer.cable;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.database.DatabaseManager;

import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CableNetwork {

    // =========================
    // MEMORY CACHE
    // =========================
    private static final Map<Location, CableNode> nodes = new ConcurrentHashMap<>();

    // =========================
    // FLOWING TRACKING — cables that had energy pass through them this tick
    // =========================
    private static final Set<Location> flowingCables = ConcurrentHashMap.newKeySet();

    // =========================
    // INIT
    // =========================
    public static void init() {
        nodes.clear();
        load();
    }

    // =========================
    // ADD NODE (только RAM — БД обновляется каждые 5 мин автосохранением)
    // =========================
    public static void addNode(Location loc) {

        loc = LocationUtil.normalize(loc);

        if (nodes.containsKey(loc)) return;

        CableNode node = new CableNode(loc);
        nodes.put(loc, node);
    }

    // =========================
    // REMOVE NODE (только RAM)
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
    // FLOWING TRACKING
    // =========================
    public static void markFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc != null) flowingCables.add(loc);
    }

    public static boolean isFlowing(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && flowingCables.contains(loc);
    }

    public static void clearFlowing() {
        flowingCables.clear();
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
    // SAVE NODE (synchronized на уровне класса для thread safety)
    // Используем try-with-resources для PreparedStatement, не закрываем Connection.
    // =========================
    public static synchronized void saveNode(CableNode node) {

        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        Location loc = node.getLocation();

        try {
            // 1. Сохраняем/обновляем запись узла
            try (PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO cables
                (world, x, y, z, energy, type)
                VALUES (?, ?, ?, ?, ?, ?)
            """)) {

                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setInt(5, node.getEnergy());
                ps.setString(6, node.getType().name());

                ps.executeUpdate();
            }

            // 2. Удаляем старые соединения для этого узла
            try (PreparedStatement del = con.prepareStatement("""
                DELETE FROM cable_connections
                WHERE world = ? AND x = ? AND y = ? AND z = ?
            """)) {

                del.setString(1, loc.getWorld().getName());
                del.setInt(2, loc.getBlockX());
                del.setInt(3, loc.getBlockY());
                del.setInt(4, loc.getBlockZ());

                del.executeUpdate();
            }

            // 3. Вставляем текущие соединения
            try (PreparedStatement ins = con.prepareStatement("""
                INSERT INTO cable_connections
                (world, x, y, z, to_world, to_x, to_y, to_z)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {

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
            }

        } catch (SQLException e) {
            Main.getInstance().getLogger().severe("Cable save error: " + e.getMessage());
        }
    }

    // =========================
    // DELETE NODE
    // Connection — singleton, НЕ закрываем его (try-with-resources только для Statement).
    // =========================
    private static void deleteNode(Location loc) {

        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try (PreparedStatement ps = con.prepareStatement("""
                DELETE FROM cables
                WHERE world=? AND x=? AND y=? AND z=?
            """)) {

            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());

            ps.executeUpdate();

        } catch (SQLException e) {
            Main.getInstance().getLogger().severe("Cable delete error: " + e.getMessage());
        }
    }

    // =========================
    // LOAD
    // Connection — singleton, НЕ закрываем его (try-with-resources только для Statement).
    // =========================
    private static void load() {

        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try {
            // Загружаем узлы
            try (PreparedStatement ps = con.prepareStatement("""
                SELECT * FROM cables
            """); ResultSet rs = ps.executeQuery()) {

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
            }

            // Загружаем соединения
            try (PreparedStatement ps2 = con.prepareStatement("""
                SELECT * FROM cable_connections
            """); ResultSet rs2 = ps2.executeQuery()) {

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
            }

            Main.getInstance().getLogger().info(
                    "[CableNetwork] Loaded " + nodes.size() + " nodes from SQLite"
            );

        } catch (SQLException e) {
            Main.getInstance().getLogger().severe("Cable load error: " + e.getMessage());
        }
    }
}