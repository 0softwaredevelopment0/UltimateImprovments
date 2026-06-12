package com.mcplugin.features.beacon;

import com.mcplugin.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class BeaconManager extends BukkitRunnable {

    private static BeaconManager instance;
    private static boolean enabled = true;
    private static boolean giveGlowing = true;
    private static boolean giveBlindness = true;
    private static int regenAmp = 4;
    private static int resistAmp = 4;

    public static void init(Main plugin) {
        instance = new BeaconManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.beacon.interval_ticks", 5);
        instance.runTaskTimer(plugin, 20L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.beacon");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        giveGlowing = cfg.getBoolean("glowing", true);
        giveBlindness = cfg.getBoolean("blindness", true);
        regenAmp = cfg.getInt("regeneration_amplifier", 4);
        resistAmp = cfg.getInt("resistance_amplifier", 4);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getBlock().getRelative(0, -1, 0).getType() == Material.BEACON) {
                if (giveGlowing) player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true));
                if (giveBlindness) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, regenAmp, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, resistAmp, true));
            }
        }
    }
}
