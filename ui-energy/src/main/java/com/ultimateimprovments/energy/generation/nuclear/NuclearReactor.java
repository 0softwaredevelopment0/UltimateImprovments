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
 * The reactor burns fuel only while redstone-powered, the structure is intact
 * and the network actually accepts the energy. It also carries a thermal
 * model: a temperature in {@code [tempMin .. max]} (the lower limit is rolled
 * from the build-site biome, like the DFC), a cooling buffer fed by ice blocks
 * in the barrel, and a meltdown countdown once the maximum temperature is
 * reached.
 */
public class NuclearReactor {

    /** Current operating state, used both by the tick and the frame status. */
    public enum Status {
        RUNNING,
        UNPOWERED,
        NO_FUEL,
        NO_CABLE,
        OUTPUT_BLOCKED,
        DAMAGED,
        MELTDOWN
    }

    private final Location anchor;
    private int fuelTicksLeft;
    private Status status = Status.UNPOWERED;

    // =========================
    // THERMAL STATE (persisted in the nuclear_reactors table)
    // =========================

    /** Current temperature in degrees (whole numbers). */
    private int temperature;

    /** Dynamic lower temperature limit rolled from the build-site biome. */
    private int tempMin;

    /** Humidity multiplier scaling the passive cooling ({@code -1} = not rolled). */
    private double naturalCoolMult = -1.0;

    /** Cooling buffer in percent ({@code 0 .. cooling_max}). */
    private int cooling;

    /** Seconds accumulated toward the next 1% cooling drain. */
    private int coolDrainSeconds;

    /** Fractional passive-cooling carry (temperature has no fractions). */
    private double coolRemainder;

    /** Remaining meltdown seconds; {@code 0} = no meltdown. */
    private int meltdownSeconds;

    /** Structure damage flag — drives the "damaged / restored" broadcasts. */
    private boolean damaged;

    /** {@code false} until the biome limits were rolled (legacy DB rows). */
    private boolean thermalReady;

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
    // THERMAL STATE
    // =========================

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = Math.max(0, temperature);
    }

    public int getTempMin() {
        return tempMin;
    }

    public void setTempMin(int tempMin) {
        this.tempMin = Math.max(0, tempMin);
    }

    public double getNaturalCoolMult() {
        return naturalCoolMult;
    }

    public void setNaturalCoolMult(double mult) {
        naturalCoolMult = mult;
    }

    public int getCooling() {
        return cooling;
    }

    public void setCooling(int cooling) {
        this.cooling = Math.max(0, cooling);
    }

    public int getCoolDrainSeconds() {
        return coolDrainSeconds;
    }

    public void setCoolDrainSeconds(int seconds) {
        coolDrainSeconds = Math.max(0, seconds);
    }

    public double getCoolRemainder() {
        return coolRemainder;
    }

    public void setCoolRemainder(double remainder) {
        coolRemainder = remainder;
    }

    public int getMeltdownSeconds() {
        return meltdownSeconds;
    }

    public void setMeltdownSeconds(int seconds) {
        meltdownSeconds = Math.max(0, seconds);
    }

    public boolean isMeltdown() {
        return meltdownSeconds > 0;
    }

    public boolean isDamaged() {
        return damaged;
    }

    public void setDamaged(boolean damaged) {
        this.damaged = damaged;
    }

    public boolean isThermalReady() {
        return thermalReady;
    }

    public void setThermalReady(boolean ready) {
        thermalReady = ready;
    }

    // =========================
    // BARREL CONTENT
    // =========================

    /** Number of matching items currently stored in the barrel. */
    public int countItem(Material material) {
        if (material == null) return 0;
        Inventory inv = barrelInventory();
        if (inv == null) return 0;

        int total = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Takes exactly one item of {@code material} from the barrel.
     *
     * @return {@code false} when the barrel is missing or holds no such item
     */
    public boolean takeOne(Material material) {
        if (material == null) return false;
        Inventory inv = barrelInventory();
        if (inv == null) return false;

        ItemStack one = new ItemStack(material, 1);
        if (!inv.containsAtLeast(one, 1)) return false;

        inv.removeItem(one);
        return true;
    }

    /**
     * Takes exactly one fuel item from the barrel and starts a fresh fuel
     * cycle.
     *
     * @return {@code false} when the barrel is missing or holds no fuel
     */
    public boolean takeFuelItem(Material fuel, int ticksPerItem) {
        if (!takeOne(fuel)) return false;
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
