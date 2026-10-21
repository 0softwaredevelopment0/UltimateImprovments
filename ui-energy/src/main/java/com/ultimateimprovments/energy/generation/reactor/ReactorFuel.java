package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * DFC fuel system — the two side barrels of the reactor.
 * <p>
 * The west barrel holds <b>gold ingots</b>, the east barrel holds
 * <b>diamonds</b> (named "Golden fuel" / "Diamond fuel" on assembly).
 * Consumption follows the core spin (RPS):
 * <ul>
 *   <li>below {@code fuel_spin_min} — nothing is consumed</li>     *   <li>at the 95 000 RPS working point — {@code fuel_base_rate}% (100%
 *       = 1 gold ingot + 1 diamond per 10s), falling linearly down to
 *       the 1% floor at the minimum spin</li>
 *   <li>between 95k and 100k — smooth dip back to 1%</li>
 *   <li>above 100 000 RPS — consumption grows again: +1% per 10 000 RPS</li>
 * </ul>
 * One fuel unit = 1 gold ingot + 1 diamond (one from each barrel). If either
 * barrel runs dry the spin coasts down (fusion needs fuel to keep the core
 * rotating).
 */
public class ReactorFuel {

    /** Barrel offsets relative to the anchor: west (gold), east (diamond). */
    public static final int[] BARREL_GOLD = { -4, -5, 0 };
    public static final int[] BARREL_DIAMOND = { 4, -5, 0 };

    private final ReactorManager reactor;

    /**
     * How long a fuel-presence check is reused (ticks). The lasers ask for the
     * fuel state on every server tick (tick + isHeating + the Fuel sign), but
     * barrel contents only change on the once-per-second fuel tick or when a
     * player edits a barrel — half a second of staleness is invisible in game
     * and saves a double inventory scan per tick.
     */
    private static final int FUEL_CACHE_TICKS = 10;

    /** Cached {@link #hasFuel(Location)} result and the tick it stays valid until. */
    private boolean fuelCached;
    private int fuelCacheUntilTick = -1;

    /** Current consumption in % (100% = 1 gold + 1 diamond per 10s). */
    private double consumptionPct;
    /** Fractional fuel-unit accumulator for smooth consumption. */
    private double fuelRemainder;

    public ReactorFuel(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // FUEL TICK (every second, from ReactorTask)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();
        double spin = reactor.getCoreSpin();

        consumptionPct = consumptionForSpin(spin, cfg);

        if (consumptionPct <= 0) return;

        if (!hasFuel(base)) {
            // Out of fuel — the spin coasts down (fusion needs fuel)
            double decay = cfg.getFuelNoFuelSpinDecay();
            if (decay > 0 && spin > 0) {
                reactor.applySpinDelta(-decay);
            }
            return;
        }

        // Smooth consumption: 100% = 1 unit per 10s → pct/1000 units per second
        fuelRemainder += consumptionPct / 1000.0;
        while (fuelRemainder >= 1) {
            fuelRemainder -= 1;
            if (!consumeUnit(base)) {
                fuelRemainder = 0;
                return;
            }
        }
    }

    /**
     * Consumption % for the given spin (100% = 1 gold + 1 diamond per 10s):
     * nothing below fuel_spin_min, a linear fall from 100% to the base rate
     * (5%) between spin_min and the working point, a smooth dip to the 1%
     * floor by 100 000 RPS, then +1% per 10 000 RPS of over-spin.
     */
    static double consumptionForSpin(double spin, ReactorConfig cfg) {
        double spinMin = cfg.getFuelSpinMin();          // 1000
        double workSpin = cfg.getFuelWorkSpin();        // 95000
        double overSpin = cfg.getFuelOverSpin();        // 100000
        double base = cfg.getFuelBaseRate();            // 5% at workSpin
        double min = cfg.getFuelMinRate();              // 1 %
        double per10k = cfg.getFuelOverPer10k();        // 1 % per 10k above overSpin

        if (spin < spinMin) return 0;
        if (spin <= workSpin) {
            // Fall from 100% (at spin_min) to the base rate (at the working point)
            double f = (spin - spinMin) / Math.max(1, workSpin - spinMin);
            return base + (100.0 - base) * (1.0 - f);
        }
        if (spin <= overSpin) {
            // Smooth dip back to the 1% floor between workSpin and overSpin
            double f = (spin - workSpin) / Math.max(1, overSpin - workSpin);
            return base + (min - base) * f;
        }
        // Above 100k: 1% + 1% per each full 10 000 RPS
        return min + per10k * ((spin - overSpin) / 10000.0);
    }

