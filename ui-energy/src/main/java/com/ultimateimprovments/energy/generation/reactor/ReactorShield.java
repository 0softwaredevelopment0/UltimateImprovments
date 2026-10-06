package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

/**
 * DFC shield system — the protective field around the core.
 * <p>
 * States: {@code OFFLINE} → (startup pulse) → {@code CREATING} (integrity
 * builds smoothly) → {@code WORKING} (core ignited, lasers operational) →
 * on integrity 0 → 10s countdown → primed creeper detonation.
 * A controlled shutdown (stall procedure) moves WORKING → {@code SHUTDOWN}:
 * the integrity ramps down smoothly at the forming rate and the shield ends
 * back in OFFLINE — a planned shutdown can never detonate.
 * <p>
 * Stress sources (each contributes its own %, they simply add up):
 * <ul>
 *   <li>temperature: +1% per {@code shield_stress_heat_per} C* (400 000 default)</li>
 *   <li>pressure: +1% per {@code shield_stress_press_per} MPa (0.5 default)</li>
 *   <li>spin: +1% per {@code shield_stress_spin_per} RPS (35 000 default)</li>
 * </ul>
 * Above 100% total stress the shield degrades at {@code (stress/100) × base}
 * %/sec — with the default base of 1/3 %/s that is 1%/3s at 100% over-stress,
 * 1%/1.5s at 200%, and 1% per tick at 6000%.
 * <p>
 * While the stress is within limits the shield passively self-repairs at
 * 1% every {@code shield_recovery_every_sec} seconds (5s default).
 * <p>
 * While the lasers fire, vector END_ROD particles travel from the lightning
 * rods of the core column into the core — side beams follow Power Laser #1/#2,
 * the upper ring follows the Stabilizer; the Content Absorber sucks ELECTRIC_SPARK
 * particles from the core into the floor barrel, scaled by the valve opening.
 * DUST particles inside the core follow the black → red → orange → yellow →
 * white gradient as the temperature rises from 0 to the 10M working point.
 */
public class ReactorShield {

    public enum State { OFFLINE, CREATING, WORKING, FAILED, SHUTDOWN }

    private final ReactorManager reactor;

    private State state = State.OFFLINE;
    private double integrity;          // 0..100 %
    private double stressHeat;         // % from temperature
    private double stressPress;        // % from pressure
    private double stressSpin;         // % from spin
    private double decayRemainder;     // fractional degradation accumulator
    private int recoveryTick;          // passive recovery counter (ticks)
    private int failCountdown;         // ticks until detonation (0 = none)

    // =========================
    // BEAM ROD OFFSETS (relative to the anchor) — the waxed lightning rods of
    // the core column; each group of beams is gated by its own laser power.
    // Parsed from darkfusionreactor.nbt:
    //   side power-laser rods: (3,4,4) (7,4,4)   → rel (−2,−5,0) (2,−5,0)
    //   stabilizer ring rods:  (4,6,4) (5,6,3) (5,6,5) (6,6,4) → rel (±1/0,−3,0/±1)
    // The low center rod (0,−7,0) has NO beam — it is the Content Absorber
    // zone (suction particles flow from the core into the floor barrel).
    // =========================
    private static final int[][] POWER_RODS = { { -2, -5, 0 }, { 2, -5, 0 } };
    private static final int[][] STAB_RODS = { { -1, -3, 0 }, { 0, -3, -1 }, { 0, -3, 1 }, { 1, -3, 0 } };
    /** Content Absorber inlet — the floor barrel the content is sucked into. */
    private static final int[] ABSORBER_BARREL = { 0, -9, 0 };
    /** Suction particle count at a fully open (100%) absorber valve, per tick. */
    private static final int ABSORBER_PARTICLES_FULL = 10;

    public ReactorShield(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // SHIELD TICK (every server tick)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();

        switch (state) {
            case OFFLINE -> { /* nothing until startup */ }

            case CREATING -> {
                // Smooth build-up to 100%
                integrity = Math.min(100, integrity + cfg.getShieldBuildRate() / 20.0);
                if (integrity >= 100) {
                    state = State.WORKING;
                    ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                            "shield_working", "<green>✔ <yellow>Shield formed! The core ignites, lasers activate."));
                }
            }

