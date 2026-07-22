package com.ultimateimprovements.energy.transfer.cable;

import com.ultimateimprovements.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
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

    public int getEnergy() { return energy; }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(energy, maxEnergy));
    }

    public void addEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return;
        this.energy = Math.max(0, Math.min(this.energy + amount, maxEnergy));
    }

    public void setMaxEnergy(int maxEnergy) {
        if (maxEnergy > 0) {
            this.maxEnergy = maxEnergy;
            if (this.energy > maxEnergy) this.energy = maxEnergy;
        }
    }

    public int getMaxEnergy() { return maxEnergy; }

    public void removeEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return;
        this.energy -= amount;
        if (this.energy < 0) this.energy = 0;
    }

    public boolean hasEnergy() { return type != NodeType.CABLE && energy > 0; }

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

    @Deprecated
    public Set<Location> getConnections() {
        Set<Location> result = new HashSet<>();
        for (long connKey : connections) {
            result.add(LocationUtil.toLocation(connKey, world));
        }
        return result;
    }

    // =========================
    // ENERGY TRANSFER TRACKING
    // =========================

    /** Добавляет переданную энергию к счётчику за этот тик. */
    public void addTransferred(int amount) {
        if (amount > 0) {
            energyTransferred.addAndGet(amount);
        }
    }

    /** Возвращает и сбрасывает счётчик переданной энергии за тик. */
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
                ", energy=" + energy + "/" + maxEnergy +
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
