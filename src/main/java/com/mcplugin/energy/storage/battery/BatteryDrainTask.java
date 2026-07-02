package com.mcplugin.energy.storage.battery;

import com.mcplugin.infrastructure.core.Main;

import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;

import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Battery drain/charge task.
 * Cables no longer store energy, so batteries only interact with other batteries.
 *
 * DISCHARGE: when redstone-powered, battery distributes energy to other batteries
 *            in the network through BFS pathfinding.
 *
 * Note: Charging is handled by GeneratorTask (generator → battery through cables).
 */
public class BatteryDrainTask extends BukkitRunnable {

    @Override
    public void run() {

        FileConfiguration cfg = Main.getInstance().getConfig();

        if (!cfg.getBoolean("energy.battery_drain.enabled", true)) {
            return;
        }

        int maxBatteryEnergy = cfg.getInt("energy.battery.max_energy", 100000);
        int dischargeAmount = cfg.getInt("energy.battery.discharge_per_tick", 10);

        boolean smoothEnabled = cfg.getBoolean("energy.battery.smooth_charge.enabled", true);
        double dischargeMultiplier = cfg.getDouble("energy.battery.smooth_charge.discharge_multiplier", 0.5);

        boolean log = cfg.getBoolean("energy.battery_drain.log", false);

        Set<CableNode> nodes = new HashSet<>(CableNetwork.getAllNodes());

        for (CableNode battery : nodes) {
            if (battery == null || battery.getType() != NodeType.BATTERY) continue;

            Location batteryLoc = LocationUtil.normalize(battery.getLocation());
            battery.setMaxEnergy(maxBatteryEnergy);

            double fillRatio = (double) battery.getEnergy() / Math.max(maxBatteryEnergy, 1);

            // Режим: отдаём энергию только если DISCHARGE или CHARGE_DISCHARGE
            BatteryManager.BatteryCluster cluster = BatteryManager.getCluster(batteryLoc);
            boolean canDischarge = (cluster != null) ? cluster.canDischarge() : (battery.getEnergy() > 0);

            // =========================
            // DISCHARGE — battery pushes energy to other batteries in network
            // =========================
            if (canDischarge && battery.getEnergy() > 0) {

                int dynamicDischarge = dischargeAmount;
                if (smoothEnabled) {
                    double factor = dischargeMultiplier + (1.0 - dischargeMultiplier) * fillRatio;
                    dynamicDischarge = Math.max(1, (int) (dischargeAmount * factor));
                }

                // BFS to find other batteries
                Set<Location> visited = new HashSet<>();
                Queue<CableNode> queue = new LinkedList<>();
                queue.add(battery);
                visited.add(batteryLoc);

                int remaining = dynamicDischarge;

                while (!queue.isEmpty() && remaining > 0) {
                    CableNode node = queue.poll();
                    if (node == null) continue;

                    // Mark cables as flowing
                    if (node.getType() == NodeType.CABLE) {
                        CableNetwork.markFlowing(node.getLocation());
                    }

                    // Push energy to other batteries
                    if (node != battery && node.getType() == NodeType.BATTERY) {
                        int space = maxBatteryEnergy - node.getEnergy();
                        if (space > 0) {
                            int transfer = Math.min(remaining, space);
                            if (transfer > 0) {
                                battery.removeEnergy(transfer);
                                node.addEnergy(transfer);
                                remaining -= transfer;

                                if (log) {
                                    ConsoleLogger.info(
                                            "[Battery] Discharged " + transfer
                                                    + " from " + batteryLoc + " to " + node.getLocation());
                                }
                            }
                        }
                    }

                    if (remaining <= 0) break;

                    for (Location conn : node.getConnections()) {
                        if (visited.contains(conn)) continue;
                        CableNode next = CableNetwork.getNode(conn);
                        if (next != null) {
                            visited.add(conn);
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }
}