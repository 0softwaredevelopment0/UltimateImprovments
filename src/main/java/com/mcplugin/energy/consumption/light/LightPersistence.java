package com.mcplugin.energy.consumption.light;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Сохранение/загрузка мультиблочных лампочек в SQLite.
 */
public class LightPersistence {

    public static void saveAll() {
        for (LightManager.LightCluster cluster : LightManager.getAllClusters()) {
            saveCluster(cluster);
        }
    }

    public static void saveCluster(LightManager.LightCluster cluster) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO lights
                (id, world, center_x, center_y, center_z, block_count, lit)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """);
            ps.setInt(1, cluster.id);
            ps.setString(2, cluster.world.getName());
            ps.setInt(3, cluster.center.getBlockX());
            ps.setInt(4, cluster.center.getBlockY());
            ps.setInt(5, cluster.center.getBlockZ());
            ps.setInt(6, cluster.blockKeys.size());
            ps.setBoolean(7, cluster.lit);
            ps.executeUpdate();
            ps.close();

            PreparedStatement del = con.prepareStatement("DELETE FROM light_blocks WHERE light_id = ?");
            del.setInt(1, cluster.id);
            del.executeUpdate();
            del.close();

            PreparedStatement ins = con.prepareStatement("""
                INSERT INTO light_blocks (light_id, x, y, z) VALUES (?, ?, ?, ?)
            """);
            for (long key : cluster.blockKeys) {
                ins.setInt(1, cluster.id);
                ins.setInt(2, LightManager.getX(key));
                ins.setInt(3, LightManager.getY(key));
                ins.setInt(4, LightManager.getZ(key));
                ins.addBatch();
            }
            ins.executeBatch();
            ins.close();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[LightMulti] DB save error: " + e.getMessage());
        }
    }

    public static void deleteCluster(int id) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement delB = con.prepareStatement("DELETE FROM light_blocks WHERE light_id = ?");
            delB.setInt(1, id);
            delB.executeUpdate();
            delB.close();
            PreparedStatement delM = con.prepareStatement("DELETE FROM lights WHERE id = ?");
            delM.setInt(1, id);
            delM.executeUpdate();
            delM.close();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[LightMulti] DB delete error: " + e.getMessage());
        }
    }

    public static void loadAll() {
        LightManager.getLocationMap().clear();
        LightManager.getClustersById().clear();
        Connection con = DatabaseManager.getConnection();
        int maxId = 0;
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM lights");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String wn = rs.getString("world");
                World world = Main.getInstance().getServer().getWorld(wn);
                if (world == null) continue;

                LightManager.LightCluster cluster = new LightManager.LightCluster();
                cluster.id = id;
                cluster.world = world;
                cluster.lit = rs.getBoolean("lit");
                cluster.center = new Location(world, rs.getInt("center_x"), rs.getInt("center_y"), rs.getInt("center_z"));

                PreparedStatement psb = con.prepareStatement(
                    "SELECT x, y, z FROM light_blocks WHERE light_id = ?");
                psb.setInt(1, id);
                ResultSet rsb = psb.executeQuery();
                while (rsb.next()) {
                    int bx = rsb.getInt("x"), by = rsb.getInt("y"), bz = rsb.getInt("z");
                    long key = LightManager.toKey(bx, by, bz);
                    cluster.blockKeys.add(key);
                    LightManager.getLocationMap().put(key, cluster);
                }
                rsb.close();
                psb.close();

                cluster.recalculateCenter();
                LightManager.getClustersById().put(id, cluster);
                if (id > maxId) maxId = id;
            }
            rs.close();
            ps.close();

            LightManager.setNextId(maxId + 1);
            Main.getInstance().getLogger().info("[LightMulti] Loaded " + LightManager.getClusterCount() + " clusters from DB");
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[LightMulti] DB load error: " + e.getMessage());
        }
    }
}
