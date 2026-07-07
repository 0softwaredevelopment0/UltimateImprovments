package com.mcplugin.mechanics.features.world;

import com.mcplugin.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class DragonEggManager extends BukkitRunnable {

    private static DragonEggManager instance;
    private static boolean enabled = true;
    private static String worldName = "world_the_end";
    private static int eggX = 0;
    private static int eggY = 142;
    private static int eggZ = 0;
    private static int intervalTicks = 1728000; // 24h по умолчанию
    private static double spawnChance = 1.0;    // 100% по умолчанию

    public static void init(Main plugin) {
        instance = new DragonEggManager();
        reloadConfig();
        instance.runTaskTimer(plugin, 200L, intervalTicks);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.dragonegg");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", false);
        worldName = cfg.getString("world", "world_the_end");
        eggX = cfg.getInt("x", 0);
        eggY = cfg.getInt("y", 142);
        eggZ = cfg.getInt("z", 0);
        intervalTicks = cfg.getInt("interval_ticks", 1728000);
        spawnChance = cfg.getDouble("spawn_chance", 1.0);
    }

    @Override
    public void run() {
        if (!enabled) return;

        // Проверка шанса спавна
        if (spawnChance < 1.0 && ThreadLocalRandom.current().nextDouble() > spawnChance) {
            return;
        }

        World end = Bukkit.getWorld(worldName);
        if (end == null) {
            end = Bukkit.getWorlds().stream()
                    .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                    .findFirst()
                    .orElse(null);
        }
        if (end == null) return;

        end.getBlockAt(eggX, eggY, eggZ).setType(Material.DRAGON_EGG);
    }
}
