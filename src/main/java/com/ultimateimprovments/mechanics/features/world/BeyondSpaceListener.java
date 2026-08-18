package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * Beyond Space — grants the {@code ui:datapack/beyond_space} achievement
 * when a player reaches the block placement limit (the top of the buildable
 * world, taken from the dimension settings via {@link org.bukkit.World#getMaxHeight()}).
 * <p>
 * A cheap periodic sweep (no per-move overhead) checks every online player and
 * awards the achievement once — {@code awardCriteria} is idempotent and the
 * progress is skipped once done.
 */
public final class BeyondSpaceListener {

    /** The datapack advancement key (parent: {@code ui:datapack/start}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/beyond_space");

    /** Sweep interval: 20 ticks (1s) — cheap enough to not load the server. */
    private static final long SWEEP_INTERVAL_TICKS = 20L;

    private BeyondSpaceListener() {}

    /** One sweep tick: grant the achievement to every player at the height limit. */
    private static void sweep() {
        Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
        if (adv == null) return; // datapack not loaded yet

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var progress = player.getAdvancementProgress(adv);
                if (progress.isDone()) continue;

                // The highest placeable block is at getMaxHeight() - 1 (the world
                // ceiling, e.g. y=2031 for min_y -2032 / height 4064).
                double y = player.getLocation().getY();
                int topBlockY = player.getWorld().getMaxHeight() - 1;
                if (y >= topBlockY) {
                    progress.awardCriteria("1");
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[BeyondSpace] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Starts the periodic height-limit sweep.
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, BeyondSpaceListener::sweep,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[BeyondSpace] Listener registered (height-limit check every 1s).");
    }
}
