package com.ultimateimprovments.energy.storage.battery;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;
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
                // Parent tracking: nodeKey -> parentKey (для реконструкции пути)
                Map<Long, Long> parent = new HashMap<>();
                Queue<CableNode> queue = new LinkedList<>();
                queue.add(battery);
                visited.add(battery.getKey());
                parent.put(battery.getKey(), -1L); // корень

                int remaining = dynamicDischarge;
                CableNode targetBattery = null;

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
                                targetBattery = node;

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
                            parent.put(connKey, node.getKey());
                            queue.add(next);
                        }
                    }
                }

                // Track transfer only on the actual BFS path (not all visited nodes)
                if (targetBattery != null) {
                    String worldUid = battery.getWorld().getUID().toString();
                    long current = targetBattery.getKey();
                    Set<Long> pathKeys = new HashSet<>();
                    while (parent.containsKey(current) && current != battery.getKey()) {
                        long p = parent.get(current);
                        if (p == battery.getKey()) break; // сосед батареи — разрыв
                        if (p != -1L) pathKeys.add(p);
                        current = p;
                    }
                    for (long pk : pathKeys) {
                        CableNode pn = CableNetwork.getNodeByKey(worldUid, pk);
                        if (pn != null && pn.getType() == NodeType.CABLE) {
                            pn.addTransferred(dynamicDischarge - remaining);
                        }
                    }
                }
            }
        }
    }
}