            case WORKING -> {
                updateStress();
                if (getTotalStress() > 100) {
                    // Degradation: (stress/100) × base %/sec — scales up to per-tick speeds
                    double ratePerTick = (getTotalStress() / 100.0)
                            * (cfg.getShieldDecayBase() / 20.0);
                    double v = ratePerTick + decayRemainder;
                    int whole = (int) v;
                    decayRemainder = v - whole;
                    if (whole > 0) {
                        integrity -= whole;
                        if (integrity <= 0) {
                            integrity = 0;
                            startFailure(cfg);
                        }
                    }
                } else {
                    decayRemainder = 0;
                    // Passive recovery: 1% every N seconds (5s default) while the
                    // stress is within limits — the shield self-repairs.
                    recoveryTick++;
                    if (recoveryTick >= cfg.getShieldRecoveryEverySec() * 20) {
                        recoveryTick = 0;
                        if (integrity < 100) {
                            integrity = Math.min(100, integrity + cfg.getShieldRecoveryRate());
                        }
                    }
                }
            }

            case FAILED -> {
                // Silent countdown — the signs show the detonation screen and a
                // single T-10s warning was broadcast at the moment of failure.
                failCountdown--;
                if (failCountdown <= 0) {
                    detonate(base, cfg);
                }
            }

