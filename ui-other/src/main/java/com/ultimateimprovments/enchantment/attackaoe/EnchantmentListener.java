package com.ultimateimprovments.enchantment.attackaoe;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Listener: Attack AoE enchantment — cleave every entity in the radius.
 * <p>
 * When a player lands a hit with an Attack AoE weapon, EVERY living entity in a
 * cubic area around the struck victim takes the same damage as the original hit
 * (the victim itself was already damaged by the vanilla attack, so it's not hit
 * again, and the attacker never hurts itself).
 * <p>
 * Radius = enchantment level: level 1 → 3×3, level 2 → 5×5, level 3 → 7×7, ...
 * i.e. a (2·level+1)³ cube centered on the victim. 🛡 For performance the scan
 * radius is capped at {@value #MAX_SCAN_RADIUS} blocks (65×65×65 cube) — levels
 * above that keep the same radius (the cap is 10 — the level where the old
 * falloff formula would have reached zero anyway).
 * <p>
 * Cleave only hits entities of the SAME type as the victim and only when there
 * is clear line of sight. No damage falloff: every cleaved target takes the
 * same force as the original hit.
 * <p>
 * Sneaking disables the AoE for precise single-target attacks.
 */
public class EnchantmentListener implements Listener {

    /** Balance cap: the cleave radius equals the charm level, capped at 10 —
     *  the level at which the old falloff formula (level/10 % per block) would
     *  have reached zero anyway. Beyond 10 the radius simply stops growing. */
    private static final int MAX_SCAN_RADIUS = 10;

    /**
     * Recursion guard — our own {@code target.damage(...)} calls fire
     * {@link EntityDamageByEntityEvent} synchronously and would otherwise
     * re-enter this listener and cascade the AoE chain indefinitely.
     * Single-threaded (main thread), so a simple boolean is enough.
     */
    private boolean processingAoE = false;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Skip damage caused by our own AoE hits — prevents infinite cascade.
        if (processingAoE) return;

        // Only the player's own weapon swing triggers the cleave.
        if (!(event.getDamager() instanceof Player player)) return;

        // Sneaking = precise single-target attack, like AoE/VeinMiner/TreeCapitator.
        if (player.isSneaking()) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return;

        int level = Enchantment.getLevel(weapon);
        if (level <= 0) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.isValid()) return;

        Location origin = victim.getLocation();
        World world = origin.getWorld();
        if (world == null) return;

        int radius = Math.min(level, MAX_SCAN_RADIUS);
        double damage = event.getDamage();
        if (damage <= 0) return;

        // No damage falloff: every cleaved target takes the SAME force as the
        // original hit (the balance lives in the radius cap, not in falloff).

        // All living entities in the (2·radius+1)³ cube around the victim.
        Collection<Entity> nearby = world.getNearbyEntities(origin, radius, radius, radius);

        processingAoE = true;
        try {
            for (Entity entity : nearby) {
                // The struck victim already took the hit; the attacker never hurts itself.
                if (entity.equals(victim) || entity.equals(player)) continue;
                // Only entities of the SAME type are cleaved (hit a zombie → only
                // other zombies in the radius are damaged).
                if (entity.getType() != victim.getType()) continue;
                if (!(entity instanceof LivingEntity target)) continue;
                if (!target.isValid() || target.isDead()) continue;
                // Anti-abuse: a target hidden behind a block is not cleaved.
                if (!hasLineOfSight(player, target)) continue;

                // Same force as the original hit — armor and enchantments apply normally.
                target.damage(damage, player);
            }
        } finally {
            processingAoE = false;
        }
    }

    /** True when no solid block lies between the attacker and the target. */
    private boolean hasLineOfSight(Player player, LivingEntity target) {
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Location end = target.getEyeLocation();

        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.5) return true;

        direction.multiply(1.0 / distance);
        double maxDistance = distance - 0.5;
        if (maxDistance <= 0) return true;

        return world.rayTraceBlocks(start, direction, maxDistance,
                FluidCollisionMode.NEVER, true) == null;
    }
}
