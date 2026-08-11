package com.ultimateimprovments.enchantment.levitation;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.security.auth.AuthPlayerState;
import com.ultimateimprovments.util.ConsoleLogger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Listener: Levitation enchantment — gentle jetpack.
 * <p>
 * While a player WEARS a chestplate carrying the Levitation charm, holding the
 * jump key smoothly lifts them upward (jetpack-style, ~1 block per press).
 * <p>
 * Safety rules:
 * <ul>
 *   <li>players pending auth (frozen by the auth system) are never boosted;</li>
 *   <li>Creative/Spectator players are never touched (they already fly);</li>
 *   <li>flying players (e.g. the Flight charm) are never touched;</li>
 *   <li>existing upward velocity is never reduced — only topped up to the target.</li>
 * </ul>
 * Jump detection uses {@link ServerPlayer#getLastClientInput()} — the player's
 * real input state, updated by the server every tick. A periodic sweep every
 * {@value #SWEEP_INTERVAL_TICKS} ticks checks every online player.
 */
public final class EnchantmentListener {

    /** Sweep interval: 2 ticks (0.1s) — responsive, feels like a jetpack. */
    static final long SWEEP_INTERVAL_TICKS = 2L;

    /** Target upward velocity while the jump key is held (blocks/tick).
     *  ~0.15 б/т каждые 2 тика при гравитации 0.08/тик ≈ 2.2 блока/сек — плавно. */
    private static final double JETPACK_Y = 0.15;

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
     * Applies the gentle upward boost while the player holds the jump key.
     */
    static void updatePlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        // Never boost a frozen (pending auth) player.
        AuthPlayerState auth = AuthPlayerState.getInstance();
        if (auth != null && auth.isPendingAuth(player.getUniqueId())) return;

        // Creative/Spectator already fly — never touch them.
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        // Flying (Flight charm / another fly source) — leave alone.
        if (player.isFlying()) return;
        // Gliding with an elytra — boost would fight the glide.
        if (player.isGliding()) return;

        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || com.ultimateimprovments.enchantment.levitation.Enchantment.getLevel(chest) <= 0) return;

        // Is the player holding the jump key right now?
        if (!isJumping(player)) return;

        // Gentle boost up: keep horizontal velocity untouched, never reduce an
        // existing upward velocity.
        Vector velocity = player.getVelocity();
        if (velocity.getY() < JETPACK_Y) {
            velocity.setY(JETPACK_Y);
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
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the periodic jetpack sweep.
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[Levitation] Listener registered (jetpack sweep every "
                + (SWEEP_INTERVAL_TICKS / 20.0) + "s).");
    }
}
