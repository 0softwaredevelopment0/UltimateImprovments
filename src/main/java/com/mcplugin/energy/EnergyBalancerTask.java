package com.mcplugin.energy;

import com.mcplugin.infrastructure.core.Main;

import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;

import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Energy balancer — balances energy between BATTERIES only.
 * Cables no longer store energy, so only batteries are balanced.
 */
public class EnergyBalancerTask extends BukkitRunnable {

    @Override
    public void run() {

        FileConfiguration cfg = Main.getInstance().getConfig();

        if (!cfg.getBoolean("energy.balancer.enabled", true)) {
            return;
        }

        int maxTransfer = cfg.getInt("energy.balancer.max_transfer", 25);
        boolean log = cfg.getBoolean("energy.balancer.log", false);

        Set<CableNode> allNodes = new HashSet<>(CableNetwork.getAllNodes());
        Set<Location> visited = new HashSet<>();

        for (CableNode start : allNodes) {
            if (start == null) continue;
            if (start.getType() != NodeType.BATTERY) continue; // only batteries have energy

            Location startLoc = LocationUtil.normalize(start.getLocation());
            if (visited.contains(startLoc)) continue;

            // BFS to find all batteries in this network
            List<CableNode> batteries = new ArrayList<>();
            Queue<CableNode> queue = new LinkedList<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                CableNode node = queue.poll();
                if (node == null) continue;
                Location nodeLoc = LocationUtil.normalize(node.getLocation());
                if (visited.contains(nodeLoc)) continue;
                visited.add(nodeLoc);

                if (node.getType() == NodeType.BATTERY) {
                    batteries.add(node);
                }

                for (Location conn : node.getConnections()) {
                    if (visited.contains(conn)) continue;
                    CableNode next = CableNetwork.getNode(conn);
                    if (next != null) queue.add(next);
                }
            }

            if (batteries.size() <= 1) continue;

            // Calculate average
            int totalEnergy = 0;
            for (CableNode b : batteries) totalEnergy += b.getEnergy();
            int average = totalEnergy / batteries.size();

            // Collect excess
            int collected = 0;
            List<CableNode> poor = new ArrayList<>();
            for (CableNode b : batteries) {
                int current = b.getEnergy();
                if (current > average) {
                    int remove = Math.min(current - average, maxTransfer);
                    if (remove > 0) {
                        b.removeEnergy(remove);
                        collected += remove;
                        CableNetwork.markFlowing(b.getLocation());
                    }
                } else if (current < average) {
                    poor.add(b);
                }
            }

            // Distribute to poor
            for (CableNode b : poor) {
                if (collected <= 0) break;
                int deficit = average - b.getEnergy();
                if (deficit <= 0) continue;
                int add = Math.min(Math.min(deficit, maxTransfer), collected);
                if (add > 0) {
                    b.addEnergy(add);
                    collected -= add;
                    CableNetwork.markFlowing(b.getLocation());
                }
            }

            if (collected > 0 && log) {
                Main.getInstance().getLogger().info(
                        "[Balancer] " + collected + " energy left undistributed");
            }
        }
    }
}