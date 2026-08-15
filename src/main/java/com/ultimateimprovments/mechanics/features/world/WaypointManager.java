package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class WaypointManager extends BukkitRunnable {

    private static WaypointManager instance;
    private static boolean enabled = true;

    public static void init(Main plugin) {
        instance = new WaypointManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.waypoint.interval_ticks", 200);
        instance.runTaskTimer(plugin, 40L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.waypoint");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Waypoint color dark_red — this is a client-side feature
            // The Paper API has no direct waypoint management,
            // but we can send a packet or use the scoreboard
            // For now just a stub — the functionality stays in the datapack
        }
    }
}
