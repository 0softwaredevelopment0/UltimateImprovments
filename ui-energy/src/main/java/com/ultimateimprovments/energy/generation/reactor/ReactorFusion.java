package com.ultimateimprovments.energy.generation.reactor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * DFC fusion system — ancient debris forms inside the core.
 * <p>
 * The fusion speed (S) depends on the core temperature:
 * <ul>
 *   <li>below {@code fusion_temp_min} (1M C*) — no fusion at all</li>
 *   <li>1M → 10M C* — linear rise from 0% to 100%</li>
 *   <li>10M → 15M C* — linear rise from 100% to 150%</li>
 *   <li>15M → 25M C* — linear fall back to 0%</li>
 * </ul>
 * 100% speed = {@code fusion_particles_per_sec} particles per second spawned
 * in the core. Each particle is a "fusion particle" (P) that presses on the
 * case (used by the future case-pressure system) and drifts around until the
 * Content Absorber collects it at {@code fusion_absorb_rate} particles/sec at
 * 100% valve power. Every {@code fusion_debris_per} collected particles one
 * ancient debris is deposited into the floor barrel; when that barrel is full
 * the fusion status shows Full and collection pauses.
 */
public class ReactorFusion {

    /** Floor barrel offset relative to the anchor (center of the floor). */
    public static final int[] BARREL_FLOOR = { 0, -9, 0 };

    private final ReactorManager reactor;

    /** Live fusion speed in % (0..150). */
    private double speedPct;
    /** Fusion particles currently floating in the core (P). */
    private double particles;
    /** Fractional particle spawn accumulator (particles/sec budget). */
    private double spawnRemainder;
    /** Collected particles counter toward the next ancient debris. */
    private double collected;

    public ReactorFusion(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // FUSION TICK (every server tick)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // 1. Speed from temperature
        speedPct = speedForTemp(reactor.getCoreTemp(), cfg);

        // 2. Spawn particles (smooth, fractional)
        if (speedPct > 0) {
            double perTick = speedPct / 100.0 * cfg.getFusionParticlesPerSec() / 20.0;
            spawnRemainder += perTick;
            int whole = (int) spawnRemainder;
            if (whole > 0) {
                spawnRemainder -= whole;
                particles += whole;
                spawnParticleBurst(base, Math.min(whole, 4));
            }
        }

        // 3. Content Absorber collects drifting particles
        double absorber = reactor.getLasers().getPower(ReactorLasers.LASER_ABSORBER);
        double collectPerTick = absorber / 100.0 * cfg.getFusionAbsorbRate() / 20.0;
        double collectBudget = collectPerTick + collectRemainder;
        int collectedNow = (int) collectBudget;
        collectRemainder = Math.max(0, collectBudget - collectedNow);
        if (collectedNow > 0 && particles > 0) {
            int taken = (int) Math.min(collectedNow, particles);
            particles -= taken;
            collected += taken;

            // 4. Every N collected particles → 1 ancient debris into the floor barrel
            int perDebris = Math.max(1, cfg.getFusionDebrisPer());
            while (collected >= perDebris) {
                if (!depositDebris(base)) break; // barrel full — hold the counter
                collected -= perDebris;
            }
        }
    }

    /**
     * Fusion speed % for the given temperature:
     * 0 below fusion_temp_min, linear to 100% at the working temp,
     * up to 150% at fusion_temp_peak, back to 0% at fusion_temp_end.
     */
    static double speedForTemp(double temp, ReactorConfig cfg) {
        double min = cfg.getFusionTempMin();     // 1M
        double work = cfg.getFusionTempWork();   // 10M
        double peak = cfg.getFusionTempPeak();   // 15M
        double end = cfg.getFusionTempEnd();     // 25M

        if (temp < min || temp >= end) return 0;
        if (temp < work) return (temp - min) / Math.max(1, work - min) * 100.0;
        if (temp < peak) return 100.0 + (temp - work) / Math.max(1, peak - work) * 50.0;
        return 150.0 * (1.0 - (temp - peak) / Math.max(1, end - peak));
    }

    /** Visual: a small burst of END_ROD particles drifting in the core. */
    private void spawnParticleBurst(Location base, int count) {
        // 2 blocks above the geometric center (matches the shield visuals)
        Location core = base.clone().add(0.5, -3.5, 0.5);
        base.getWorld().spawnParticle(Particle.END_ROD, core, count,
                0.3, 0.3, 0.3, 0.02);
    }

    // =========================
    // FLOOR BARREL — ancient debris deposit
    // =========================
    private Barrel floorBarrel(Location base) {
        Block block = base.clone().add(BARREL_FLOOR[0], BARREL_FLOOR[1], BARREL_FLOOR[2]).getBlock();
        if (block.getState() instanceof Barrel barrel) return barrel;
        return null;
    }

    /** True when the floor barrel has no free slot left (status Full). */
    public boolean isFloorBarrelFull(Location base) {
        Barrel barrel = floorBarrel(base);
        if (barrel == null) return true;
        Inventory inv = barrel.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getAmount() < item.getMaxStackSize()) return false;
        }
        return true;
    }

    /** Deposits one ancient debris into the floor barrel. False when there is no space. */
    private boolean depositDebris(Location base) {
        Barrel barrel = floorBarrel(base);
        if (barrel == null) return false;
        java.util.HashMap<Integer, ItemStack> leftover =
                barrel.getInventory().addItem(new ItemStack(Material.ANCIENT_DEBRIS, 1));
        if (!leftover.isEmpty()) return false;
        barrel.update();
        return true;
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        speedPct = 0;
        particles = 0;
        spawnRemainder = 0;
        collected = 0;
    }

    // =========================
    // GETTERS / SETTERS (persistence)
    // =========================
    /** Current fusion speed % (Fusion Stats S indicator). */
    public double getSpeedPct() { return speedPct; }

    /** Fusion particles floating in the core (Fusion Stats P indicator). */
    public double getParticles() { return particles; }
    public void setParticles(double val) { particles = Math.max(0, val); }

    /** Collected-particles progress toward the next ancient debris. */
    public double getCollected() { return collected; }
    public void setCollected(double val) { collected = Math.max(0, val); }

    private double collectRemainder;
}
