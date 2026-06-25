package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BoostedCobwebManager extends BukkitRunnable {

    private static BoostedCobwebManager instance;
    private static boolean enabled = true;

    public static void init(Main plugin) {
        instance = new BoostedCobwebManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.boostedcobweb.interval_ticks", 5);
        instance.runTaskTimer(plugin, 20L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.boostedcobweb");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getBlock().getType() == Material.COBWEB) {
                // Полная остановка движения: XZ = 0, Y только падение вниз (как в паутине)
                player.setVelocity(player.getVelocity().setX(0).setZ(0).setY(Math.min(player.getVelocity().getY(), 0)));
            }
        }
    }
}
