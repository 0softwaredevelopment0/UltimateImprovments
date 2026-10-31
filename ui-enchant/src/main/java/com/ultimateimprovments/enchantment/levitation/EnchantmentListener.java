package com.ultimateimprovments.enchantment.levitation;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.integrity.ItemDurabilityUtil;
import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener: Levitation enchantment — gentle jetpack.
 * <p>
 * While a player WEARS a chestplate carrying the Levitation charm, holding the
 * jump key gives them the real vanilla {@link PotionEffectType#LEVITATION}
 * effect, which lifts them upward (jetpack-style, ~6 blocks per second).
 * <p>
 * <b>Why the potion effect and not a raw velocity push:</b> the old version
 * wrote {@code player.setVelocity(...)} every sweep. The client never simulates
 * that push, so the server's position and the client's prediction disagreed and
 * the movement anticheat read the ascent as hack-flight — players were kicked
 * for flying. A levitation effect is simulated by BOTH sides
 * ({@code LivingEntity#travelInAir} handles it server-side, the vanilla client
 * runs the same physics), so the client predicts the same motion the server
 * expects, and every movement check in the anticheat already treats
 * {@code LEVITATION} as a legitimate ascent reason (see
 * {@code ExemptionManager#isMovementExempted}).
 * <p>
 * <b>Strength:</b> vanilla levitation converges to a constant rise of
 * {@code 0.05 * (amplifier + 1)} blocks/tick
 * ({@code v = 0.8 * v + 0.01 * (amplifier + 1)} per tick in
 * {@code LivingEntity#travelInAir}). Amplifier
 * {@value #LEVITATION_AMPLIFIER} therefore tops out at exactly
 * {@value #TARGET_RISE_PER_TICK} blocks/tick — the same lift the old
 * velocity jetpack pushed, so the charm feels unchanged.
 * <p>
 * <b>Integrity cost:</b> actively boosting drains 1 use of integrity from the
 * charming CHESTPLATE per second (the jetpack is the chestplate's engine —
 * the rest of the set is not involved). Cheaper than Flight (which strains
 * the whole armor set), fitting its lower value. Standing still / not holding
 * jump is free.
 * <p>
 * Safety rules:
 * <ul>
 *   <li>players pending auth (frozen by the auth system) are never boosted;</li>
 *   <li>Creative/Spectator players are never touched (they already fly);</li>
 *   <li>flying players (e.g. the Flight charm) are never touched;</li>
 *   <li>gliding players (elytra) and vehicle riders are never touched;</li>
 *   <li>the effect is applied by THIS charm only — on release it is removed and
 *       the leftover upward momentum is dropped, so the anticheat never sees
 *       unexplained ascent after the effect is gone.</li>
 * </ul>
 * Jump detection uses {@link ServerPlayer#getLastClientInput()} — the player's
 * real input state, updated by the server every tick. A sweep every
 * {@value #SWEEP_INTERVAL_TICKS} ticks checks every online player; a separate
 * 1-second task handles the integrity drain.
 */
public final class EnchantmentListener {

    /** Sweep interval: 2 ticks (0.1s) — responsive, feels like a jetpack. */
    static final long SWEEP_INTERVAL_TICKS = 2L;

    /** Integrity drain interval: 20 ticks = 1 second of active boosting. */
    static final long DRAIN_INTERVAL_TICKS = 20L;

    /** The lift the charm has always produced: 0.30 blocks/tick ≈ 6 blocks/s. */
    static final double TARGET_RISE_PER_TICK = 0.30;

    /**
     * Levitation amplifier that reproduces {@link #TARGET_RISE_PER_TICK}:
     * vanilla terminal rise is {@code 0.05 * (amplifier + 1)} blocks/tick,
     * so amplifier 5 → 0.30 blocks/tick.
     */
    static final int LEVITATION_AMPLIFIER =
            (int) Math.round(TARGET_RISE_PER_TICK / 0.05) - 1;

    /**
     * Effect duration: 40 ticks (2s). Refreshed only when less than
     * {@value #EFFECT_REFRESH_BELOW_TICKS} ticks remain, so the client sees about
     * one effect packet per second instead of one per sweep.
     */
    static final int EFFECT_DURATION_TICKS = 40;

    /** Re-apply the effect when its remaining duration falls below this. */
    static final int EFFECT_REFRESH_BELOW_TICKS = 20;

    /** Players ACTIVELY boosting right now (jump held, effect applied on the last sweep). */
    private static final Set<UUID> BOOSTING = ConcurrentHashMap.newKeySet();

    /** Players currently carrying OUR levitation effect (so we only ever remove our own). */
    private static final Set<UUID> LIFTING = ConcurrentHashMap.newKeySet();

    private EnchantmentListener() {}

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: boost every online Levitation chestplate wearer. */
    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                updatePlayer(player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Levitation] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Keeps the levitation effect alive while the player holds the jump key,
     * and takes it away (plus the leftover momentum) as soon as they stop.
     */
    static void updatePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        if (!shouldLift(player)) {
            endLift(player);
            return;
        }

        grantLift(player);
        BOOSTING.add(uuid);
    }

    /** All the "may this player be lifted right now?" rules in one place. */
    private static boolean shouldLift(Player player) {
        // Never boost a frozen (pending auth) player.
        if (CoreHooks.isPendingAuth(player.getUniqueId())) return false;

        // Creative/Spectator already fly — never touch them.
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;

        // Flying (Flight charm / another fly source) — leave alone.
        if (player.isFlying()) return false;

        // Gliding with an elytra — the lift would fight the glide.
        if (player.isGliding()) return false;

        // Riding something — the vehicle owns the movement.
        if (player.isInsideVehicle()) return false;

        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() == Material.AIR
                || Enchantment.getLevel(chest) <= 0) {
            return false;
        }

        // Holding the jump key right now?
        return isJumping(player);
    }

    /**
     * Applies (or refreshes) the vanilla levitation effect on the player.
     * The effect carries no particles and no HUD icon — it is the chestplate's
     * engine, not a status the player has to watch.
     */
    private static void grantLift(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.LEVITATION);
        boolean ours = current != null && current.getAmplifier() == LEVITATION_AMPLIFIER;

        if (!ours || current.getDuration() <= EFFECT_REFRESH_BELOW_TICKS) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                    EFFECT_DURATION_TICKS, LEVITATION_AMPLIFIER,
                    true,   // ambient: no "fresh effect" particles on the client
                    false,  // particles
                    false)); // HUD icon
        }
        LIFTING.add(player.getUniqueId());
    }

    /**
     * Stops the lift: removes OUR levitation effect and drops the leftover
     * upward velocity. Without the latter the player would keep ascending for
     * a dozen ticks after the effect is gone — ascent with no legitimate reason
     * as far as the anticheat can tell.
     */
    private static void endLift(Player player) {
        if (!LIFTING.remove(player.getUniqueId())) return;

        PotionEffect current = player.getPotionEffect(PotionEffectType.LEVITATION);
        // Not our effect (shulker bullet, another plugin) — leave it alone.
        if (current == null || current.getAmplifier() != LEVITATION_AMPLIFIER) return;

        player.removePotionEffect(PotionEffectType.LEVITATION);

        Vector velocity = player.getVelocity();
        if (velocity.getY() > 0) {
            velocity.setY(0);
            player.setVelocity(velocity);
        }
    }

    /**
     * True if the player's latest client input has the jump key pressed.
     */
    private static boolean isJumping(Player player) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            Input input = serverPlayer.getLastClientInput();
            return input != null && input.jump();
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  INTEGRITY DRAIN
    // ─────────────────────────────────────────────────────────────

    /**
     * Every second, every ACTIVELY BOOSTING player's charming chestplate loses
     * 1 use of integrity. Not boosting (key released, airborne drift, landed)
     * is free.
     */
    private static void drainIntegrity() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (!BOOSTING.remove(player.getUniqueId())) continue; // not boosting this second

                ItemStack chest = player.getInventory().getChestplate();
                if (chest == null || chest.getType() == Material.AIR
                        || Enchantment.getLevel(chest) <= 0) {
                    continue;
                }

                ItemDurabilityUtil.decreaseItemIntegrity(chest, 1, player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Levitation] Integrity drain error for " + player.getName()
                        + ": " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the periodic jetpack sweep + integrity drain.
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::drainIntegrity,
                DRAIN_INTERVAL_TICKS, DRAIN_INTERVAL_TICKS);
        ConsoleLogger.info("[Levitation] Listener registered (jetpack sweep every "
                + (SWEEP_INTERVAL_TICKS / 20.0) + "s, levitation "
                + (LEVITATION_AMPLIFIER + 1) + " while jump is held, 1 chestplate integrity-use/s).");
    }
}
