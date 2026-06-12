package com.mcplugin.tasks;

import com.mcplugin.cable.*;
import com.mcplugin.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class CableTickTask extends BukkitRunnable {

    @Override
    public void run() {

        // =========================
        // CABLE FLOW — distributes energy evenly between connected cables
        // Battery charging is handled by BatteryDrainTask
        // =========================
        Set<CableNode> snapshot = new HashSet<>(CableNetwork.getAllNodes());

        for (CableNode node : snapshot) {

            if (node.getType() == NodeType.BATTERY) continue;
            if (node.getEnergy() <= 0) continue;

            Location fromLoc = node.getLocation();

            for (Location conn : node.getConnections()) {

                CableNode target = CableNetwork.getNode(conn);
                if (target == null) continue;

                if (target.getType() == NodeType.BATTERY) continue;

                if (!isValidCable(conn)) continue;

                // ❗ FIX: strict direction BOTH WAYS
                if (!LocationUtil.isFullyConnected(fromLoc, conn)
                        || !LocationUtil.isFullyConnected(conn, fromLoc)) {
                    continue;
                }

                int diff = node.getEnergy() - target.getEnergy();
                if (diff <= 1) continue;

                int transfer = Math.min(2, diff / 3);
                if (transfer <= 0) continue;

                node.removeEnergy(transfer);
                target.addEnergy(transfer);
            }
        }
    }

    // =========================
    // VALID CABLE CHECK
    // =========================
    private boolean isValidCable(Location loc) {

        Material type = loc.getBlock().getType();

        return type == Material.WAXED_LIGHTNING_ROD
                || type == Material.WAXED_CHISELED_COPPER;
    }
}