package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Runtime state of one assembled compact nuclear reactor.
 * <p>
 * The anchor is the template origin — the top-center fuel barrel. Relative to
 * it the template puts the redstone-controlled reinforced deepslate at
 * {@code (0,−1,0)} and the bottom energy port (waxed chiseled copper) at
 * {@code (0,−2,0)}, so the cable connects directly under the structure.
 * <p>
 * The reactor is intentionally safe: no heat, no meltdown, no radiation. It
 * only burns fuel while redstone-powered, the structure is intact and the
 * network actually accepts the energy.
 */
public class NuclearReactor {

    /** Current operating state, used both by the tick and the frame status. */
    public enum Status {
        RUNNING,
        UNPOWERED,
        NO_FUEL,
        NO_CABLE,
        OUTPUT_BLOCKED,
        DAMAGED
    }

    private final Location anchor;
    private int fuelTicksLeft;
    private Status status = Status.UNPOWERED;

    public NuclearReactor(Location anchor) {
        this.anchor = LocationUtil.normalize(anchor);
    }

    public Location getAnchor() {
        return anchor;
    }

    /** Redstone-controlled reinforced deepslate at {@code (0,−1,0)}. */
    public Location getCenterBlockLocation() {
        return anchor.clone().add(0, -1, 0);
    }

    /** Bottom waxed chiseled copper energy port at {@code (0,−2,0)}. */
    public Location getEnergyPortLocation() {
        return anchor.clone().add(0, -2, 0);
    }

    public Block getBarrelBlock() {
        return anchor.getBlock();
    }

    public Block getCenterBlock() {
        return getCenterBlockLocation().getBlock();
    }

    public Block getEnergyPort() {
        return getEnergyPortLocation().getBlock();
    }

    public int getFuelTicksLeft() {
        return fuelTicksLeft;
    }

    /** Loads one fresh fuel cycle (ticks one item sustains). */
    public void setFuelTicksLeft(int ticks) {
        fuelTicksLeft = Math.max(0, ticks);
    }

    public boolean hasFuel() {
        return fuelTicksLeft > 0;
    }

    /** Consumes up to {@code ticks} of the current fuel cycle. */
    public void burnFuelTicks(int ticks) {
        fuelTicksLeft = Math.max(0, fuelTicksLeft - Math.max(0, ticks));
    }

    public Status getStatus() {
        return status;
    }

    /** @return {@code true} when the status actually changed. */
    public boolean setStatus(Status next) {
        if (status == next) return false;
        status = next;
        return true;
    }

    // =========================
    // FUEL (the barrel above the core)
    // =========================

    /** Number of matching fuel items currently stored in the barrel. */
    public int countFuel(Material fuel) {
        if (fuel == null) return 0;
        Inventory inv = barrelInventory();
        if (inv == null) return 0;

        int total = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.getType() == fuel) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Takes exactly one fuel item from the barrel and starts a fresh fuel
     * cycle.
     *
     * @return {@code false} when the barrel is missing or holds no fuel
     */
    public boolean takeFuelItem(Material fuel, int ticksPerItem) {
        if (fuel == null) return false;
        Inventory inv = barrelInventory();
        if (inv == null) return false;

        ItemStack one = new ItemStack(fuel, 1);
        if (!inv.containsAtLeast(one, 1)) return false;

        inv.removeItem(one);
        setFuelTicksLeft(ticksPerItem);
        return true;
    }

    private Inventory barrelInventory() {
        Block block = getBarrelBlock();
        if (block.getType() != Material.BARREL) return null;

        BlockState state = block.getState();
        if (!(state instanceof Barrel barrel)) return null;
        return barrel.getInventory();
    }

    // =========================
    // CHUNK GUARD
    // =========================

    /**
     * The structure spans up to two chunks horizontally — every one of them
     * must be loaded before the tick reads blocks (getBlockAt would otherwise
     * synchronously load chunks).
     */
    public boolean isChunkLoaded() {
        World world = anchor.getWorld();
        if (world == null) return false;

        int baseX = anchor.getBlockX();
        int baseZ = anchor.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded((baseX + dx) >> 4, (baseZ + dz) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }
}
