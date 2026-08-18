package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * Where is the Earth's core here? — grants the
 * {@code ui:datapack/earth_core} achievement when a player reaches the
 * lower limit for block placement (the bottom of the buildable world, taken
 * from the dimension settings via {@link org.bukkit.World#getMinHeight()}).
 * <p>
 * A cheap periodic sweep (no per-move overhead) checks every online player and
 * awards the achievement once — {@code awardCriteria} is idempotent and the
 * progress is skipped once done.
 */
public final class EarthCoreListener {

    /** The datapack advancement key (parent: {@code ui:datapack/beyond_space}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/earth_core");

    /** Sweep interval: 20 ticks (1s) — cheap enough to not load the server. */
    private static final long SWEEP_INTERVAL_TICKS = 20L;

    private EarthCoreListener() {}

    /** One sweep tick: grant the achievement to every player at the height floor. */
    private static void sweep() {
        Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
        if (adv == null) return; // datapack not loaded yet

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var progress = player.getAdvancementProgress(adv);
                if (progress.isDone()) continue;

                // The lowest placeable block is at getMinHeight()
                // (e.g. y=-2032 for min_y -2032 / height 4064).
                double y = player.getLocation().getY();
                if (y <= player.getWorld().getMinHeight()) {
                    progress.awardCriteria("1");
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[EarthCore] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Starts the periodic height-floor sweep.
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, EarthCoreListener::sweep,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[EarthCore] Listener registered (height-floor check every 1s).");
    }
}
