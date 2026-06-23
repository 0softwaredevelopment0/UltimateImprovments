package com.mcplugin.mechanics.features.player;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AttributesManager extends BukkitRunnable {

    private static AttributesManager instance;
    private static boolean enabled = true;
    private static double attackDmg = 0.1;
    private static double sneakSpeed = 8192;
    private static double attackSpeed = 3.5;

    public static void init(Main plugin) {
        instance = new AttributesManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.attributes.interval_ticks", 200);
        instance.runTaskTimer(plugin, 0L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.attributes");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        attackDmg = cfg.getDouble("attack_damage", 0.1);
        sneakSpeed = cfg.getDouble("sneak_speed", 8192);
        attackSpeed = cfg.getDouble("attack_speed", 3.5);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            var atkDmg = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atkDmg != null) atkDmg.setBaseValue(attackDmg);

            var sneak = player.getAttribute(Attribute.SNEAKING_SPEED);
            if (sneak != null) sneak.setBaseValue(sneakSpeed);

            var atkSpd = player.getAttribute(Attribute.ATTACK_SPEED);
            if (atkSpd != null) atkSpd.setBaseValue(attackSpeed);
        }
    }
}
