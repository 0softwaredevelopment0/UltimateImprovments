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
 * While the lasers fire, vector END_ROD particles travel from each lightning
 * rod of the core column into the core; DUST particles inside the core follow
 * the black → red → orange → yellow → white gradient as the temperature rises
 * from 0 to the 10M working point.
 */
public class ReactorShield {

    public enum State { OFFLINE, CREATING, WORKING, FAILED }

    private final ReactorManager reactor;

    private State state = State.OFFLINE;
    private double integrity;          // 0..100 %
    private double stressHeat;         // % from temperature
    private double stressPress;        // % from pressure
    private double stressSpin;         // % from spin
    private double decayRemainder;     // fractional degradation accumulator
    private int failCountdown;         // ticks until detonation (0 = none)

    // =========================
    // ROD OFFSETS (relative to the anchor) — the waxed lightning rods of the
    // core column; laser beams travel from these into the core.
    // Parsed from darkfusionreactor.nbt: (5,2,4) (3,4,4) (7,4,4) (4,6,4) (5,6,3) (5,6,5) (6,6,4)
    // =========================
    private static final int[][] RODS = {
            { 0, -7, 0 }, { -2, -5, 0 }, { 2, -5, 0 },
            { -1, -3, 0 }, { 0, -3, -1 }, { 0, -3, 1 }, { 1, -3, 0 }
    };

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
                            "shield_working", "<green>✔ <yellow>Щит сформирован! Ядро зажигается, лазеры активируются."));
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
                }
            }

            case FAILED -> {
                failCountdown--;
                if (failCountdown > 0 && failCountdown % 20 == 0) {
                    ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                            "shield_failure_countdown",
                            "<dark_red>☠ <red>Отказ щита! <white>%sec%<red> сек до взрыва...")
                            .replace("%sec%", String.valueOf(failCountdown / 20)));
                }
                if (failCountdown <= 0) {
                    detonate(base, cfg);
                }
            }
        }

        if (state != State.OFFLINE) {
            tickParticles(base);
        }
    }

    // =========================
    // STARTUP — called by the laser system on the startup pulse
    // =========================
    public void start() {
        if (state != State.OFFLINE) return;
        state = State.CREATING;
        integrity = Math.max(integrity, 1);
        ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                "shield_creating", "<gold>⚡ <yellow>Формирование щита..."));
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
    // PARTICLES — vector laser beams from the rods into the core
    // =========================
    private void tickParticles(Location base) {
        var lasers = reactor.getLasers();
        boolean lasersActive = state == State.WORKING
                && (lasers.getPower(ReactorLasers.LASER_P1) > 0
                    || lasers.getPower(ReactorLasers.LASER_P2) > 0
                    || lasers.getPower(ReactorLasers.LASER_STAB) > 0);
        if (!lasersActive) return;

        ReactorConfig cfg = ReactorConfig.getInstance();

        // Core chamber center (anchor-relative): (0.5, −5.5, 0.5)
        Location core = base.clone().add(0.5, -5.5, 0.5);

        // =========================
        // END_ROD beams — from each rod tip toward the core (moderate speed,
        // NORMAL render mode, 16 particles total across the active rods)
        // =========================
        double speed = cfg.getShieldParticleRodSpeed();
        int total = cfg.getShieldParticleRodCount();
        int perRod = Math.max(1, total / RODS.length);
        for (int[] rod : RODS) {
            Location tip = base.clone().add(rod[0] + 0.5, rod[1] + 0.5, rod[2] + 0.5);
            Vector dir = core.toVector().subtract(tip.toVector());
            double len = dir.length();
            if (len < 0.1) continue;
            dir.multiply(1.0 / len);
            Location start = tip.clone().add(dir.clone().multiply(0.6));
            // count = 0 → (dx,dy,dz) act as the velocity vector
            for (int i = 0; i < perRod; i++) {
                base.getWorld().spawnParticle(Particle.END_ROD, start, 0,
                        dir.getX(), dir.getY(), dir.getZ(), speed);
            }
        }

        // =========================
        // DUST inside the core — 16 particles, color follows the temperature:
        // black (0) → red → orange → yellow → white (10M working point)
        // =========================
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(reactor.getCoreTemp()), 1.25f);
        base.getWorld().spawnParticle(Particle.DUST, core,
                cfg.getShieldParticleDustCount(), 0.4, 0.4, 0.4, 0, dust);
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
                "shield_failure", "<dark_red>☠ <red>Щит разрушен! Отказ ядра неизбежен."));
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
                "shield_detonated", "<dark_red>☠ <red>Взрыв щита! Реактор уничтожен."));

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
            case OFFLINE -> StructuresMessages.get("signs.status_offline", "Offline");
        };
    }
}
