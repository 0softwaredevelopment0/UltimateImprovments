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
 *   <li>{@code points} — points for upgrades (fractional, see {@link #getRepairCost()})</li>
 *   <li>{@code enabled} — whether the block is enabled (protects the area)</li>
 *   <li>{@code radiusUpgradeCount} — how many times the radius was already upgraded (cost = ceil(base × growth^n))</li>
 *   <li>{@code repairCount} — how many times integrity was already repaired (cost = ceil(base × growth^n))</li>
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
    /** Fractional since 1.8.5-beta.16: prices grow smoothly (1.1^n), so whole points
     *  would round the curve away. Persisted as REAL (SQLite keeps reals in the
     *  legacy INTEGER-affinity column, so old databases need no migration). */
    private double points;
    private boolean enabled;

    private int radiusUpgradeCount;
    private int repairCount;

    /** Whitelist: UUIDs of players allowed to interact with the area and open the GUI.
     *  {@link CopyOnWriteArraySet} — because this list is read from async threads
     *  (the dirty-retry task ProtectionManager.saveWhitelist iterates it on retry)
     *  and written from main threads (addToWhitelist from GUI/command). A plain LinkedHashSet
     *  would throw ConcurrentModificationException with this pattern. */
    private final Set<UUID> whitelist = new CopyOnWriteArraySet<>();

    public ProtectionBlock(UUID id, Location loc, UUID owner, int radius, double integrity, double points, boolean enabled) {
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

    public double getPoints() { return points; }
    public void setPoints(double points) { this.points = points; }

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
     * Cost = baseCost × growth^(radiusUpgradeCount) — a fractional value (points are doubles).
     * <p>
     * {@code growth} is {@link ProtectionConfig#getPointsGrowthMultiplier()} (1.1 by default):
     * each purchase gets gradually more expensive instead of the old «×2 per click».
     */
    public double getRadiusUpgradeCost() {
        return scaledCost(ProtectionConfig.getRadiusUpgradeBaseCost(), radiusUpgradeCount);
    }

    /**
     * Current point cost for the next integrity repair.
     * Cost = baseCost × growth^(repairCount) — a fractional value (points are doubles).
     * <p>
     * Repairs are the frequent action (one click = +1 % integrity), so the price must grow
     * gently: 1, 1.1, 1.21, 1.331 ... instead of 1, 2, 4, 8 ...
     */
    public double getRepairCost() {
        return scaledCost(ProtectionConfig.getRepairBaseCost(), repairCount);
    }

    private static double scaledCost(int baseCost, int n) {
        return scaledCost(baseCost, n, ProtectionConfig.getPointsGrowthMultiplier());
    }

    /**
     * Price curve: {@code baseCost × growth^n}, clamped to {@link #MAX_COST}.
     * <p>
     * Deliberately NOT rounded: the whole point of the 1.1 factor is a smooth curve
     * (rounding up turned 1.1, 1.21, 1.33 into 2, 2, 2 and killed the growth).
     * {@code growth <= 1} (or NaN from a broken TOML) means a flat price, so a typo
     * in the config can never produce a negative, NaN or infinite cost.
     * <p>
     * Static and multiplier-explicit so it can be unit-tested without a running server.
     */
    static double scaledCost(int baseCost, int n, double growth) {
        if (baseCost <= 0) return MAX_COST;
        if (!(growth > 1.0) || n <= 0) return baseCost;
        double raw = baseCost * Math.pow(growth, n);
        if (!Double.isFinite(raw) || raw >= MAX_COST) return MAX_COST;
        return raw;
    }

    /** Upper bound of any price — keeps the GUI readable and the spend check finite. */
    static final double MAX_COST = Integer.MAX_VALUE;

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
