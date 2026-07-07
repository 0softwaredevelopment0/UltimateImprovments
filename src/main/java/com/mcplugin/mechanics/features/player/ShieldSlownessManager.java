package com.mcplugin.mechanics.features.player;

import com.mcplugin.core.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ShieldSlownessManager implements Listener {

    private static ShieldSlownessManager instance;
    private static boolean enabled = true;
    private static int slownessAmp = 255;
    private static int slownessDuration = 20;

    public static void init(Main plugin) {
        instance = new ShieldSlownessManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.shieldslowness");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        slownessAmp = cfg.getInt("slowness_amplifier", 255);
        slownessDuration = cfg.getInt("slowness_duration", 20);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (!player.isBlocking()) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessDuration, slownessAmp, true));
    }
}
