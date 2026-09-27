package com.ultimateimprovments.mechanics.features.player;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * 🛡 TrimEffectsTask — calls {@link TrimEffectsManager#tick()} every
 * {@value #INTERVAL_TICKS} ticks (1 second).
 * <p>
 * The manager itself owns all the per-unit interval logic, so a fixed
 * 1-second heartbeat is enough.
 */
public class TrimEffectsTask extends BukkitRunnable {

    /** Heartbeat: 20 ticks = 1 second. */
    static final long INTERVAL_TICKS = 20L;

    @Override
    public void run() {
        TrimEffectsManager manager = TrimEffectsManager.getInstance();
        if (manager != null) {
            manager.tick();
        }
    }
}
