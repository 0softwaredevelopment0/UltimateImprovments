package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * The server has not responding! — grants the {@code ui:datapack/server_not_responding}
 * achievement to every online player when the server's main thread freezes
 * for at least 10 continuous seconds.
 * <p>
 * A per-tick task records the actual wall-clock gap between consecutive
 * scheduled executions: while the main thread is frozen the task runs late,
 * so the gap is exactly the freeze duration. When a gap of {@code >= 10s}
 * is detected (the server was unresponsive that whole time), all online
 * players are awarded (idempotently).
 */
public final class ServerFreezeListener {

    /** The datapack advancement key (parent: {@code ui:datapack/out_of_memory}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/server_not_responding");

    /** Required main-thread freeze duration in milliseconds (10 seconds). */
    private static final long REQUIRED_FREEZE_MS = 10_000L;

    private static long lastTickNanos = 0L;

    private ServerFreezeListener() {}

    /** Awards the achievement to the player. Idempotent. */
    private static void grant(Player player) {
        if (player == null) return;
        try {
            Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
            if (adv == null) return; // datapack not loaded yet

            var progress = player.getAdvancementProgress(adv);
            if (!progress.isDone()) {
                progress.awardCriteria("1");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[ServerFreeze] Award error for " + player.getName() + ": " + e.getMessage());
        }
    }

    /** One tick: measure the gap since the last execution — a huge gap means a freeze. */
    private static void tick() {
        long now = System.nanoTime();

        if (lastTickNanos != 0L) {
            long gapMs = (now - lastTickNanos) / 1_000_000L;

            if (gapMs >= REQUIRED_FREEZE_MS) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    grant(player);
                }
            }
        }

        lastTickNanos = now;
    }

    /**
     * Starts the per-tick freeze detection.
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, ServerFreezeListener::tick, 1L, 1L);
        ConsoleLogger.info("[ServerFreeze] Listener registered (main thread freeze ≥ 10s → The server has not responding!).");
    }
}
