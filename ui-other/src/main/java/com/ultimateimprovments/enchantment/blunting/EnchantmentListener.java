package com.ultimateimprovments.enchantment.blunting;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener: Blunting — the damage-reduction engine (sharpness reversed).
 * <p>
 * When an entity is MELEE-attacked (direct {@link EntityDamageByEntityEvent},
 * no projectiles / explosions) while holding a Blunting weapon, the final
 * damage is reduced by {@code level × 0.5}. The reduction applies to the FINAL
 * damage (after armor and effects), floors at 0, and the vanilla armor is not
 * re-evaluated — the same reverse of Sharpness, which adds on top at the end.
 * <p>
 * A level-255 blunted weapon reduces damage by 127.5 — effectively zeroing
 * out any hit.
 */
public class EnchantmentListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Melee hits only: the damager must be the attacker itself, not a projectile.
        if (!(event.getDamager() instanceof org.bukkit.entity.LivingEntity attacker)) return;

        ItemStack weapon = attacker.getEquipment() != null
                ? attacker.getEquipment().getItemInMainHand()
                : null;
        int level = Enchantment.getLevel(weapon == null ? new ItemStack(org.bukkit.Material.AIR) : weapon);
        if (level <= 0) return;

        double reduction = level * Enchantment.DAMAGE_REDUCTION_PER_LEVEL;
        double newDamage = Math.max(0.0, event.getFinalDamage() - reduction);
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, Math.max(0.0, newDamage));
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }

        Bukkit.getLogger().finest("[Blunting] Level " + level + " reduced damage by " + reduction);
    }
}
