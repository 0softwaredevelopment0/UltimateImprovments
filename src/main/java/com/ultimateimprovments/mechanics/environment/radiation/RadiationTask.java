package com.ultimateimprovments.mechanics.environment.radiation;

import org.bukkit.scheduler.BukkitRunnable;

public class RadiationTask extends BukkitRunnable {

    private int tick;

    @Override
    public void run() {
        RadiationManager rad = RadiationManager.getInstance();
        if (rad == null) return;

        tick++;

        // Главный тик радиации (каждые 20 тиков = 1 сек)
        // естественный спад, древние обломки, биомы, дозиметр
        if (tick % 20 == 0) {
            rad.tick();
        }

        // Эффекты радиации (каждые 10 тиков = 0.5 сек, как в датапаке)
        if (tick % 10 == 0) {
            rad.tickEffects();
        }
    }
}
