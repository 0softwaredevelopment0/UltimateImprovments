package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timed advancement challenge: <b>Let me teleport!</b> — teleport with an
 * ender pearl 60 times within 1 minute to earn the
 * {@code ui:datapack/let_me_teleport} achievement.
 * <p>
 * Started via {@code /ui advancement start teleport}. Per-teleport work is a
 * single map increment; a cheap 1-second sweep only iterates players with an
 * active challenge (showing progress / expiring). The global
 * {@link TimedChallengeLock} guarantees only one challenge per player at a time.
 */
public final class EnderPearlChallenge implements Listener {

    /** The datapack advancement key (parent: {@code ui:datapack/suicide}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/let_me_teleport");

    /** Challenge duration: 1 minute in milliseconds. */
    private static final long DURATION_MS = 60_000L;

    /** Ender pearl teleports needed for the achievement. */
    private static final int GOAL = 60;

    /** Active challenges: player UUID → state. */
    private static final Map<UUID, ChallengeState> ACTIVE = new ConcurrentHashMap<>();

    private EnderPearlChallenge() {}

    private static final class ChallengeState {
        final long startTime = System.currentTimeMillis();
        int count = 0;
    }

    // =========================
    // COMMAND ENTRY
    // =========================

    /** Starts the timed challenge ("teleport"). */
    public static void start(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (!TimedChallengeLock.tryAcquire(uuid)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>✖ <white>You already have an active challenge! Finish the current one first.</white>"));
            return;
        }

        ACTIVE.put(uuid, new ChallengeState());
        player.sendMessage(MessageUtil.parse(
                "<gold>⏱ <white>Challenge <yellow>Let me teleport!</yellow> started!</white>\n"
                + "<gray>Teleport <white>60</white> times with an ender pearl in <white>1 minute</white>."));
    }

    /** Cancels the player's active challenge, if any. Returns true if one was stopped. */
    public static boolean stop(Player player) {
        ChallengeState state = ACTIVE.remove(player.getUniqueId());
        TimedChallengeLock.release(player.getUniqueId());
        if (state == null) return false;
        player.sendMessage(MessageUtil.parse(
                "<yellow>⏹ <white>The challenge has been canceled. Teleports: <yellow>" + state.count + "</yellow>/60.</white>"));
        return true;
    }

    // =========================
    // COUNTING
    // =========================

    /** Counts a successful ender pearl teleport toward the player's active challenge. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();
        ChallengeState state = ACTIVE.get(player.getUniqueId());
        if (state == null) return;

        state.count++;
        if (state.count >= GOAL) {
            ACTIVE.remove(player.getUniqueId());
            TimedChallengeLock.release(player.getUniqueId());
            grant(player, state.count);
        }
    }

    // =========================
    // SWEEP (20 ticks) — progress bar + expiry
    // =========================

    private static void sweep() {
        long now = System.currentTimeMillis();

        ACTIVE.entrySet().removeIf(entry -> {
            ChallengeState state = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            long elapsed = now - state.startTime;

            if (elapsed >= DURATION_MS) {
                TimedChallengeLock.release(entry.getKey());
                if (player != null) {
                    player.sendMessage(MessageUtil.parse(
                            "<red>✖ <white>Time's up! You teleported: <yellow>" + state.count
                            + "</yellow>/60 times.</white>"));
                }
                return true;
            }

            if (player != null) {
                int percent = (int) (state.count * 100.0 / GOAL);
                long leftSec = (DURATION_MS - elapsed) / 1000;
                player.sendActionBar(MessageUtil.parse(
                        "<gold>🌀 <white>Teleports: <yellow>" + state.count + "</yellow>/60</white> "
                        + "<gray>(" + percent + "%) · remaining " + leftSec + "s</gray>"));
            }
            return false;
        });
    }

    // =========================
    // REWARD
    // =========================

    /** Awards the achievement and notifies the player. Idempotent. */
    private static void grant(Player player, int count) {
        try {
            Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[EnderPearlChallenge] Award error for " + player.getName() + ": " + e.getMessage());
        }
        player.sendMessage(MessageUtil.parse(
                "<green>✔ <white>Challenge completed! <yellow>Let me teleport!</yellow> reached "
                + "(" + count + " ender pearl teleports).</white>"));
    }

    // =========================
    // REGISTRATION
    // =========================

    public static void register(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new EnderPearlChallenge(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnderPearlChallenge::sweep, 20L, 20L);
        ConsoleLogger.info("[EnderPearlChallenge] Registered (timed challenge: Let me teleport!).");
    }
}
