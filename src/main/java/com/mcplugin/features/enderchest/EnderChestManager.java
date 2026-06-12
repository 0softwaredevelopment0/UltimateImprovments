package com.mcplugin.features.enderchest;

import com.mcplugin.Main;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class EnderChestManager implements Listener {

    private static EnderChestManager instance;
    private static boolean enabled = true;
    private static int damage = 8192;
    private static int explosionRadius = 5;

    public static void init(Main plugin) {
        instance = new EnderChestManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.enderchest");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        damage = cfg.getInt("damage", 8192);
        explosionRadius = cfg.getInt("explosion_radius", 5);
    }

    @EventHandler
    public void onEnderChestBreak(BlockBreakEvent e) {
        if (!enabled) return;
        if (e.getBlock().getType() != Material.ENDER_CHEST) return;

        org.bukkit.Location loc = e.getBlock().getLocation();

        e.getPlayer().damage(damage);

        loc.getWorld().spawn(loc, Creeper.class, c -> {
            c.setPowered(true);
            c.setExplosionRadius(explosionRadius);
            c.setMaxFuseTicks(0);
            c.setIgnited(true);
        });
    }
}
