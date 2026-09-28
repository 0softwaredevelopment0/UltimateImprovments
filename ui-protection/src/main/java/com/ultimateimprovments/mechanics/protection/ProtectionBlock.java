package com.ultimateimprovments.mechanics.protection;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Data of one placed «Protection Block».
 * <p>
 * Stores:
 * <ul>
 *   <li>{@code id} — unique UUID (used as PK in the DB)</li>
 *   <li>{@code location} — world coordinates of the center</li>
 *   <li>{@code owner} — UUID of the player who placed the block</li>
 *   <li>{@code radius} — current protection radius (blocks)</li>
 *   <li>{@code integrity} — current integrity 0..100</li>
 *   <li>{@code points} — points for upgrades</li>
 *   <li>{@code enabled} — whether the block is enabled (protects the area)</li>
 *   <li>{@code radiusUpgradeCount} — how many times the radius was already upgraded (cost *= 2^(n))</li>
 *   <li>{@code repairCount} — how many times integrity was already repaired (cost *= 2^(n))</li>
 *   <li>{@code whitelist} — UUIDs of players with access to the area and GUI</li>
 * </ul>
 */
public class ProtectionBlock {

    private final UUID id;
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    private UUID owner;
    private int radius;
    private double integrity;
    private int points;
    private boolean enabled;

    private int radiusUpgradeCount;
    private int repairCount;

    /** Whitelist: UUIDs of players allowed to interact with the area and open the GUI.
     *  {@link CopyOnWriteArraySet} — because this list is read from async threads
     *  (the dirty-retry task ProtectionManager.saveWhitelist iterates it on retry)
     *  and written from main threads (addToWhitelist from GUI/command). A plain LinkedHashSet
     *  would throw ConcurrentModificationException with this pattern. */
    private final Set<UUID> whitelist = new CopyOnWriteArraySet<>();

    public ProtectionBlock(UUID id, Location loc, UUID owner, int radius, double integrity, int points, boolean enabled) {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (loc == null || loc.getWorld() == null) throw new IllegalArgumentException("location must have a world");
        this.id = id;
        this.world = loc.getWorld();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
        this.owner = owner;
        this.radius = radius;
        this.integrity = integrity;
        this.points = points;
        this.enabled = enabled;
    }

    public UUID getId() { return id; }
    public World getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public Location getLocation() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public Location getBlockLocation() {
        return new Location(world, x, y, z);
    }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }

    public double getIntegrity() { return integrity; }
    public void setIntegrity(double integrity) { this.integrity = Math.max(0.0, Math.min(100.0, integrity)); }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getRadiusUpgradeCount() { return radiusUpgradeCount; }
    public void setRadiusUpgradeCount(int n) { this.radiusUpgradeCount = n; }

    public int getRepairCount() { return repairCount; }
    public void setRepairCount(int n) { this.repairCount = n; }

    public Set<UUID> getWhitelist() { return whitelist; }

    public void addToWhitelist(UUID playerId) {
        whitelist.add(playerId);
    }

    public void removeFromWhitelist(UUID playerId) {
        whitelist.remove(playerId);
    }

    public boolean isWhitelisted(UUID playerId) {
        return whitelist.contains(playerId);
    }

    /**
     * Current point cost for the next radius upgrade.
     * Cost = baseCost × 2^(radiusUpgradeCount), clamped.
     * <p>
     * O(1) via a bit shift into long, with a shift of at most 30 bits,
     * to avoid overflow even for large baseCost.
     */
    public int getRadiusUpgradeCost() {
        int baseCost = ProtectionConfig.getRadiusUpgradeBaseCost();
        if (baseCost <= 0) return Integer.MAX_VALUE;
        int n = Math.min(radiusUpgradeCount, 30);
        long cost = ((long) baseCost) << n;
        if (cost < 0 || cost > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) cost;
    }

    /**
     * Current point cost for the next integrity repair.
     * Cost = baseCost × 2^(repairCount), clamped.
     * <p>
     * O(1) via a bit shift into long, with a shift of at most 30 bits,
     * to avoid overflow even for large baseCost.
     */
    public int getRepairCost() {
        int baseCost = ProtectionConfig.getRepairBaseCost();
        if (baseCost <= 0) return Integer.MAX_VALUE;
        int n = Math.min(repairCount, 30);
        long cost = ((long) baseCost) << n;
        if (cost < 0 || cost > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) cost;
    }

    /**
     * True if the block has integrity > 0 AND is enabled.
     * <p>
     * IMPORTANT: the semantics are — «alive AND active», strictly as the docstring and callers
     * (findProtectingBlock, applyExplosionProtection, triggerIntruderEffects inside) say.
     * Previously this was only {@code integrity > 0.0}, which created a discrepancy
     * with the rest of the code and led to phantom-accumulation integrity loss on disabled copies.
     */
    public boolean isAlive() {
        return integrity > 0.0 && enabled;
    }

    /** True if the block kept integrity > 0 (regardless of enabled). For HUD/GUI. */
    public boolean hasIntegrity() {
        return integrity > 0.0;
    }
}
