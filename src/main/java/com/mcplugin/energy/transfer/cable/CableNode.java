package com.mcplugin.energy.transfer.cable;

import com.mcplugin.infrastructure.util.LocationUtil;
import org.bukkit.Location;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CableNode {

    private final Location location;
    private final Set<Location> connections = new HashSet<>();

    private int energy;
    private NodeType type = NodeType.CABLE;
    private int maxEnergy = 0; // cables don't store energy; batteries override via config

    public CableNode(Location location) {
        this.location = LocationUtil.normalize(location);
    }

    public Location getLocation() {
        return location;
    }

    // =========================
    // ENERGY (FIXED)
    // =========================
    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        // No type check here — setEnergy is for initialization (DB load, etc.)
        // Runtime energy changes use addEnergy/removeEnergy which enforce type rules.
        this.energy = Math.max(0, Math.min(energy, maxEnergy));
    }

    public void addEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return; // cables only transmit, don't store
        this.energy = Math.max(0, Math.min(this.energy + amount, maxEnergy));
    }

    public void setMaxEnergy(int maxEnergy) {
        if (maxEnergy > 0) {
            this.maxEnergy = maxEnergy;
            if (this.energy > maxEnergy) {
                this.energy = maxEnergy;
            }
        }
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public void removeEnergy(int amount) {
        if (amount <= 0) return;
        if (type == NodeType.CABLE) return; // cables only transmit, don't store

        this.energy -= amount;

        if (this.energy < 0) {
            this.energy = 0;
        }
    }

    public boolean hasEnergy() {
        return type != NodeType.CABLE && energy > 0;
    }

    // =========================
    // CONNECTIONS
    // =========================
    public void connect(Location loc) {

        if (loc == null) return;

        Location target = LocationUtil.normalize(loc);

        if (target.equals(location)) return;

        if (connections.contains(target)) return;

        if (!LocationUtil.isFullyConnected(this.location, target)) return;

        connections.add(target);
    }

    public void disconnect(Location loc) {

        if (loc == null) return;

        connections.remove(LocationUtil.normalize(loc));
    }

    public boolean isConnected(Location loc) {

        if (loc == null) return false;

        return connections.contains(LocationUtil.normalize(loc));
    }

    public void clearConnections() {
        connections.clear();
    }

    public Set<Location> getConnections() {
        return Collections.unmodifiableSet(connections);
    }

    public int getConnectionCount() {
        return connections.size();
    }

    // =========================
    // TYPE
    // =========================
    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        if (type == null) return;
        this.type = type;
    }

    // =========================
    // DEBUG
    // =========================
    @Override
    public String toString() {
        return "CableNode{" +
                "location=" + location +
                ", energy=" + energy + "/" + maxEnergy +
                ", type=" + type +
                ", connections=" + connections.size() +
                '}';
    }

    // =========================
    // IDENTITY (SAFE)
    // =========================
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CableNode other)) return false;
        return Objects.equals(this.location, other.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }
}