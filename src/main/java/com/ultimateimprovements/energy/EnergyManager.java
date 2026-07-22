package com.ultimateimprovements.energy;

import com.ultimateimprovements.energy.transfer.cable.CableNetwork;
import com.ultimateimprovements.energy.transfer.cable.CableNode;
import com.ultimateimprovements.energy.transfer.cable.NodeType;

import com.ultimateimprovements.util.LocationUtil;

import org.bukkit.Location;

/**
 * @deprecated Cables no longer store energy — energy flows directly
 * from generators to batteries and from batteries to consumers through BFS.
 * Use BFS pathfinding via CableNetwork instead.
 */
@Deprecated
public class EnergyManager {

    // =========================
    // ADD ENERGY
    // =========================
    public static void addEnergy(Location loc, int amount) {

        if (amount <= 0) {
            return;
        }

        CableNode node =
                CableNetwork.getNode(
                        LocationUtil.normalize(loc)
                );

        if (node == null) {
            return;
        }

        node.addEnergy(amount);
    }

    // =========================
    // REMOVE ENERGY
    // =========================
    public static void removeEnergy(Location loc, int amount) {

        if (amount <= 0) {
            return;
        }

        CableNode node =
                CableNetwork.getNode(
                        LocationUtil.normalize(loc)
                );

        if (node == null) {
            return;
        }

        node.removeEnergy(amount);
    }

    // =========================
    // SAFE TRANSFER
    // =========================
    public static void transfer(
            Location from,
            Location to,
            int amount
    ) {

        if (amount <= 0) {
            return;
        }

        from = LocationUtil.normalize(from);
        to = LocationUtil.normalize(to);

        CableNode source =
                CableNetwork.getNode(from);

        CableNode target =
                CableNetwork.getNode(to);

        // =========================
        // NULL CHECK
        // =========================
        if (source == null || target == null) {
            return;
        }

        // =========================
        // SAME NODE
        // =========================
        if (from.equals(to)) {
            return;
        }

        // =========================
        // MUST BE CONNECTED
        // =========================
        if (!source.getConnections().contains(to)) {
            return;
        }

        // =========================
        // AXIS CHECK
        // =========================
        if (!LocationUtil.isFullyConnected(
                from,
                to
        )) {
            return;
        }

        // =========================
        // BATTERY RULES
        // =========================

        // батарея -> батарея запрещено
        if (source.getType() == NodeType.BATTERY
                && target.getType() == NodeType.BATTERY) {

            return;
        }

        // =========================
        // ENERGY CHECK
        // =========================
        int available = source.getEnergy();

        if (available <= 0) {
            return;
        }

        int transfer =
                Math.min(available, amount);

        if (transfer <= 0) {
            return;
        }

        // =========================
        // TRANSFER
        // =========================
        source.removeEnergy(transfer);
        target.addEnergy(transfer);
    }

    // =========================
    // GET ENERGY
    // =========================
    public static int getEnergy(Location loc) {

        CableNode node =
                CableNetwork.getNode(
                        LocationUtil.normalize(loc)
                );

        if (node == null) {
            return 0;
        }

        return node.getEnergy();
    }

    // =========================
    // HAS ENERGY
    // =========================
    public static boolean hasEnergy(
            Location loc,
            int amount
    ) {

        return getEnergy(loc) >= amount;
    }
}