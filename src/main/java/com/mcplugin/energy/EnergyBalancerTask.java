package com.mcplugin.energy;

import com.mcplugin.core.Main;
import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EnergyBalancerTask extends BukkitRunnable {

    @Override
    public void run() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("energy.balancer.enabled", true)) return;

        int maxTransfer = cfg.getInt("energy.balancer.max_transfer", 25);
        boolean log = cfg.getBoolean("energy.balancer.log", false);

        // Собираем только BATTERY ноды, избегая двойного копирования
        List<CableNode> batteries = new ArrayList<>();
        CableNetwork.forEachNode(node -> {
            if (node != null && node.getType() == NodeType.BATTERY) {
                batteries.add(node);
            }
        });
        
        Set<Long> visitedKeys = new HashSet<>();

        for (CableNode start : batteries) {
            if (start == null) continue;
            if (start.getType() != NodeType.BATTERY) continue;

            BatteryManager.BatteryCluster cluster = BatteryManager.getCluster(start.getLocation());
            if (cluster != null && cluster.mode != BatteryManager.BatteryMode.CHARGE_DISCHARGE) continue;

            if (visitedKeys.contains(start.getKey())) continue;

            // BFS to find all batteries in this network
            List<CableNode> networkBatteries = new ArrayList<>();
            Queue<CableNode> queue = new LinkedList<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                CableNode node = queue.poll();
                if (node == null) continue;
                if (!visitedKeys.add(node.getKey())) continue;

                if (node.getType() == NodeType.BATTERY) {
                    BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                    if (bc == null || bc.mode == BatteryManager.BatteryMode.CHARGE_DISCHARGE) {
                        networkBatteries.add(node);
                    }
                }

                for (long connKey : node.getConnectionKeys()) {
                    if (visitedKeys.contains(connKey)) continue;
                    CableNode next = CableNetwork.getNodeByKey(node.getWorld().getUID().toString(), connKey);
                    if (next != null) queue.add(next);
                }
            }

            if (networkBatteries.size() <= 1) continue;

            // Calculate average
            int totalEnergy = 0;
            for (CableNode b : networkBatteries) totalEnergy += b.getEnergy();
            int average = totalEnergy / networkBatteries.size();

            // Collect excess
            int collected = 0;
            List<CableNode> rich = new ArrayList<>();
            List<CableNode> poor = new ArrayList<>();
            for (CableNode b : networkBatteries) {
                int current = b.getEnergy();
                if (current > average) {
                    int remove = Math.min(current - average, maxTransfer);
                    if (remove > 0) {
                        b.removeEnergy(remove);
                        collected += remove;
                        rich.add(b);
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

            // Return undistributed energy back to rich batteries (proportionally)
            if (collected > 0 && !rich.isEmpty()) {
                int totalRichEnergy = 0;
                for (CableNode r : rich) totalRichEnergy += r.getEnergy();
                int remainingToReturn = collected;
                for (int i = 0; i < rich.size() && remainingToReturn > 0; i++) {
                    CableNode r = rich.get(i);
                    int takenBack;
                    if (i == rich.size() - 1) {
                        takenBack = remainingToReturn;
                    } else {
                        double share = totalRichEnergy > 0 ? (double) r.getEnergy() / totalRichEnergy : 1.0 / rich.size();
                        takenBack = Math.min(remainingToReturn, Math.max(1, (int) (collected * share)));
                        // Prevent over-allocation
                        if (takenBack > remainingToReturn) takenBack = remainingToReturn;
                    }
                    r.addEnergy(takenBack);
                    remainingToReturn -= takenBack;
                }
                int returned = collected - remainingToReturn;
                if (log && returned > 0) {
                    ConsoleLogger.info("[Balancer] Returned " + returned + " undistributed energy to rich batteries");
                }
            } else if (collected > 0 && log) {
                ConsoleLogger.info("[Balancer] " + collected + " energy left undistributed (no rich batteries to return to)");
            }
        }
    }
}
