package com.ultimateimprovments.energy.generation.reactor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.util.StructuresMessages;

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
 * <p>
 * A breached case bleeds the core: while particles remain, they escape
 * through the broken glass at {@code case_breach_leak_per_pct} particles/sec
 * per 1% of broken case glass (END_ROD burst at a random breach cell); a
 * core drained dry loses fusion speed instead —
 * {@code case_breach_speed_penalty} points per 1% broken, floor 0. The
 * effective speed replaces the raw one everywhere (case heating, spawn
 * rate, signs), so the displays simply show the reduced values.
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
    /** Fractional C* remainder for the smooth valve cooling. */
    private double coolRemainder;
    /** Fractional radiation accumulator (rad per tick from the open valve). */
    private double radAccumulator;
    /** One-shot latch: the deposit is failing (floor barrel missing or full). */
    private boolean depositStalled;
    /** Fractional particle leak accumulator (breach escape budget). */
    private double leakRemainder;
    /** Random source for the breach-cell leak visuals. */
    private final java.util.Random random = new java.util.Random();

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

        // =========================
        // 1a. CASE BREACH — fusion particles escape through the broken case
        // glass: while the core still HAS particles, they leak out at
        // breach_leak_per_pct × broken% per second (visual burst at a
        // random breach cell); a drained core gets its fusion throttled
        // instead — speed − breach_speed_penalty × broken% points, floor 0.
        // The effective speed lands in the same field, so the case heating
        // and the signs simply show the reduced values.
        // =========================
        double brokenPct = caseBreachPct();
        if (brokenPct > 0) {
            if (particles > 0) {
                leakRemainder += brokenPct * cfg.getCaseBreachLeakPerPct() / 20.0;
                int leaked = (int) leakRemainder;
                if (leaked > 0) {
                    leakRemainder -= leaked;
                    particles = Math.max(0, particles - leaked);
                    spawnLeakBurst(base);
                }
            } else {
                speedPct = Math.max(0, speedPct - brokenPct * cfg.getCaseBreachSpeedPenalty());
            }
        }

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
        boolean particleFlow = collectedNow > 0 && particles > 0;
        if (particleFlow) {
            int taken = (int) Math.min(collectedNow, particles);
            particles -= taken;
            collected += taken;

            // 4. Every N collected particles → 1 ancient debris into the floor barrel
            int perDebris = Math.max(1, cfg.getFusionDebrisPer());
            while (collected >= perDebris) {
                if (!depositDebris(base)) {
                    // Barrel missing or full — hold the counter, but SAY so once
                    // per stall episode instead of silently freezing at N/N.
                    if (!depositStalled) {
                        depositStalled = true;
                        reactor.broadcastRaw(StructuresMessages.get(
                                "fusion_deposit_stalled",
                                "<dark_gray>│ <dark_red>D.F.C <dark_gray>» <gold>⚠ <white>Fusion output stalled: <red>floor barrel is missing or full<white> — the debris counter is holding."));
                    }
                    break;
                }
                depositStalled = false;
                collected -= perDebris;
                reactor.onFusionDebrisCrafted();
            }
        }

        // =========================
        // 5. VALVE COOLING — the more the Content Absorber valve is open, the
        // more heat it drains from the core: quadratic k × valve%² C*/sec.
        // Heat is carried away by the flowing particles — no flow, no cooling.
        // An open valve vents fusion products out of the containment: radiation
        // leaks around the reactor proportionally to the opening.
        // =========================
        if (absorber > 0 && particleFlow) {
            double coolPerTick = (absorber * absorber / 10000.0)
                    * cfg.getAbsorberCoolRate() / 20.0;
            double delta = -coolPerTick + coolRemainder;
            int intPart = (int) delta;
            coolRemainder = delta - intPart;
            if (intPart != 0) {
                reactor.applyCoreTempDelta(intPart);
            }

            radAccumulator += (absorber / 100.0) * cfg.getAbsorberRadPerSec() / 20.0;
            int rad = (int) radAccumulator;
            if (rad > 0) {
                radAccumulator -= rad;
                RadiationManager.addRadiationNear(base, 5.0, rad);
            }
        } else {
            coolRemainder = 0;
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
        // Geometric center of the core column (matches the shield visuals)
        Location core = base.clone().add(0.5, -4.5, 0.5);
        base.getWorld().spawnParticle(Particle.END_ROD, core, count,
                0.3, 0.3, 0.3, 0.02);
    }

    /**
     * % of the case glass currently broken (0 when intact or untracked) —
     * the breach scale for the particle leak and the fusion speed penalty.
     */
    private double caseBreachPct() {
        Location reactorLoc = reactor.getReactorLocation();
        if (reactorLoc == null || !ReactorDamageTracker.isTracked()) return 0;
        int[] glass = ReactorDamageTracker.cachedCount(reactorLoc, ReactorDamageTracker.Category.GLASS);
        int total = glass[1];
        if (total <= 0) return 0;
        return Math.max(0, total - glass[0]) * 100.0 / total;
    }

    /** Visual: escaped particles stream out through a random breach cell. */
    private void spawnLeakBurst(Location base) {
        int[][] cells = ReactorCase.GLASS;
        if (cells.length == 0 || base.getWorld() == null) return;
        int[] off = cells[random.nextInt(cells.length)];
        Location cell = base.clone().add(off[0] + 0.5, off[1] + 0.5, off[2] + 0.5);
        if (cell.getBlock().getType() != Material.AIR) return; // pane intact — no leak point
        base.getWorld().spawnParticle(Particle.END_ROD, cell, 2, 0.15, 0.15, 0.15, 0.08);
    }

    // =========================
    // FLOOR BARREL — ancient debris deposit
    // =========================
    /**
     * Live floor-barrel state. {@code getState(false)} skips the NBT snapshot
     * (this is queried once per second by the displays and on every deposit),
     * while {@link Barrel#getInventory()} still returns the real inventory —
     * items added to it are applied immediately.
     */
    private Barrel floorBarrel(Location base) {
        org.bukkit.World world = base.getWorld();
        if (world == null) return null;
        Block block = world.getBlockAt(base.getBlockX() + BARREL_FLOOR[0],
                base.getBlockY() + BARREL_FLOOR[1], base.getBlockZ() + BARREL_FLOOR[2]);
        if (block.getType() != Material.BARREL) return null;
        return block.getState(false) instanceof Barrel barrel ? barrel : null;
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

    /** Fill % of the floor barrel by occupied item slots (0..100). */
    public int floorBarrelFillPct(Location base) {
        Barrel barrel = floorBarrel(base);
        if (barrel == null) return 100;
        int used = 0;
        for (ItemStack item : barrel.getInventory().getContents()) {
            if (item != null && item.getAmount() > 0) used++;
        }
        return used * 100 / 27;
    }

    /**
     * Deposits one ancient debris into the floor barrel. False when there is no space.
     * <p>
     * Note: {@code Barrel.getInventory()} on a block state is the LIVE inventory —
     * items are applied immediately. Calling {@code update()} here would write back
     * the stale snapshot taken before the addition and silently destroy the item.
     */
    private boolean depositDebris(Location base) {
        Barrel barrel = floorBarrel(base);
        if (barrel == null) return false;
        java.util.HashMap<Integer, ItemStack> leftover =
                barrel.getInventory().addItem(new ItemStack(Material.ANCIENT_DEBRIS, 1));
        return leftover.isEmpty();
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        speedPct = 0;
        particles = 0;
        spawnRemainder = 0;
        collected = 0;
        coolRemainder = 0;
        radAccumulator = 0;
        depositStalled = false;
        leakRemainder = 0;
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
