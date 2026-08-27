package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * java.lang.OutOfMemoryError — grants the {@code ui:datapack/out_of_memory}
 * achievement to every online player when the Java process's heap usage
 * reaches 100% for at least one second.
 * <p>
 * A cheap 1-second sweep (20 ticks) reads the JVM heap
 * ({@link Runtime#totalMemory()} / {@link Runtime#maxMemory()}): when the
 * used heap hits the maximum (100%), all online players are awarded
 * (idempotently).
 */
public final class OutOfMemoryListener {

    /** The datapack advancement key (parent: {@code ui:datapack/halt_the_plugin}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/out_of_memory");

    /** Sweep interval: 20 ticks (1s). */
    private static final long SWEEP_INTERVAL_TICKS = 20L;

    /** Heap usage threshold: 95% is treated as "RAM exhausted" (JVM OOMs before literal 100%). */
    private static final double THRESHOLD_PERCENT = 95.0;

    private OutOfMemoryListener() {}

    /** Current JVM heap usage in percent (0..100+). */
    private static double ramUsagePercent() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        if (max <= 0) return 0.0;
        long used = rt.totalMemory() - rt.freeMemory();
        return used * 100.0 / max;
    }

    /** One sweep tick: grant the achievement to everyone online while heap is above the threshold. */
    private static void sweep() {
        if (ramUsagePercent() < THRESHOLD_PERCENT) return;

        Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
        if (adv == null) return; // datapack not loaded yet

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[OutOfMemory] Award error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Starts the periodic RAM-usage sweep. */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, OutOfMemoryListener::sweep,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[OutOfMemory] Listener registered (heap ≥ 95% check every 1s).");
    }
}
