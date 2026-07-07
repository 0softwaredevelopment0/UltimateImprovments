package com.mcplugin.mechanics.features.world;

import com.mcplugin.core.Main;
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
            // Молния на месте игрока, который позвонил в колокол
            player.getWorld().strikeLightning(player.getLocation());
        } else if (entity != null) {
            // Если колокол позвонило не игроком — молния на сущности
            entity.getWorld().strikeLightning(entity.getLocation());
        } else {
            // Fallback: на колокол
            e.getBlock().getWorld().strikeLightning(e.getBlock().getLocation());
        }
    }
}
