package com.mcplugin.core1;

import org.bukkit.scheduler.BukkitRunnable;

public class ReactorTask extends BukkitRunnable {

    // =========================
    // TICK INTERVALS (in server ticks)
    // =========================
    private static final int PRESSURE_INTERVAL = 100;     // 5s
    private static final int RECIPE_INTERVAL = 100;         // 5s
    private static final int INTENSITY_DOWN_INTERVAL = 20; // 1s
    private static final int INTENSITY_UP_INTERVAL = 60;   // 3s
    private static final int SOUND_INTERVAL = 10;          // 0.5s
    private static final int STRUCTURE_CHECK_INTERVAL = 20; // 1s
    private static final int DISPLAY_UPDATE_INTERVAL = 1;   // every tick (smooth)

    // =========================
    // TICK COUNTERS
    // =========================
    private int tick;

    @Override
    public void run() {

        ReactorManager reactor = ReactorManager.getInstance();

        if (reactor == null) {
            return;
        }

        tick++;

        // =========================
        // MAIN TICK (every tick)
        // =========================
        reactor.tick();
        reactor.tickVisual();
        reactor.tickSmoothDisplay();

        // =========================
        // MELTDOWN COUNTDOWN (every tick when active)
        // =========================
        reactor.tickMeltdownCountdown();

        // =========================
        // SOUND TICK (every 10 ticks)
        // =========================
        if (tick % SOUND_INTERVAL == 0) {
            reactor.tickSound();
        }

        // =========================
        // DISPLAY UPDATE (every tick)
        // =========================
        if (tick % DISPLAY_UPDATE_INTERVAL == 0) {
            reactor.updateDisplays();
        }

        // =========================
        // STRUCTURE CHECK (every 1s)
        // =========================
        if (tick % STRUCTURE_CHECK_INTERVAL == 0) {
            reactor.validateStructure();
        }

        // =========================
        // PRESSURE TICK (every 5s)
        // =========================
        if (tick % PRESSURE_INTERVAL == 0) {
            reactor.tickPressure();
        }

        // =========================
        // RECIPE TICK (every 5s)
        // =========================
        if (tick % RECIPE_INTERVAL == 0) {
            reactor.tickRecipe();
        }

        // =========================
        // INTENSITY DECAY (every 1s)
        // =========================
        if (tick % INTENSITY_DOWN_INTERVAL == 0) {
            reactor.tickIntensityDown();
        }

        // =========================
        // WEAR TICK (every 1s — накопление износа + чат-таймер)
        // =========================
        if (tick % INTENSITY_DOWN_INTERVAL == 0) {
            reactor.tickWear();
        }

        // =========================
        // INTENSITY RECOVERY (every 3s)
        // =========================
        if (tick % INTENSITY_UP_INTERVAL == 0) {
            reactor.tickIntensityUp();
        }
    }
}
