package com.ultimateimprovments.energy.transfer.cable;

import com.ultimateimprovments.energy.storage.battery.BatteryPool;
import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CableNode {

    private final long key;
    private final World world;
    private final int x, y, z;
    private Location location;

    private final Set<Long> connections = ConcurrentHashMap.newKeySet();

    private int energy;
    private NodeType type = NodeType.CABLE;
    private int maxEnergy = 0;

    /**
     * Shared pool of the assembled battery multiblock this block belongs to
     * (null for cables, generators and legacy single-block batteries). When
     * set, every energy accessor below goes to the pool, so the whole
     * structure stores/discharges as ONE battery regardless of which block
     * received the energy.
     */
    private BatteryPool batteryPool;

    // Transfer tracking (how much energy passed through this node per tick)
    private final java.util.concurrent.atomic.AtomicInteger energyTransferred = new java.util.concurrent.atomic.AtomicInteger(0);

    public CableNode(Location location) {
        Location norm = LocationUtil.normalize(location);
        this.world = norm.getWorld();
        this.x = norm.getBlockX();
        this.y = norm.getBlockY();
        this.z = norm.getBlockZ();
        this.key = LocationUtil.toKey(x, y, z);
        this.location = norm;
    }

    public Location getLocation() {
        if (location == null) {
            location = new Location(world, x, y, z);
        }
        return location;
    }

    public long getKey() { return key; }
    public World getWorld() { return world; }
    public int getBlockX() { return x; }
    public int getBlockY() { return y; }
    public int getBlockZ() { return z; }

    public int getEnergy() { return batteryPool != null ? batteryPool.getEnergy() : energy; }

    public void setEnergy(int energy) {
        if (batteryPool != null) {
            batteryPool.setEnergy(energy);
            return;
        }
        this.energy = Math.max(0, Math.min(energy, maxEnergy));
    }

    public void addEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return;
        if (batteryPool != null) {
            batteryPool.addEnergy(amount);
            return;
        }
        this.energy = Math.max(0, Math.min(this.energy + amount, maxEnergy));
    }

    public void setMaxEnergy(int maxEnergy) {
        if (batteryPool != null) {
            // The cap of a multiblock battery is the cluster capacity, not a
            // per-block value — keep the shared pool authoritative.
            batteryPool.setMaxEnergy(maxEnergy);
            return;
        }
        if (maxEnergy > 0) {
            this.maxEnergy = maxEnergy;
            if (this.energy > maxEnergy) this.energy = maxEnergy;
        }
    }

    public int getMaxEnergy() { return batteryPool != null ? batteryPool.getMaxEnergy() : maxEnergy; }

    public void removeEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return;
        if (batteryPool != null) {
            batteryPool.removeEnergy(amount);
            return;
        }
        this.energy -= amount;
        if (this.energy < 0) this.energy = 0;
    }

    /** Shared pool of the multiblock battery this block belongs to, or null. */
    public BatteryPool getBatteryPool() { return batteryPool; }

    /**
     * Binds this node to the shared pool of its battery multiblock (or
     * clears the binding with {@code null}). Every block of the structure
     * must be bound to the SAME instance, otherwise the blocks drift apart
     * again.
     */
    public void bindBatteryPool(BatteryPool pool) {
        this.batteryPool = pool;
        if (pool != null) {
            this.type = NodeType.BATTERY;
        }
    }

    public boolean hasEnergy() { return type != NodeType.CABLE && getEnergy() > 0; }

    public void connect(Location loc) {
        if (loc == null) return;
        long targetKey = LocationUtil.toKey(loc);
        if (targetKey == key) return;
        if (connections.contains(targetKey)) return;
        if (!LocationUtil.isFullyConnected(this.key, targetKey)) return;
        connections.add(targetKey);
    }

    public void connectKey(long targetKey) {
        if (targetKey == key) return;
        if (connections.contains(targetKey)) return;
        if (!LocationUtil.isFullyConnected(this.key, targetKey)) return;
        connections.add(targetKey);
    }

    public void disconnect(Location loc) {
        if (loc == null) return;
        connections.remove(LocationUtil.toKey(loc));
    }

    public void disconnectKey(long targetKey) { connections.remove(targetKey); }

    public boolean isConnected(Location loc) {
        if (loc == null) return false;
        return connections.contains(LocationUtil.toKey(loc));
    }

    public boolean isConnectedTo(long targetKey) { return connections.contains(targetKey); }

    public void clearConnections() { connections.clear(); }

    public Set<Long> getConnectionKeys() { return Collections.unmodifiableSet(connections); }

    // =========================
    // ENERGY TRANSFER TRACKING
    // =========================

    /** Adds the transferred energy to this tick's counter. */
    public void addTransferred(int amount) {
        if (amount > 0) {
            energyTransferred.addAndGet(amount);
        }
    }

    /** Returns and resets the per-tick transferred energy counter. */
    public int getAndResetTransferred() {
        return energyTransferred.getAndSet(0);
    }

    public int getConnectionCount() { return connections.size(); }

    public NodeType getType() { return type; }

    public void setType(NodeType type) {
        if (type == null) return;
        this.type = type;
    }

    @Override
    public String toString() {
        return "CableNode{loc=" + x + "," + y + "," + z +
                ", energy=" + getEnergy() + "/" + getMaxEnergy() +
                ", type=" + type +
                ", connections=" + connections.size() + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CableNode other)) return false;
        return this.key == other.key && Objects.equals(this.world, other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world.getName(), x, y, z);
    }
}
