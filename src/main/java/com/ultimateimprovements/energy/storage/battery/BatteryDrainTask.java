package com.ultimateimprovements.energy.storage.battery;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.energy.transfer.cable.CableNetwork;
import com.ultimateimprovements.energy.transfer.cable.CableNode;
import com.ultimateimprovements.energy.transfer.cable.NodeType;
import com.ultimateimprovements.util.LocationUtil;
import com.ultimateimprovements.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BatteryDrainTask extends BukkitRunnable {

    @Override
    public void run() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("energy.battery_drain.enabled", true)) return;

        int maxBatteryEnergy = cfg.getInt("energy.battery.max_energy", 100000);
        int dischargeAmount = cfg.getInt("energy.battery.discharge_per_tick", 10);

        boolean smoothEnabled = cfg.getBoolean("energy.battery.smooth_charge.enabled", true);
        double dischargeMultiplier = cfg.getDouble("energy.battery.smooth_charge.discharge_multiplier", 0.5);
        boolean log = cfg.getBoolean("energy.battery_drain.log", false);

        // Используем прямой итератор без создания копии
        List<CableNode> batteries = new ArrayList<>();
        CableNetwork.forEachNode(node -> {
            if (node != null && node.getType() == NodeType.BATTERY) {
                batteries.add(node);
            }
        });

        for (CableNode battery : batteries) {
            if (battery == null || battery.getType() != NodeType.BATTERY) continue;

            Location batteryLoc = LocationUtil.normalize(battery.getLocation());
            battery.setMaxEnergy(maxBatteryEnergy);

            double fillRatio = (double) battery.getEnergy() / Math.max(maxBatteryEnergy, 1);

            BatteryManager.BatteryCluster cluster = BatteryManager.getCluster(batteryLoc);
            boolean canDischarge = (cluster != null) ? cluster.canDischarge() : (battery.getEnergy() > 0);

            if (canDischarge && battery.getEnergy() > 0) {
                int dynamicDischarge = dischargeAmount;
                if (smoothEnabled) {
                    double factor = dischargeMultiplier + (1.0 - dischargeMultiplier) * fillRatio;
                    dynamicDischarge = Math.max(1, (int) (dischargeAmount * factor));
                }

                // BFS to find other batteries - using connection keys for efficiency
                Set<Long> visited = new HashSet<>();
                Queue<CableNode> queue = new LinkedList<>();
                queue.add(battery);
                visited.add(battery.getKey());

                int remaining = dynamicDischarge;

                while (!queue.isEmpty() && remaining > 0) {
                    CableNode node = queue.poll();
                    if (node == null) continue;

                    if (node.getType() == NodeType.CABLE) {
                        CableNetwork.markFlowingKey(node.getWorld().getUID().toString(), node.getKey());
                    }

                    if (node != battery && node.getType() == NodeType.BATTERY) {
                        int space = maxBatteryEnergy - node.getEnergy();
                        if (space > 0) {
                            int transfer = Math.min(remaining, space);
                            if (transfer > 0) {
                                battery.removeEnergy(transfer);
                                node.addEnergy(transfer);
                                remaining -= transfer;

                                // Track transfer on cable nodes in the path
                                for (var entry : visited) {
                                    CableNode pathNode = CableNetwork.getNodeByKey(node.getWorld().getUID().toString(), entry);
                                    if (pathNode != null && pathNode.getType() == NodeType.CABLE) {
                                        pathNode.addTransferred(transfer);
                                    }
                                }

                                if (log) {
                                    ConsoleLogger.info(
                                            "[Battery] Discharged " + transfer
                                                    + " from " + batteryLoc + " to " + node.getLocation());
                                }
                            }
                        }
                    }

                    if (remaining <= 0) break;

                    for (long connKey : node.getConnectionKeys()) {
                        if (visited.contains(connKey)) continue;
                        CableNode next = CableNetwork.getNodeByKey(node.getWorld().getUID().toString(), connKey);
                        if (next != null) {
                            visited.add(connKey);
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }
}
