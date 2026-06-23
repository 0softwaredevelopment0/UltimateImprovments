package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class BoostedCobwebManager extends BukkitRunnable {

    private static BoostedCobwebManager instance;
    private static boolean enabled = true;
    private static int fatigueAmp = 255;
    private static int weaknessAmp = 255;
    private static int slownessAmp = 255;
    private static int slowfallAmp = 255;

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
        fatigueAmp = cfg.getInt("fatigue_amplifier", 255);
        weaknessAmp = cfg.getInt("weakness_amplifier", 255);
        slownessAmp = cfg.getInt("slowness_amplifier", 255);
        slowfallAmp = cfg.getInt("slowfall_amplifier", 255);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getBlock().getType() == Material.COBWEB) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, fatigueAmp, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, weaknessAmp, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slownessAmp, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, slowfallAmp, true));
            }
        }
    }
}
