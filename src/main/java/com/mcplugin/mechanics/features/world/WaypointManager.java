package com.mcplugin.mechanics.features.world;

import com.mcplugin.core.Main;
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
            // Waypoint color dark_red — это клиентская фича
            // В Paper API нет прямого управления waypoints,
            // но мы можем отправить packet или использовать scoreboard
            // Пока просто заглушка — функционал остаётся в датапаке
        }
    }
}
