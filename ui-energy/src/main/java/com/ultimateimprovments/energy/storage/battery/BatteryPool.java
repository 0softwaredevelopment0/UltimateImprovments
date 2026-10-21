package com.ultimateimprovments.energy.storage.battery;

/**
 * ⚡ Shared energy pool of ONE assembled battery multiblock.
 * <p>
 * A battery cluster is a monolithic structure: every copper-grate block of the
 * cluster is a network access point, but the charge itself lives here, once.
 * All {@code CableNode}s of the structure (see
 * {@link com.ultimateimprovments.energy.transfer.cable.CableNode#bindBatteryPool(BatteryPool)})
 * delegate their energy getters/setters to the same instance, so charging
 * through any face of the battery charges the whole structure and discharging
 * through any face drains the same reserve — there are no per-block values
 * that can drift apart.
 * <p>
 * The cap is the cluster capacity ({@code energy.battery.max_energy × blocks})
 * and is kept in sync by {@code BatteryManager} whenever the structure grows
 * or two clusters merge.
 * <p>
 * Threading: main server thread only — the same rule as the rest of the
 * cable network. No synchronization on purpose.
 */
public final class BatteryPool {

    private int energy;
    private int maxEnergy;

    public BatteryPool(int maxEnergy) {
        this.maxEnergy = Math.max(0, maxEnergy);
    }

    public int getEnergy() { return energy; }

    public int getMaxEnergy() { return maxEnergy; }

    /** Raises/lowers the cap; the stored charge is clamped to the new limit. */
    public void setMaxEnergy(int maxEnergy) {
        this.maxEnergy = Math.max(0, maxEnergy);
        if (energy > this.maxEnergy) energy = this.maxEnergy;
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(energy, maxEnergy));
    }

    /**
     * Adds energy, clamped to the cap. Returns the amount actually accepted
     * (the caller may have asked for more than the free room).
     */
    public int addEnergy(int amount) {
        if (amount <= 0) return 0;
        int before = energy;
        long target = (long) before + amount;
        setEnergy(target > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) target);
        return energy - before;
    }

    /**
     * Removes energy, clamped at zero. Returns the amount actually removed
     * (the caller may have asked for more than the stored charge).
     */
    public int removeEnergy(int amount) {
        if (amount <= 0) return 0;
        int before = energy;
        setEnergy(Math.max(0, before - amount));
        return before - energy;
    }

    public int getFreeSpace() { return Math.max(0, maxEnergy - energy); }

    public boolean isEmpty() { return energy <= 0; }

    public boolean isFull() { return energy >= maxEnergy; }

    @Override
    public String toString() {
        return "BatteryPool{" + energy + "/" + maxEnergy + '}';
    }
}
