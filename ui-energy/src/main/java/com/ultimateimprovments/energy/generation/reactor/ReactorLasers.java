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

    // =========================
    // OVERPOWER MODE (self-destruct finale): the Power Lasers are forced to
    // 1000% (beyond their normal 100% limit — not a normal situation), control
    // bulbs are locked and the shield burns. Reaching the report stage (shield
    // 0% → detonation countdown) completes the self-destruct sequence.
    // =========================
    private static final int OVERPOWER_RAMP_TICKS = 40; // ~2s ramp 0 → 1000%
    private boolean overpowerMode;
    private int overpowerRampTicks;

    /** Control bulbs locked (self-destruct timed phase) — the ±5% lamps are dead. */
    private boolean controlLocked;
    public void setControlLocked(boolean val) { controlLocked = val; }
    public boolean isControlLocked() { return controlLocked; }

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
                    "<gold>⚡ <yellow>Forming the shield... Lasers activate after."));
            // Shield first: integrity builds up (Creating → Working), lasers become operational then
            ReactorManager.getInstance().onStartupPulse();
        }
        prevStartupPowered = startupPowered;

        if (!started) return;

        // =========================
        // SELF-DESTRUCT OVERPOWER — control bulbs are locked, the Power Lasers
        // ramp to 1000% and burn the shield into the report stage.
        // =========================
        if (overpowerMode) {
            tickOverpower();
            return;
        }

        // Lasers are operational only when the shield is fully formed (WORKING):
        // before that they ramp their power (signs show it) but do not heat/cool.
        if (reactor.getShield().getState() != ReactorShield.State.WORKING) {
            rampOnly(base);
            return;
        }

        // =========================
        // POWER RAMP — ±5%/sec while the +5/−5 lamp is powered.
        // Self-destruct: the control bulbs are locked (dead) — no ramp.
        // =========================
        double rampPerTick = cfg.getLaserRampRate() / 20.0;
        double[] max = { 100, 100, 200, 100 };
        for (int i = 0; i < 4; i++) {
            if (controlLocked) break;
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
        // Without fuel the Power Lasers do not heat at all (Fuel Stats: No)
        // =========================
        double heatPerTick = reactor.hasBarrelFuelPublic()
                ? (power[LASER_P1] + power[LASER_P2]) / 100.0
                        * cfg.getPowerLaserHeatRate() / 20.0
                : 0;
        double coolPerTick = power[LASER_STAB] / 100.0
                * cfg.getStabCoolRate() / 20.0;

        double delta = heatPerTick - coolPerTick + tempRemainder;
        int intPart = (int) delta;
        tempRemainder = delta - intPart;
        if (intPart != 0) {
            reactor.applyCoreTempDelta(intPart);
        }
    }

    /**
     * Cooldown mode (damaged structure): only the Stabilization Laser works —
     * its power lamps stay functional so the core can be cooled to 0 C*.
     * No startup, no Power Lasers, no Absorber.
     */
    public void tickCooldownMode(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();
        double rampPerTick = cfg.getLaserRampRate() / 20.0;

        // Only the Stab Laser ramps (its +/− lamps), everything else drains off
        for (int i = 0; i < power.length; i++) {
            if (i != LASER_STAB) {
                power[i] = 0;
                continue;
            }
            if (isLampPowered(base, LAMP_PLUS[i])) {
                power[i] = Math.min(200, power[i] + rampPerTick);
            }
            if (isLampPowered(base, LAMP_MINUS[i])) {
                power[i] = Math.max(0, power[i] - rampPerTick);
            }
        }

        double coolPerTick = power[LASER_STAB] / 100.0 * cfg.getStabCoolRate() / 20.0;
        double delta = -coolPerTick + tempRemainder;
        int intPart = (int) delta;
        tempRemainder = delta - intPart;
        if (intPart != 0) {
            reactor.applyCoreTempDelta(intPart);
        }
    }

    /**
     * Overpower tick (self-destruct finale): the control bulbs are ignored,
     * Power Laser #1/#2 ramp to 1000% and heat without a fuel check; while the
     * burn phase is active they also damage the shield directly.
     */
    private void tickOverpower() {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // The shield must be formed — during CREATING the lasers still hold off
        if (reactor.getShield().getState() != ReactorShield.State.WORKING) return;

        if (overpowerRampTicks < OVERPOWER_RAMP_TICKS) {
            overpowerRampTicks++;
            power[LASER_P1] = Math.min(1000, power[LASER_P1] + 1000.0 / OVERPOWER_RAMP_TICKS);
            power[LASER_P2] = Math.min(1000, power[LASER_P2] + 1000.0 / OVERPOWER_RAMP_TICKS);
        }

        // 1000% heating — the fuel check is deliberately skipped (not a normal situation)
        double heatPerTick = (power[LASER_P1] + power[LASER_P2]) / 100.0
                * cfg.getPowerLaserHeatRate() / 20.0;
        double delta = heatPerTick + tempRemainder;
        int intPart = (int) delta;
        tempRemainder = delta - intPart;
        if (intPart != 0) {
            reactor.applyCoreTempDelta(intPart);
        }

        // While the burn phase is active the lasers damage the shield directly
        // (independent of the stress model) — report stage in ~10s at the default rate
        if (reactor.isSelfdestructFinale()) {
            reactor.getShield().applyOverpowerDamage(cfg.getSelfdestructOverpowerRate() / 20.0);
        }
    }

    /** Enters the overpower mode (self-destruct finale). Irreversible until reset. */
    public void beginOverpower() {
        overpowerMode = true;
        overpowerRampTicks = 0;
    }

    public boolean isOverpowerMode() { return overpowerMode; }
    private void rampOnly(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();
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
        overpowerMode = false;
        overpowerRampTicks = 0;
        controlLocked = false;
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

    /** True while any Power Laser has positive power and the reactor has fuel. */
    public boolean isHeating() {
        return reactor.hasBarrelFuelPublic()
                && (power[LASER_P1] > 0 || power[LASER_P2] > 0);
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
