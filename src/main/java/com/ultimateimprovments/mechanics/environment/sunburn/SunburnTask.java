package com.ultimateimprovments.mechanics.environment.sunburn;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * ☀️ SunburnTask — calls SunburnManager.tick() every tick.
 * <p>
 * Registered from TaskManager on startup.
 * The manager itself handles the interval logic (damage every N ticks).
 */
public class SunburnTask extends BukkitRunnable {

    @Override
    public void run() {
        SunburnManager manager = SunburnManager.getInstance();
        if (manager != null) {
            manager.tick();
        }
    }
}
