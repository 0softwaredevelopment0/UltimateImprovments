package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class EntityLocatorManager extends BukkitRunnable {

    private static EntityLocatorManager instance;
    private static boolean enabled = true;
    private static int scanRadius = 12;

    public static void init(Main plugin) {
        instance = new EntityLocatorManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.entitylocator.interval_ticks", 20);
        instance.runTaskTimer(plugin, 20L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.entitylocator");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        scanRadius = cfg.getInt("scan_radius", 12);
    }

    private boolean isLocatorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() always returns non-null for non-AIR items.
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.LOCATOR, PersistentDataType.BYTE);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            // Проверяем и основную, и дополнительную руку
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            boolean hasLocator = isLocatorItem(mainHand) || isLocatorItem(offHand);

            if (!hasLocator) continue;

            // Find nearest non-player, non-item entity in the same world
            Entity nearest = null;
            double nearestDist = scanRadius + 1;
            for (Entity e : player.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
                if (e instanceof Player) continue;
                if (e instanceof org.bukkit.entity.Item) continue;
                if (!e.getWorld().equals(player.getWorld())) continue;
                double dist = player.getLocation().distance(e.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = e;
                }
            }

            double q1 = scanRadius * 0.25;
            double q2 = scanRadius * 0.5;
            double q3 = scanRadius * 0.75;

            if (nearest == null) {
                player.sendMessage("§fСтатус обнаружения: §7Не найдено");
            } else if (nearestDist <= q1) {
                player.sendMessage("§fСтатус обнаружения: §aОчень близко");
            } else if (nearestDist <= q2) {
                player.sendMessage("§fСтатус обнаружения: §eБлизко");
            } else if (nearestDist <= q3) {
                player.sendMessage("§fСтатус обнаружения: §6Средне-далеко");
            } else {
                player.sendMessage("§fСтатус обнаружения: §cДалеко");
            }
        }
    }
}
