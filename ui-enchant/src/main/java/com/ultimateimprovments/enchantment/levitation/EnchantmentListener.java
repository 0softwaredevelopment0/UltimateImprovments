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
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener: Levitation enchantment — gentle jetpack.
 * <p>
 * While a player WEARS a chestplate carrying the Levitation charm, holding the
 * jump key smoothly lifts them upward (jetpack-style, ~1 block per press).
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
 *   <li>existing upward velocity is never reduced — only topped up to the target.</li>
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

    /** Target upward velocity while the jump key is held (blocks/tick).
     *  2× the old 0.15 (the old toss felt too weak): ~0.30 blocks/tick every
     *  2 ticks with 0.08/tick gravity ≈ 4.4–6 blocks/sec — a real jetpack lift. */
    private static final double JETPACK_Y = 0.30;

    /** Players ACTIVELY boosting right now (jump held, boost applied on the last sweep). */
    private static final java.util.Set<UUID> BOOSTING = ConcurrentHashMap.newKeySet();

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
     * Applies the gentle upward boost while the player holds the jump key,
     * and tracks who is actively boosting (for the integrity drain).
     */
    static void updatePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Never boost a frozen (pending auth) player.
        if (CoreHooks.isPendingAuth(uuid)) {
            BOOSTING.remove(uuid);
            return;
        }

        // Creative/Spectator already fly — never touch them.
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            BOOSTING.remove(uuid);
            return;
        }
        // Flying (Flight charm / another fly source) — leave alone.
        if (player.isFlying()) {
            BOOSTING.remove(uuid);
            return;
        }
        // Gliding with an elytra — boost would fight the glide.
        if (player.isGliding()) {
            BOOSTING.remove(uuid);
            return;
        }

        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() == Material.AIR
                || com.ultimateimprovments.enchantment.levitation.Enchantment.getLevel(chest) <= 0) {
            BOOSTING.remove(uuid);
            return;
        }

        // Is the player holding the jump key right now?
        if (!isJumping(player)) {
            BOOSTING.remove(uuid);
            return;
        }

        // Gentle boost up: keep horizontal velocity untouched, never reduce an
        // existing upward velocity.
        Vector velocity = player.getVelocity();
        if (velocity.getY() < JETPACK_Y) {
            velocity.setY(JETPACK_Y);
            player.setVelocity(velocity);
        }
        BOOSTING.add(uuid);
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
                        || com.ultimateimprovments.enchantment.levitation.Enchantment.getLevel(chest) <= 0) {
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
                + (SWEEP_INTERVAL_TICKS / 20.0) + "s, 1 chestplate integrity-use/s while boosting).");
    }
}
