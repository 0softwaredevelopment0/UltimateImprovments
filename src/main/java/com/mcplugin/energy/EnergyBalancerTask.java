package com.mcplugin.energy;

import com.mcplugin.Main;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;

import com.mcplugin.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EnergyBalancerTask extends BukkitRunnable {

    @Override
    public void run() {

        FileConfiguration cfg =
                Main.getInstance().getConfig();

        // =========================
        // ENABLE CHECK
        // =========================
        if (!cfg.getBoolean(
                "energy.balancer.enabled",
                true
        )) {
            return;
        }

        boolean includeBatteries =
                cfg.getBoolean(
                        "energy.balancer.include_batteries",
                        false
                );

        int maxTransfer =
                cfg.getInt(
                        "energy.balancer.max_transfer",
                        25
                );

        boolean log =
                cfg.getBoolean(
                        "energy.balancer.log",
                        false
                );

        // =========================
        // SAFE NODE COPY
        // =========================
        Set<CableNode> allNodes =
                new HashSet<>(
                        CableNetwork.getAllNodes()
                );

        Set<Location> visited =
                new HashSet<>();

        // =========================
        // FIND NETWORKS
        // =========================
        for (CableNode start : allNodes) {

            if (start == null) {
                continue;
            }

            Location startLoc =
                    LocationUtil.normalize(
                            start.getLocation()
                    );

            if (visited.contains(startLoc)) {
                continue;
            }

            Set<CableNode> network =
                    new HashSet<>();

            Queue<CableNode> queue =
                    new LinkedList<>();

            queue.add(start);

            // =========================
            // BFS NETWORK SCAN
            // =========================
            while (!queue.isEmpty()) {

                CableNode node =
                        queue.poll();

                if (node == null) {
                    continue;
                }

                Location nodeLoc =
                        LocationUtil.normalize(
                                node.getLocation()
                        );

                if (visited.contains(nodeLoc)) {
                    continue;
                }

                // =========================
                // BATTERY FILTER
                // =========================
                if (!includeBatteries
                        && node.getType() == NodeType.BATTERY) {

                    continue;
                }

                visited.add(nodeLoc);

                network.add(node);

                // =========================
                // CONNECTIONS
                // =========================
                for (Location conn : node.getConnections()) {

                    if (conn == null) {
                        continue;
                    }

                    Location targetLoc =
                            LocationUtil.normalize(conn);

                    CableNode next =
                            CableNetwork.getNode(targetLoc);

                    if (next == null) {
                        continue;
                    }

                    // =========================
                    // BATTERY FILTER
                    // =========================
                    if (!includeBatteries
                            && next.getType() == NodeType.BATTERY) {

                        continue;
                    }

                    // =========================
                    // REAL CONNECTION CHECK
                    // =========================
                    if (!LocationUtil.isFullyConnected(
                            nodeLoc,
                            targetLoc
                    )) {
                        continue;
                    }

                    // =========================
                    // DOUBLE LINK CHECK
                    // =========================
                    if (!node.isConnected(targetLoc)
                            || !next.isConnected(nodeLoc)) {

                        continue;
                    }

                    if (!visited.contains(targetLoc)) {
                        queue.add(next);
                    }
                }
            }

            // =========================
            // EMPTY NETWORK
            // =========================
            if (network.isEmpty()) {
                continue;
            }

            // =========================
            // TOTAL ENERGY & BALANCE
            // =========================
            int totalEnergy = 0;

            for (CableNode node : network) {
                totalEnergy += node.getEnergy();
            }

            // =========================
            // SINGLE NODE
            // =========================
            if (network.size() <= 1) {
                continue;
            }

            int average =
                    totalEnergy / network.size();

            // =========================
            // COLLECT EXCESS ENERGY FROM RICH NODES
            // =========================
            int collectedExcess = 0;
            List<CableNode> poorNodes = new ArrayList<>();

            for (CableNode node : network) {

                int current =
                        node.getEnergy();

                // =========================
                // TOO HIGH — remove excess and collect it
                // =========================
                if (current > average) {

                    int remove =
                            Math.min(
                                    current - average,
                                    maxTransfer
                            );

                    if (remove > 0) {

                        node.removeEnergy(remove);
                        collectedExcess += remove;

                        CableNetwork.saveNode(node);

                        if (log) {

                            Main.getInstance()
                                    .getLogger()
                                    .info(
                                            "[Balancer] Collected "
                                                    + remove
                                                    + " energy from "
                                                    + node.getLocation()
                                    );
                        }
                    }
                }

                // =========================
                // TOO LOW — mark for distribution
                // =========================
                else if (current < average) {
                    poorNodes.add(node);
                }
            }

            // =========================
            // DISTRIBUTE COLLECTED ENERGY TO POOR NODES
            // =========================
            if (collectedExcess > 0) {
                for (CableNode node : poorNodes) {

                    if (collectedExcess <= 0) break;

                    int current = node.getEnergy();
                    int deficit = average - current;

                    if (deficit <= 0) continue;

                    int add = Math.min(
                            Math.min(deficit, maxTransfer),
                            collectedExcess
                    );

                    if (add > 0) {

                        node.addEnergy(add);
                        collectedExcess -= add;

                        CableNetwork.saveNode(node);

                        if (log) {

                            Main.getInstance()
                                    .getLogger()
                                    .info(
                                            "[Balancer] Distributed "
                                                    + add
                                                    + " energy to "
                                                    + node.getLocation()
                                    );
                        }
                    }
                }
            }

            // =========================
            // LOG REMAINDER
            // =========================
            if (collectedExcess > 0 && log) {
                Main.getInstance().getLogger().info(
                        "[Balancer] " + collectedExcess + " energy left undistributed (all nodes at cap)"
                );
            }
        }
    }
}