package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Main;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Prevents the levitation effect from shulker bullets being applied to players.
 * Shulkers still deal damage, but the levitation effect is removed immediately.
 * Enabled/disabled via config.yml → features.shulker_protection.enabled
 */
public class ShulkerBulletListener implements Listener {

    @EventHandler
    public void onShulkerDamage(EntityDamageByEntityEvent e) {
        // Check: is the protection enabled in the config
        if (!Main.getInstance().getConfig()
                .getBoolean("features.shulker_protection.enabled", true)) return;

        // Check: the shulker bullet hits a player
        if (!(e.getDamager() instanceof ShulkerBullet)) return;
        if (!(e.getEntity() instanceof Player player)) return;

        // Remove levitation on the next tick (the effect is applied after the damage event)
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isValid() && !player.isDead()) {
                player.removePotionEffect(PotionEffectType.LEVITATION);
            }
        });
    }
}
