package com.ultimateimprovments.mechanics.environment.radiation;

import org.bukkit.scheduler.BukkitRunnable;

public class RadiationTask extends BukkitRunnable {

    private int tick;

    @Override
    public void run() {
        RadiationManager rad = RadiationManager.getInstance();
        if (rad == null) return;

        tick++;

        // Main radiation tick (every 20 ticks = 1 sec)
        // natural decay, ancient debris, biomes, dosimeter
        if (tick % 20 == 0) {
            rad.tick();
        }

        // Radiation effects (every 10 ticks = 0.5 sec, like in the datapack)
        if (tick % 10 == 0) {
            rad.tickEffects();
        }
    }
}
