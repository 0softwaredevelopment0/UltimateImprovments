package com.ultimateimprovments.enchantment.igniting;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Listener: Igniting enchantment — armored retaliation.
 * <p>
 * When a creature wearing armor with the Igniting charm (player, mob, ...) is hit
 * by another creature, the ATTACKER is set on fire for a number of seconds equal
 * to the charm level (level 5 → 5 seconds = 100 fire ticks).
 * <p>
 * Works for ANY armored entity — player or mob — and for ANY armor piece
 * (helmet, chestplate, leggings, boots). The highest charm level among the worn
 * pieces is used.
 * <p>
 * It's plain ignition: water / rain still extinguishes it, Fire Resistance protects
 * the attacker. The attacker must be a {@link LivingEntity} (projectiles don't burn).
 */
public class EnchantmentListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // The victim must be a living armored entity.
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // The attacker must be a living creature we can set on fire.
        Entity damager = event.getDamager();
        if (!(damager instanceof LivingEntity attacker)) return;

        // Self-damage edge case — don't ignite the victim on itself.
        if (attacker.equals(victim)) return;

        // Fire Resistance / fire-immune creatures can't be ignited — skip.
        if (attacker.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) return;
        if (isFireImmuneType(attacker)) return;

        // Find the highest Igniting level among the victim's worn armor.
        int level = getArmorLevel(victim);
        if (level <= 0) return;

        // Ignite the attacker: level seconds = level * 20 ticks.
        // Never shorten an existing longer burn (e.g. from Fire Aspect).
        int newFireTicks = level * 20;
        attacker.setFireTicks(Math.max(attacker.getFireTicks(), newFireTicks));
    }

    /**
     * Fire-immune mob types that can't be set on fire (no isFireImmune() in the
     * Bukkit API of this version — checked by entity type).
     */
    private static boolean isFireImmuneType(LivingEntity entity) {
        return switch (entity.getType()) {
            case BLAZE, ZOMBIFIED_PIGLIN, MAGMA_CUBE, STRIDER, WITHER -> true;
            default -> false;
        };
    }

    /**
     * Scans all worn armor pieces and returns the highest Igniting level.
     */
    private static int getArmorLevel(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return 0;

        int max = 0;
        for (ItemStack item : equipment.getArmorContents()) {
            if (item == null || item.getType().isAir()) continue;
            int lvl = com.ultimateimprovments.enchantment.igniting.Enchantment.getLevel(item);
            if (lvl > max) max = lvl;
        }
        return max;
    }
}