    // =========================
    // BARREL ACCESS
    // =========================
    /**
     * Both barrels contain their fuel item (gold ingot / diamond).
     * <p>
     * The result is cached for {@value #FUEL_CACHE_TICKS} ticks: this method is
     * called several times per server tick (heat check, {@code isHeating}, the
     * Fuel sign), and a barrel only changes on the one-second fuel tick or on a
     * player click. The cache is dropped right after a consumption.
     */
    public boolean hasFuel(Location base) {
        int now = Bukkit.getCurrentTick();
        if (now < fuelCacheUntilTick) return fuelCached;

        fuelCached = countInBarrel(base, BARREL_GOLD, Material.GOLD_INGOT) > 0
                && countInBarrel(base, BARREL_DIAMOND, Material.DIAMOND) > 0;
        fuelCacheUntilTick = now + FUEL_CACHE_TICKS;
        return fuelCached;
    }

    /** Drops the cached fuel check (contents changed right now). */
    private void invalidateFuelCache() {
        fuelCacheUntilTick = -1;
    }

    /** Average fill % of both barrels (slot count / 27, capped at 100). */
    public int getFillPercent(Location base) {
        double gold = countInBarrel(base, BARREL_GOLD, Material.GOLD_INGOT) / 27.0;
        double diamond = countInBarrel(base, BARREL_DIAMOND, Material.DIAMOND) / 27.0;
        return (int) Math.round(Math.min(1, (gold + diamond) / 2.0) * 100);
    }

    /** Count of the given material across all slots of a barrel. */
    private int countInBarrel(Location base, int[] off, Material type) {
        Barrel barrel = barrelAt(base, off);
        if (barrel == null) return 0;
        int count = 0;
        for (ItemStack item : barrel.getInventory().getContents()) {
            if (item != null && item.getType() == type) count += item.getAmount();
        }
        return count;
    }

    /** Removes one fuel unit on recipe completion (legacy path, 1 diamond + 1 gold). */
    public void consumeRecipeUnit(Location base) {
        consumeUnit(base);
    }

    /** Removes one fuel unit: 1 gold ingot from the west barrel + 1 diamond from the east. */
    private boolean consumeUnit(Location base) {
        boolean gold = removeOne(base, BARREL_GOLD, Material.GOLD_INGOT);
        boolean diamond = removeOne(base, BARREL_DIAMOND, Material.DIAMOND);
        invalidateFuelCache();
        return gold && diamond;
    }

    private boolean removeOne(Location base, int[] off, Material type) {
        Barrel barrel = barrelAt(base, off);
        if (barrel == null) return false;
        Inventory inv = barrel.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == type) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    inv.setItem(i, item);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Live barrel state at an anchor-relative offset.
     * <p>
     * {@code getState(false)} returns the live block entity wrapper: no NBT
     * snapshot is serialized/copied (a {@code getState()} snapshot costs ~1 ms
     * per call on a container), and {@link Barrel#getInventory()} still points
     * at the real inventory. This runs several times per tick, so the snapshot
     * variant is reserved for writes that need {@code update()}.
     */
    private Barrel barrelAt(Location base, int[] off) {
        World world = base.getWorld();
        if (world == null) return null;
        Block block = world.getBlockAt(base.getBlockX() + off[0],
                base.getBlockY() + off[1], base.getBlockZ() + off[2]);
        if (block.getType() != Material.BARREL) return null;
        return block.getState(false) instanceof Barrel barrel ? barrel : null;
    }

    /** Snapshot barrel state — only for writes applied through {@code update()}. */
    private Barrel snapshotBarrelAt(Location base, int[] off) {
        World world = base.getWorld();
        if (world == null) return null;
        Block block = world.getBlockAt(base.getBlockX() + off[0],
                base.getBlockY() + off[1], base.getBlockZ() + off[2]);
        if (block.getType() != Material.BARREL) return null;
        return block.getState() instanceof Barrel barrel ? barrel : null;
    }

    // =========================
    // NAMING — "Golden fuel" / "Diamond fuel"
    // =========================
    /** Renames both fuel barrels (on assembly; re-asserted if the name is lost). */
    public void ensureNamed(Location base) {
        setBarrelName(base, BARREL_GOLD, "<yellow>Golden fuel");
        setBarrelName(base, BARREL_DIAMOND, "<dark_aqua>Diamond fuel");
    }

    private void setBarrelName(Location base, int[] off, String miniMessage) {
        // Read the current name from the live entity first — in the steady state
        // (the usual case) no snapshot is created at all.
        Barrel live = barrelAt(base, off);
        if (live == null) return;
        net.kyori.adventure.text.Component expected = MessageUtil.parse(miniMessage);
        net.kyori.adventure.text.Component current = live.customName();
        if (current != null && current.equals(expected)) return;

        Barrel snapshot = snapshotBarrelAt(base, off);
        if (snapshot == null) return;
        snapshot.customName(expected);
        snapshot.update();
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        consumptionPct = 0;
        fuelRemainder = 0;
        fuelCached = false;
        invalidateFuelCache();
    }

    // =========================
    // GETTERS
    // =========================
    /** Current consumption % for the Fuel Stats M indicator. */
    public double getConsumptionPct() { return consumptionPct; }
}
