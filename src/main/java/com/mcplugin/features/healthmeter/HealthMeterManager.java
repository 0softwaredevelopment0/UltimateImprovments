package com.mcplugin.features.healthmeter;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class HealthMeterManager extends BukkitRunnable {

    private static HealthMeterManager instance;
    private static boolean enabled = true;
    private static int scanRadius = 2;

    public static void init(Main plugin) {
        instance = new HealthMeterManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.healthmeter.interval_ticks", 10);
        instance.runTaskTimer(plugin, 20L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.healthmeter");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        scanRadius = cfg.getInt("scan_radius", 2);
    }

    private boolean isHealthMeterItem(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;
        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() always returns non-null for non-AIR items.
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.HP_METER, PersistentDataType.BYTE);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            // Проверяем и основную, и дополнительную руку
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            boolean hasMeter = isHealthMeterItem(mainHand) || isHealthMeterItem(offHand);

            if (!hasMeter) continue;

            // Find nearest non-player entity in the same world
            LivingEntity target = null;
            double nearestDist = scanRadius + 1;
            for (org.bukkit.entity.Entity e : player.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
                if (e instanceof LivingEntity le && !(e instanceof Player)) {
                    if (!e.getWorld().equals(player.getWorld())) continue;
                    double dist = player.getLocation().distance(e.getLocation());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        target = le;
                    }
                }
            }

            if (target == null) {
                player.sendActionBar("§fСтатус сканнера: §7Не найдено");
            } else {
                double maxHealth = target.getMaxHealth();
                double currentHealth = target.getHealth();
                int hp = (int) Math.round(currentHealth);
                int maxHp = (int) Math.round(maxHealth);

                // Цветовая индикация по проценту здоровья
                String color;
                double pct = currentHealth / maxHealth;
                if (pct > 0.75) color = "§a";
                else if (pct > 0.5) color = "§e";
                else if (pct > 0.25) color = "§6";
                else color = "§c";

                // Имя сущности (тип)
                String entityName = target.getType().name();
                if (target.getCustomName() != null) {
                    entityName = target.getCustomName();
                }

                player.sendActionBar("§f§l" + entityName + " §r" + color + hp + "§f/" + maxHp + " §fОЗ");
            }
        }
    }
}
