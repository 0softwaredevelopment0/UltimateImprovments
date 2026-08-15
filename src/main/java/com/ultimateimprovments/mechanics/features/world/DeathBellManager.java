package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BellRingEvent;

public class DeathBellManager implements Listener {

    private static DeathBellManager instance;
    private static boolean enabled = true;
    private static boolean lightning = true;

    public static void init(Main plugin) {
        instance = new DeathBellManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.deathbell");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        lightning = cfg.getBoolean("lightning", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBellRing(BellRingEvent e) {
        if (!enabled || !lightning) return;

        org.bukkit.entity.Entity entity = e.getEntity();
        if (entity instanceof org.bukkit.entity.Player player) {
            // Lightning at the player's location who rang the bell
            player.getWorld().strikeLightning(player.getLocation());
        } else if (entity != null) {
            // If the bell was rung not by a player — lightning on the entity
            entity.getWorld().strikeLightning(entity.getLocation());
        } else {
            // Fallback: on the bell
            e.getBlock().getWorld().strikeLightning(e.getBlock().getLocation());
        }
    }
}
