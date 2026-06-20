package com.mcplugin.features.magnet;

import com.mcplugin.Main;
import com.mcplugin.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;

/**
 * Сохранение и загрузка кластеров магнита из SQLite.
 * <p>
 * Извлечено из {@link MagnetManager} для уменьшения размера класса.
 */
public final class MagnetDatabase {

    private MagnetDatabase() {}

    // =========================
    // SAVE ALL
    // =========================
    public static void saveAll() {
        for (var cluster : MagnetManager.getClusters()) {
            saveCluster(cluster);
        }
    }

    private static void saveCluster(MagnetManager.MagnetCluster cluster) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("""
                INSERT OR REPLACE INTO magnets
                (id, world, center_x, center_y, center_z, block_count, active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
            """);
            ps.setInt(1, cluster.id);
            ps.setString(2, cluster.world.getName());
            ps.setInt(3, cluster.center.getBlockX());
            ps.setInt(4, cluster.center.getBlockY());
            ps.setInt(5, cluster.center.getBlockZ());
            ps.setInt(6, cluster.blockKeys.size());
            ps.executeUpdate();
            ps.close();

            PreparedStatement del = con.prepareStatement(
                "DELETE FROM magnet_blocks WHERE magnet_id = ?"
            );
            del.setInt(1, cluster.id);
            del.executeUpdate();
            del.close();

            PreparedStatement ins = con.prepareStatement("""
                INSERT INTO magnet_blocks (magnet_id, x, y, z)
                VALUES (?, ?, ?, ?)
            """);
            for (long key : cluster.blockKeys) {
                ins.setInt(1, cluster.id);
                ins.setInt(2, MagnetManager.getX(key));
                ins.setInt(3, MagnetManager.getY(key));
                ins.setInt(4, MagnetManager.getZ(key));
                ins.addBatch();
            }
            ins.executeBatch();
            ins.close();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Magnet] DB save error: " + e.getMessage());
        }
    }

    // =========================
    // DELETE
    // =========================
    public static void deleteCluster(int id) {
        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement delBlocks = con.prepareStatement(
                "DELETE FROM magnet_blocks WHERE magnet_id = ?"
            );
            delBlocks.setInt(1, id);
            delBlocks.executeUpdate();
            delBlocks.close();

            PreparedStatement delMagnet = con.prepareStatement(
                "DELETE FROM magnets WHERE id = ?"
            );
            delMagnet.setInt(1, id);
            delMagnet.executeUpdate();
            delMagnet.close();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Magnet] DB delete error: " + e.getMessage());
        }
    }

    // =========================
    // LOAD ALL
    // =========================
    public static void loadAll() {
        var locationToCluster = MagnetManager.getLocationMapInternal();
        var clustersById = MagnetManager.getClustersByIdInternal();

        locationToCluster.clear();
        clustersById.clear();

        Connection con = DatabaseManager.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                "SELECT * FROM magnets WHERE active = 1"
            );
            ResultSet rs = ps.executeQuery();
            int maxId = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String worldName = rs.getString("world");
                World world = Main.getInstance().getServer().getWorld(worldName);
                if (world == null) continue;

                var cluster = new MagnetManager.MagnetCluster();
                cluster.id = id;
                cluster.world = world;
                cluster.center = new Location(
                        world,
                        rs.getInt("center_x"),
                        rs.getInt("center_y"),
                        rs.getInt("center_z")
                );

                PreparedStatement psb = con.prepareStatement(
                    "SELECT x, y, z FROM magnet_blocks WHERE magnet_id = ?"
                );
                psb.setInt(1, id);
                ResultSet rsb = psb.executeQuery();
                while (rsb.next()) {
                    int bx = rsb.getInt("x"), by = rsb.getInt("y"), bz = rsb.getInt("z");
                    long key = MagnetManager.toKey(bx, by, bz);
                    cluster.blockKeys.add(key);
                    locationToCluster.put(key, cluster);
                }
                rsb.close();
                psb.close();

                cluster.blockKeys = new HashSet<>(cluster.blockKeys);
                cluster.recalculateCenter();
                clustersById.put(id, cluster);
                if (id > maxId) maxId = id;
            }
            rs.close();
            ps.close();

            MagnetManager.setNextId(maxId + 1);

            Main.getInstance().getLogger().info(
                    "[Magnet] Loaded " + clustersById.size()
                            + " magnet clusters (" + locationToCluster.size() + " blocks) from DB"
            );
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[Magnet] DB load error: " + e.getMessage());
        }
    }
}
