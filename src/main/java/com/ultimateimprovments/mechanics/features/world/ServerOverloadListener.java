package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * Something's not right here... — grants the {@code ui:datapack/server_overload}
 * achievement to every online player when the server runs overloaded for 5
 * continuous seconds (MSPT above 50ms).
 * <p>
 * MSPT is measured directly: a per-tick task records the actual wall-clock gap
 * between consecutive scheduled executions — during lag the task runs late, so
 * that gap is exactly the tick duration. When a continuous run of ticks with
 * {@code gap > 50ms} accumulates 5 real seconds, all online players are awarded
 * (idempotently). A single normal tick resets the counter.
 */
public final class ServerOverloadListener {

    /** The datapack advancement key (parent: {@code ui:datapack/start}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/server_overload");

    /** Tick considered "overloaded" when it takes longer than 50ms. */
    private static final double OVERLOAD_THRESHOLD_MS = 50.0;

    /** Required continuous overload duration in milliseconds (5 seconds). */
    private static final long REQUIRED_OVERLOAD_MS = 5000L;

    private static long lastTickNanos = 0L;
    private static long overloadMs = 0L;

    private ServerOverloadListener() {}

    /** Awards the achievement to the player. Idempotent. */
    public static void grant(Player player) {
        if (player == null) return;
        try {
            Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
            if (adv == null) return; // datapack not loaded yet

            var progress = player.getAdvancementProgress(adv);
            if (!progress.isDone()) {
                progress.awardCriteria("1");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[ServerOverload] Award error for " + player.getName() + ": " + e.getMessage());
        }
    }

    /** One tick: measure MSPT and accumulate continuous overload time. */
    private static void tick() {
        long now = System.nanoTime();

        if (lastTickNanos != 0L) {
            double deltaMs = (now - lastTickNanos) / 1_000_000.0;

            if (deltaMs > OVERLOAD_THRESHOLD_MS) {
                overloadMs += (long) deltaMs;
                if (overloadMs >= REQUIRED_OVERLOAD_MS) {
                    overloadMs = 0L; // reset, can trigger again later
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        grant(player);
                    }
                }
            } else {
                overloadMs = 0L;
            }
        }

        lastTickNanos = now;
    }

    /**
     * Starts the per-tick MSPT measurement and overload check.
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, ServerOverloadListener::tick, 1L, 1L);
        ConsoleLogger.info("[ServerOverload] Listener registered (MSPT > 50ms for 5s → Something's not right here...).");
    }
}
