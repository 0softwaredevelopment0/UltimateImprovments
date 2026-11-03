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
     * Cost = ceil(baseCost × growth^(radiusUpgradeCount)), clamped to int.
     * <p>
     * {@code growth} is {@link ProtectionConfig#getPointsGrowthMultiplier()} (1.1 by default):
     * a gentle curve instead of the old «×2 per click», which priced the second upgrade
     * out of reach after only a couple of purchases.
     */
    public int getRadiusUpgradeCost() {
        return scaledCost(ProtectionConfig.getRadiusUpgradeBaseCost(), radiusUpgradeCount);
    }

    /**
     * Current point cost for the next integrity repair.
     * Cost = ceil(baseCost × growth^(repairCount)), clamped to int.
     * <p>
     * Repairs are the frequent action (one click = +1 % integrity), so the same
     * gentle growth as for the radius keeps a long-lived block maintainable
     * while still charging more for every extra percent.
     */
    public int getRepairCost() {
        return scaledCost(ProtectionConfig.getRepairBaseCost(), repairCount);
    }

    private static int scaledCost(int baseCost, int n) {
        return scaledCost(baseCost, n, ProtectionConfig.getPointsGrowthMultiplier());
    }

    /**
     * Ceil-scaled price: {@code ceil(baseCost × growth^n)}, clamped to {@link Integer#MAX_VALUE}.
     * <p>
     * Static and multiplier-explicit so it can be unit-tested without a running server.
     * {@code growth <= 1} (or NaN from a broken TOML) means a flat price, so a typo
     * in the config can never produce a negative or infinite cost.
     */
    static int scaledCost(int baseCost, int n, double growth) {
        if (baseCost <= 0) return Integer.MAX_VALUE;
        if (!(growth > 1.0) || n <= 0) return baseCost;
        double raw = baseCost * Math.pow(growth, n);
        if (!Double.isFinite(raw) || raw >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.ceil(raw);
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
