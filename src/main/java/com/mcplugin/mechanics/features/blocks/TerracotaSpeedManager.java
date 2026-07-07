package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class TerracotaSpeedManager extends BukkitRunnable {

    private static TerracotaSpeedManager instance;
    private static boolean enabled = true;
    private static int speedAmp = 4;

    public static void init(Main plugin) {
        instance = new TerracotaSpeedManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.terracotaspeed.interval_ticks", 5);
        instance.runTaskTimer(plugin, 20L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.terracotaspeed");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        speedAmp = cfg.getInt("speed_amplifier", 4);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getBlock().getRelative(0, -1, 0).getType() == Material.MAGENTA_GLAZED_TERRACOTTA) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, speedAmp, true));
            }
        }
    }
}