            case SHUTDOWN -> {
                // Controlled shutdown (stall procedure): the integrity drops
                // at a fixed 10%/sec (100% → 0 in ~10s) — no stress, no
                // degradation, no failure countdown. At 0% the shield is
                // simply offline again and the reactor finishes its shutdown.
                double v = integrity - STALL_SHUTDOWN_RATE / 20.0 + shutdownRemainder;
                int whole = (int) Math.floor(v);
                shutdownRemainder = v - whole;
                if (whole > 0) {
                    integrity -= whole;
                    if (integrity <= 0) {
                        integrity = 0;
                        state = State.OFFLINE;
                        reactor.onStallShieldDown();
                    }
                }
            }
        }

        if (state != State.OFFLINE) {
            tickParticles(base);
        }
    }

    /** Shield failure countdown (ticks until the detonation), 0 = not failing. */
    public int getFailCountdown() { return Math.max(0, failCountdown); }

    /** Restores the persisted detonation countdown (used by DB load). */
    public void restoreFailCountdown(int ticks) { failCountdown = Math.max(0, ticks); }

    /** Whether the shield is in the failure (detonation countdown) state. */
    public boolean isFailed() { return state == State.FAILED; }

    // =========================
    // STARTUP — called by the laser system on the startup pulse
    // =========================
    public void start() {
        if (state != State.OFFLINE) return;
        state = State.CREATING;
        integrity = Math.max(integrity, 1);
        ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                "shield_creating", "<gold>⚡ <yellow>Forming the shield..."));
    }

    // =========================
    // OVERPOWER DAMAGE — self-destruct finale: the lasers run at 1000% and
    // burn the shield directly (independent of the stress model).
    // Fractional parts accumulate so odd per-tick rates still add up exactly.
    // =========================
    private double overpowerRemainder;

    public void applyOverpowerDamage(double perTick) {
        if (state != State.WORKING && state != State.CREATING) return;
        double v = integrity - perTick + overpowerRemainder;
        int whole = (int) Math.floor(v);
        overpowerRemainder = v - whole;
        if (whole > 0) {
            integrity -= whole;
            if (integrity <= 0) {
                integrity = 0;
                startFailure(ReactorConfig.getInstance());
            }
        }
    }

    // =========================
    // CONTROLLED SHUTDOWN — stall procedure: the shield ramps down at a fixed
    // 10%/sec and ends back OFFLINE. Fractional parts accumulate so odd
    // per-tick rates still add up exactly.
    // =========================
    /** Shield integrity loss during the controlled shutdown, %/sec. */
    private static final double STALL_SHUTDOWN_RATE = 10.0;
    private double shutdownRemainder;

    /** Enters the smooth controlled shutdown (stall procedure). */
    public void beginShutdown() {
        if (state == State.OFFLINE || state == State.FAILED) return;
        state = State.SHUTDOWN;
        decayRemainder = 0;
    }

    // =========================
    // STRESS MODEL
    // =========================
    private void updateStress() {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // Each source contributes its own %, they simply add up:
        // temperature — 1% per shield_stress_heat_per C* (400 000 default → 25% at 10M)
        // pressure    — 1% per shield_stress_press_per MPa (0.5 default → 20% at 10.01 MPa)
        // spin        — 1% per shield_stress_spin_per RPS (35 000 default → 2.7% at 95 000)
        stressHeat = Math.max(0, reactor.getCoreTemp()) / cfg.getShieldStressHeatPer();
        stressPress = Math.max(0, reactor.getShieldPress()) / cfg.getShieldStressPressPer();
        stressSpin = Math.max(0, reactor.getCoreSpin()) / cfg.getShieldStressSpinPer();
    }

    /** Total stress % — the three sources simply add up. */
    public double getTotalStress() {
        return Math.max(0, stressHeat) + Math.max(0, stressPress) + Math.max(0, stressSpin);
    }

    // =========================
    // PARTICLES — gated laser beams + absorber suction flow
    // =========================
    private void tickParticles(Location base) {
        var lasers = reactor.getLasers();
        double p1 = lasers.getPower(ReactorLasers.LASER_P1);
        double p2 = lasers.getPower(ReactorLasers.LASER_P2);
        double stab = lasers.getPower(ReactorLasers.LASER_STAB);
        double valve = lasers.getPower(ReactorLasers.LASER_ABSORBER);
        boolean anyLaser = p1 > 0 || p2 > 0 || stab > 0;
        // Stall shutdown: the reactor is powering down — all laser effects off
        if (state != State.WORKING || reactor.isStallShutdownActive() || (!anyLaser && valve <= 0)) return;

        ReactorConfig cfg = ReactorConfig.getInstance();

        // Core chamber center (anchor-relative): (0.5, −4.5, 0.5) — the
        // geometric middle of the core column, straight from the NBT template.
        Location core = base.clone().add(0.5, -4.5, 0.5);

        // =========================
        // END_ROD beams — vector particles from a rod tip toward the core
        // (count = 0 → (dx,dy,dz) act as the velocity vector). Each group of
        // beams is only drawn while its laser has power (> 0).
        // =========================
        if (anyLaser) {
            double speed = cfg.getShieldParticleRodSpeed();
            int total = cfg.getShieldParticleRodCount();
            int perBeam = Math.max(1, total / (POWER_RODS.length + STAB_RODS.length));

            // Side lasers: west beam = Power Laser #1, east beam = Power Laser #2
            spawnBeamGroup(base, core, POWER_RODS[0], perBeam, speed, p1 > 0);
            spawnBeamGroup(base, core, POWER_RODS[1], perBeam, speed, p2 > 0);
            // Stabilizer ring: 4 beams, on while the cooler has any power
            for (int[] rod : STAB_RODS) {
                spawnBeamGroup(base, core, rod, perBeam, speed, stab > 0);
            }
        }

        // =========================
        // CONTENT ABSORBER — suction flow from the core down into the floor
        // barrel. Particle count scales with the valve opening: 100% → 10,
        // −25% of valve → −25% of particles (75% → 7, 50% → 5, 25% → 2),
        // closed valve → nothing.
        // =========================
        if (valve > 0) {
            int count = (int) (ABSORBER_PARTICLES_FULL * valve / 100.0);
            if (count > 0) {
                Location barrel = base.clone().add(ABSORBER_BARREL[0] + 0.5, ABSORBER_BARREL[1] + 1.0,
                        ABSORBER_BARREL[2] + 0.5);
                Vector dir = barrel.toVector().subtract(core.toVector());
                double len = dir.length();
                if (len > 0.1) {
                    dir.multiply(1.0 / len);
                    base.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, core, count,
                            dir.getX(), dir.getY(), dir.getZ(), 0.4);
                }
            }
        }

        // =========================
        // DUST inside the core — dense cloud, color follows the temperature:
        // black (0) → red → orange → yellow → white (10M working point).
        // Offsets ±0.25 — a compact cloud; count is unchanged.
        // =========================
        if (anyLaser) {
            Particle.DustOptions dust = new Particle.DustOptions(dustColor(reactor.getCoreTemp()), 1.25f);
            base.getWorld().spawnParticle(Particle.DUST, core,
                    cfg.getShieldParticleDustCount(), 0.25, 0.25, 0.25, 0, dust);
        }
    }

    /** Spawns {@code count} END_ROD particles from a rod tip toward the core. */
    private void spawnBeamGroup(Location base, Location core, int[] rod, int count, double speed, boolean active) {
        if (!active) return;
        Location tip = base.clone().add(rod[0] + 0.5, rod[1] + 0.5, rod[2] + 0.5);
        Vector dir = core.toVector().subtract(tip.toVector());
        double len = dir.length();
        if (len < 0.1) return;
        dir.multiply(1.0 / len);
        Location start = tip.clone().add(dir.clone().multiply(0.6));
        for (int i = 0; i < count; i++) {
            base.getWorld().spawnParticle(Particle.END_ROD, start, 0,
                    dir.getX(), dir.getY(), dir.getZ(), speed);
        }
    }

    /**
     * Gradient color for the core dust: temperature 0 → black, the 10M working
     * point → white; stops at black / red / orange / yellow / white.
     */
    static Color dustColor(int temp) {
        double workTemp = ReactorManager.getInstance().getCoreWorkTemp();
        if (workTemp <= 0) workTemp = 10_000_000;
        double t = Math.max(0, Math.min(1, temp / workTemp));

        float[][] stops = {
                { 0.0f, 0, 0, 0 },          // black
                { 0.25f, 255, 0, 0 },       // red
                { 0.5f, 255, 165, 0 },      // orange
                { 0.75f, 255, 255, 0 },     // yellow
                { 1.0f, 255, 255, 255 }     // white
        };
        for (int i = 0; i < stops.length - 1; i++) {
            if (t <= stops[i + 1][0]) {
                double f = (t - stops[i][0]) / (stops[i + 1][0] - stops[i][0]);
                int r = (int) Math.round(stops[i][1] + (stops[i + 1][1] - stops[i][1]) * f);
                int g = (int) Math.round(stops[i][2] + (stops[i + 1][2] - stops[i][2]) * f);
                int b = (int) Math.round(stops[i][3] + (stops[i + 1][3] - stops[i][3]) * f);
                return Color.fromRGB(r, g, b);
            }
        }
        return Color.WHITE;
    }

    // =========================
    // FAILURE & DETONATION
    // =========================
    private void startFailure(ReactorConfig cfg) {
        state = State.FAILED;
        failCountdown = cfg.getShieldFailureCountdown() * 20;
        ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                "shield_failure",
                "<dark_red>Danger! <white>Core shield has been compromised, core detonation estimated in T-10s, good luck."));
    }

    /** Shield breach detonation: primed creeper (fuse 0) + radius from config. */
    private void detonate(Location base, ReactorConfig cfg) {
        Location core = base.clone().add(0.5, -5.5, 0.5);

        core.getWorld().spawn(core, org.bukkit.entity.Creeper.class, creeper -> {
            creeper.setExplosionRadius(cfg.getShieldExplosionRadius());
            creeper.setMaxFuseTicks(0);
            creeper.setIgnited(true);
        });
        core.getWorld().strikeLightning(core);
        core.getWorld().playSound(core, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE,
                org.bukkit.SoundCategory.MASTER, 3.0f, 0.6f);

        ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                "shield_detonated", "<dark_red>☠ <red>Shield detonation! Reactor destroyed."));

        reactor.onShieldDetonated();
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        state = State.OFFLINE;
        integrity = 0;
        stressHeat = 0;
        stressPress = 0;
        stressSpin = 0;
        decayRemainder = 0;
        overpowerRemainder = 0;
        shutdownRemainder = 0;
        recoveryTick = 0;
        failCountdown = 0;
    }

    // =========================
    // GETTERS / SETTERS
    // =========================
    public State getState() { return state; }
    public void setState(State val) { state = val; }

    public double getIntegrity() { return integrity; }
    public void setIntegrity(double val) { integrity = Math.max(0, Math.min(100, val)); }

    public double getStressHeat() { return stressHeat; }
    public double getStressPress() { return stressPress; }
    public double getStressSpin() { return stressSpin; }

    /** Localized status text for the Shield Stats sign. */
    public String statusText() {
        return switch (state) {
            case CREATING -> StructuresMessages.get("signs.status_creating", "Creating");
            case WORKING -> StructuresMessages.get("signs.status_working", "Working");
            case FAILED -> StructuresMessages.get("signs.status_failed", "Failed");
            case SHUTDOWN -> StructuresMessages.get("signs.status_shutting_down", "Shutting down");
            case OFFLINE -> StructuresMessages.get("signs.status_offline", "Offline");
        };
    }
}
