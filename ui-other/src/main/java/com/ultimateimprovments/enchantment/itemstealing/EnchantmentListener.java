package com.ultimateimprovments.enchantment.itemstealing;

import com.ultimateimprovments.core.Main;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Listener: Item Stealing enchantment — steal, don't pull.
 * <p>
 * When a player hooks another PLAYER with an Item Stealing fishing rod, the steal
 * rolls a {@code level × 10%} chance. On success the victim is NOT pulled toward the
 * fisher — instead the item he holds in his hand is thrown OUT of him and flies
 * toward the fisher, and only then picked up:
 * <ul>
 *   <li>main hand item first, offhand as fallback;</li>
 *   <li>the whole stack is stolen;</li>
 *   <li>the item is spawned as a physical entity flying at the fisher — it is never
 *       inserted straight into his inventory; if his inventory is full it lands on
 *       the ground for him (or anyone) to pick up.</li>
 * </ul>
 * On a failed roll (or when the hooked player holds NOTHING in both hands) the
 * vanilla behavior stays: the player is pulled normally.
 * <p>
 * <b>Permission:</b> stealing is gated by a LuckPerms-grantable permission
 * ({@value #DEFAULT_STEAL_PERMISSION} by default, configurable in UI-Other.toml);
 * without it the rod never steals.
 * <p>
 * <b>Feedback:</b> on a successful steal a fishing-rod "yank" is played to every
 * player within {@value #YANK_SOUND_RADIUS} blocks of the thief — everyone nearby
 * hears the item get snapped away.
 * <p>
 * Only {@link PlayerFishEvent.State#CAUGHT_ENTITY} is handled — in 26.x that is the
 * state that carries the hooked entity on reel-in; {@code REEL_IN} always fires with
 * {@code getCaught() == null}.
 */
public class EnchantmentListener implements Listener {

    /** Permission allowing a player to steal with this enchantment (LuckPerms-grantable). */
    private static final String DEFAULT_STEAL_PERMISSION = "ui.enchant.itemstealing.steal";

    /** Radius (blocks) of the steal "yank" sound — everyone inside hears it. */
    private static final double YANK_SOUND_RADIUS = 10.0;

    /** How close to the fisher the thrown item stops homing (blocks). */
    private static final double PICKUP_REACH = 1.5;

    /** Maximum homing speed of the thrown item (blocks/tick). */
    private static final double MAX_THROW_SPEED = 1.2;

    /** Initial launch speed of the thrown item (blocks/tick). */
    private static final double LAUNCH_SPEED = 0.6;

    /** Homing gives up after this many ticks (5 s) — the item then just falls. */
    private static final int MAX_FLIGHT_TICKS = 100;

    /** Pickup delay of the thrown item (ticks) — stops the victim re-grabbing it. */
    private static final int THROW_PICKUP_DELAY = 20;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // The hooked entity is only present in the CAUGHT_ENTITY state.
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        // The caught entity must be a player — we steal from players only.
        if (!(event.getCaught() instanceof Player victim)) return;

        Player fisher = event.getPlayer();
        // Can't hook yourself.
        if (victim.equals(fisher)) return;

        // The rod must carry the Item Stealing charm (main hand first, offhand as fallback).
        ItemStack rod = fisher.getInventory().getItemInMainHand();
        if (!Enchantment.isValidTool(rod)) {
            rod = fisher.getInventory().getItemInOffHand();
        }
        if (!Enchantment.isValidTool(rod)) return;
        int level = Enchantment.getLevel(rod);
        if (level <= 0) return;

        // Permission gate: only players with the steal node may use the enchantment
        // (LuckPerms-grantable; configurable in UI-Other.toml).
        if (!canSteal(fisher)) return;

        // Look for an item in the victim's hands: main hand first, offhand as fallback.
        boolean fromOffhand = false;
        ItemStack stolen = victim.getInventory().getItemInMainHand();
        if (stolen == null || stolen.getType().isAir()) {
            stolen = victim.getInventory().getItemInOffHand();
            fromOffhand = true;
        }

        // Nothing in the hands → the player is pulled normally (vanilla).
        if (stolen == null || stolen.getType().isAir()) return;

        // Self-Destruct items are inventory-locked (InventoryLockListener) and
        // must never be yanked out of the victim's hands. Skip the theft.
        if (com.ultimateimprovments.enchantment.selfdestruct.Enchantment.isCursed(stolen)) return;

        // Steal roll: level N = N×10% chance (level 10 = always). A failed roll
        // keeps the vanilla behavior — the player is pulled normally.
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= level * 10) return;

        // Take the item away from the victim...
        if (fromOffhand) {
            victim.getInventory().setItemInOffHand(null);
        } else {
            victim.getInventory().setItemInMainHand(null);
        }

        // ...and THROW it out of him toward the fisher as a physical item entity.
        // It is never inserted straight into the fisher's inventory: it flies to
        // him and only then gets picked up. The pickup delay stops the victim from
        // instantly re-grabbing it at the launch point.
        World world = victim.getWorld();
        Item flying = world.dropItem(victim.getLocation().add(0, 1.2, 0), stolen);
        flying.setPickupDelay(THROW_PICKUP_DELAY);
        flyTo(flying, fisher);

        // The "yank" cue: everyone within 10 blocks of the thief hears the reel snap.
        playYankSound(fisher);

        // Cancel the pull — the player stays in place, only the item "comes" to us.
        event.setCancelled(true);
        // Make sure the bobber retracts instead of staying stuck in the world.
        event.getHook().remove();
    }

    /**
     * True when the fisher is allowed to steal. The gate is the permission defined by
     * {@code enchant.item_stealing_permission} (default
     * {@value #DEFAULT_STEAL_PERMISSION}); {@code enchant.item_stealing_require_permission}
     * (default true) turns the check off. A config failure never locks the feature out.
     */
    private static boolean canSteal(Player player) {
        try {
            var cfg = Main.getInstance().getConfig();
            if (!cfg.getBoolean("enchant.item_stealing_require_permission", true)) {
                return true;
            }
            String node = cfg.getString("enchant.item_stealing_permission", DEFAULT_STEAL_PERMISSION);
            if (node == null || node.isBlank()) node = DEFAULT_STEAL_PERMISSION;
            return player.hasPermission(node);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Plays the steal "yank" (the fishing-rod reel) for every player within
     * {@value #YANK_SOUND_RADIUS} blocks of the thief, so bystanders hear the theft.
     */
    private static void playYankSound(Player fisher) {
        World world = fisher.getWorld();
        Location loc = fisher.getLocation();
        double maxSquared = YANK_SOUND_RADIUS * YANK_SOUND_RADIUS;
        for (Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distanceSquared(loc) <= maxSquared) {
                nearby.playSound(loc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
                        SoundCategory.PLAYERS, 1.0f, 1.3f);
            }
        }
    }

    /**
     * Steers a freshly thrown item entity toward {@code target} for a short while,
     * then releases it to normal physics so the target can pick it up. Gives up
     * after {@value #MAX_FLIGHT_TICKS} ticks, when the target logs off, or once the
     * item dies/disappears (picked up, despawned).
     */
    private static void flyTo(Item item, Player target) {
        // Launch immediately (the repeating task's first tick is one tick away),
        // so the item does not drop at the victim's feet first.
        Vector launch = target.getLocation().add(0, 1.0, 0).toVector()
                .subtract(item.getLocation().toVector());
        if (launch.lengthSquared() > 0.0001) {
            item.setVelocity(launch.normalize().multiply(LAUNCH_SPEED));
        }

        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (item.isDead() || !item.isValid() || !target.isOnline()
                        || ticks++ > MAX_FLIGHT_TICKS) {
                    cancel();
                    return;
                }

                Location from = item.getLocation();
                Location to = target.getLocation().add(0, 1.0, 0);
                double distance = from.distance(to);
                if (distance < PICKUP_REACH) {
                    // At the target — stop homing and let vanilla pickup take over.
                    item.setVelocity(new Vector(0, -0.15, 0));
                    cancel();
                    return;
                }

                Vector direction = to.toVector().subtract(from.toVector()).normalize();
                double speed = Math.min(MAX_THROW_SPEED, 0.35 + distance * 0.04);
                item.setVelocity(direction.multiply(speed));
            }
        }.runTaskTimer(Main.getInstance(), 1L, 1L);
    }
}
