package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Location;

/**
 * DFC laser system - roof controls of the Dark Fusion Reactor.
 * <p>
 * 9 roof lamps (waxed copper bulbs) with lever+sign pairs. A laser ramps its
 * power by {@code laser_ramp_rate} percent/sec <b>while</b> redstone is applied to
 * its +5%/-5% lamp (powered state, not a pulse). The Core Startup lamp is the
 * exception: it is pulse-triggered - a rising redstone edge activates the reactor.
 * <ul>
 *   <li><b>Power Laser #1/#2</b> - heat the core at {@code power_laser_heat_rate}
 *       C* per sec each at 100% power; range 0..100%</li>
 *   <li><b>Stab. Laser</b> - cools the core at {@code stab_cool_rate} C* per sec
 *       per 100% of power; range 0..200% (200% = 2x power)</li>
 *   <li><b>Content Absorber</b> - valve opening 0..100% (fuel system, later)</li>
 * </ul>
 * Nothing is active before startup. Heating/cooling is applied smoothly every
 * tick (rate/20 per tick with a fractional remainder).
 */
public class ReactorLasers {

    // =========================
    // LAMP OFFSETS (relative to the anchor — frame cell above the central bulb)
    // +5% lamps row (x=−4) and −5% lamps row (x=−2); the startup lamp ends row 1.
    // Order: Power #1, Power #2, Stab, Absorber
    // =========================
    private static final int[][] LAMP_PLUS  = { { -4, 0, -4 }, { -4, 0, -2 }, { -4, 0, 0 }, { -4, 0, 2 } };
    private static final int[][] LAMP_MINUS = { { -2, 0, -4 }, { -2, 0, -2 }, { -2, 0, 0 }, { -2, 0, 2 } };
    private static final int[] LAMP_STARTUP = { -4, 0, 4 };

    /** Laser indices. */
    public static final int LASER_P1 = 0;
    public static final int LASER_P2 = 1;
    public static final int LASER_STAB = 2;
    public static final int LASER_ABSORBER = 3;

    private final ReactorManager reactor;

    private boolean started;
    private boolean prevStartupPowered;

    /** Laser powers in %: P1, P2 (0..100), Stab (0..200), Absorber valve (0..100). */
    private final double[] power = new double[4];

    /** Fractional C* remainder for smooth per-tick heating/cooling. */
    private double tempRemainder;

    public ReactorLasers(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // MAIN LASER TICK (every server tick)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // =========================
        // CORE STARTUP — pulse on the startup lamp (rising redstone edge)
        // =========================
        boolean startupPowered = isLampPowered(base, LAMP_STARTUP);
        if (startupPowered && !prevStartupPowered && !started) {
            started = true;
            ReactorManager.getInstance().broadcastRaw(msg("reactor_startup",
                    "<gold>⚡ <yellow>Стартап ядра выполнен! Лазеры активны."));
        }
        prevStartupPowered = startupPowered;

        if (!started) return;

        // =========================
        // POWER RAMP — ±5%/sec while the +5/−5 lamp is powered
        // =========================
        double rampPerTick = cfg.getLaserRampRate() / 20.0;
        double[] max = { 100, 100, 200, 100 };
        for (int i = 0; i < 4; i++) {
            if (isLampPowered(base, LAMP_PLUS[i])) {
                power[i] = Math.min(max[i], power[i] + rampPerTick);
            }
            if (isLampPowered(base, LAMP_MINUS[i])) {
                power[i] = Math.max(0, power[i] - rampPerTick);
            }
        }

        // =========================
        // HEATING / COOLING — smooth, every tick
        // Power Lasers: power_laser_heat_rate C*/sec each at 100%
        // Stab Laser: stab_cool_rate C*/sec per 100% of power
        // =========================
        double heatPerTick = (power[LASER_P1] + power[LASER_P2]) / 100.0
                * cfg.getPowerLaserHeatRate() / 20.0;
        double coolPerTick = power[LASER_STAB] / 100.0
                * cfg.getStabCoolRate() / 20.0;

        double delta = heatPerTick - coolPerTick + tempRemainder;
        int intPart = (int) delta;
        tempRemainder = delta - intPart;
        if (intPart != 0) {
            reactor.applyCoreTempDelta(intPart);
        }
    }

    // =========================
    // HELPERS
    // =========================
    private boolean isLampPowered(Location base, int[] off) {
        return ReactorManager.getInstance().isBulbPoweredAt(base, off[0], off[1], off[2]);
    }

    private static String msg(String key, String def) {
        return StructuresMessages.get(key, def);
    }

    /** Resets the laser system (disassemble / meltdown / fresh install). */
    public void reset() {
        started = false;
        prevStartupPowered = false;
        for (int i = 0; i < power.length; i++) power[i] = 0;
        tempRemainder = 0;
    }

    // =========================
    // GETTERS / SETTERS
    // =========================
    public boolean isStarted() { return started; }
    public void setStarted(boolean val) { started = val; }

    public double getPower(int laser) { return power[laser]; }
    /** Sets power, clamped to the laser range (P1/P2/Absorber 0..100, Stab 0..200). */
    public void setPower(int laser, double val) {
        double max = (laser == LASER_STAB) ? 200 : 100;
        power[laser] = Math.max(0, Math.min(max, val));
    }

    public boolean isStartupLampPowered(Location base) {
        return isLampPowered(base, LAMP_STARTUP);
    }

    /** True while any Power Laser has positive power (for broadcast/case-reaction). */
    public boolean isHeating() {
        return power[LASER_P1] > 0 || power[LASER_P2] > 0;
    }

    /** True while the Stab Laser has positive power. */
    public boolean isCooling() {
        return power[LASER_STAB] > 0;
    }

    /** Localized startup broadcast support (delegates to the manager). */
    private void broadcastRaw(String message) {
        ReactorManager.getInstance().broadcastRaw(message);
    }
}
