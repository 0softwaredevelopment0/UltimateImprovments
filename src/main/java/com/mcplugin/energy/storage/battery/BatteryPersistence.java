package com.mcplugin.energy.storage.battery;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Сохранение/загрузка мультиблочных батарей в SQLite.
 */
public class BatteryPersistence {

    public static void saveAll() {
        for (BatteryManager.BatteryCluster cluster : BatteryManager.getAllClusters()) {
            saveCluster(cluster);
        }
    }

    public static void saveCluster(BatteryManager.BatteryCluster cluster) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO batteries
                (id, world, center_x, center_y, center_z, block_count)
                VALUES (?, ?, ?, ?, ?, ?)
            """);
            ps.setInt(1, cluster.id);
            ps.setString(2, cluster.world.getName());
            ps.setInt(3, cluster.center.getBlockX());
            ps.setInt(4, cluster.center.getBlockY());
            ps.setInt(5, cluster.center.getBlockZ());
            ps.setInt(6, cluster.blockKeys.size());
            ps.executeUpdate();
            ps.close();

            PreparedStatement del = con.prepareStatement("DELETE FROM battery_blocks WHERE battery_id = ?");
            del.setInt(1, cluster.id);
            del.executeUpdate();
            del.close();

            PreparedStatement ins = con.prepareStatement("""
                INSERT INTO battery_blocks (battery_id, x, y, z) VALUES (?, ?, ?, ?)
            """);
            for (long key : cluster.blockKeys) {
                ins.setInt(1, cluster.id);
                ins.setInt(2, BatteryManager.getX(key));
                ins.setInt(3, BatteryManager.getY(key));
                ins.setInt(4, BatteryManager.getZ(key));
                ins.addBatch();
            }
            ins.executeBatch();
            ins.close();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[BatteryMulti] DB save error: " + e.getMessage());
        }
    }

    public static void deleteCluster(int id) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement delB = con.prepareStatement("DELETE FROM battery_blocks WHERE battery_id = ?");
            delB.setInt(1, id);
            delB.executeUpdate();
            delB.close();
            PreparedStatement delM = con.prepareStatement("DELETE FROM batteries WHERE id = ?");
            delM.setInt(1, id);
            delM.executeUpdate();
            delM.close();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[BatteryMulti] DB delete error: " + e.getMessage());
        }
    }

    public static void loadAll() {
        BatteryManager.getLocationMap().clear();
        BatteryManager.getClustersById().clear();
        Connection con = DatabaseManager.getConnection();
        int maxId = 0;
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM batteries");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String wn = rs.getString("world");
                World world = Main.getInstance().getServer().getWorld(wn);
                if (world == null) continue;

                BatteryManager.BatteryCluster cluster = new BatteryManager.BatteryCluster();
                cluster.id = id;
                cluster.world = world;
                cluster.center = new Location(world, rs.getInt("center_x"), rs.getInt("center_y"), rs.getInt("center_z"));

                PreparedStatement psb = con.prepareStatement(
                    "SELECT x, y, z FROM battery_blocks WHERE battery_id = ?");
                psb.setInt(1, id);
                ResultSet rsb = psb.executeQuery();
                while (rsb.next()) {
                    int bx = rsb.getInt("x"), by = rsb.getInt("y"), bz = rsb.getInt("z");
                    long key = BatteryManager.toKey(bx, by, bz);
                    cluster.blockKeys.add(key);
                    BatteryManager.getLocationMap().put(key, cluster);
                }
                rsb.close();
                psb.close();

                cluster.recalculateCenter();
                BatteryManager.getClustersById().put(id, cluster);
                if (id > maxId) maxId = id;
            }
            rs.close();
            ps.close();

            BatteryManager.setNextId(maxId + 1);
            Main.getInstance().getLogger().info("[BatteryMulti] Loaded " + BatteryManager.getClusterCount() + " clusters from DB");
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[BatteryMulti] DB load error: " + e.getMessage());
        }
    }
}
