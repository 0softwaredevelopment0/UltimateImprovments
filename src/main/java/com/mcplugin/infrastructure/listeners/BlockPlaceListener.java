package com.mcplugin.infrastructure.listeners;

import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.energy.transfer.cable.CableBlock;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;

import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlaceListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {

        Location loc =
                LocationUtil.normalize(
                        e.getBlock().getLocation()
                );

        Material type =
                e.getBlock().getType();

        // =========================
        // 🔋 BATTERY MULTIBLOCK (hot expand)
        // =========================
        if (type == Material.WAXED_COPPER_GRATE) {
            BatteryManager.onBlockPlaced(loc);
        }

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot expand)
        // =========================
        if (type == Material.REDSTONE_LAMP) {
            LightManager.onBlockPlaced(loc);
        }

        // =========================
        // ⚡ CABLE / BATTERY
        // =========================
        if (!CableBlock.isCable(e.getBlock())) {
            return;
        }

        // create node
        CableNetwork.addNode(loc);

        CableNode node =
                CableNetwork.getNode(loc);

        if (node == null) {
            return;
        }

        // =========================
        // NODE TYPE
        // =========================
        if (type == Material.WAXED_COPPER_GRATE) {

            node.setType(NodeType.BATTERY);

        } else {

            node.setType(NodeType.CABLE);
        }

        // =========================
        // SQLITE SAVE
        // =========================
        CableNetwork.saveNode(node);

        // =========================
        // AUTO CONNECT
        // =========================
        autoConnect(loc, node);

        // =========================
        // SAVE AFTER CONNECTIONS
        // =========================
        CableNetwork.saveNode(node);
    }

    // =========================
    // AUTO CONNECT
    // =========================
    private void autoConnect(
            Location loc,
            CableNode node
    ) {

        if (node == null) {
            return;
        }

        for (Location nearby :
                LocationUtil.getNeighbors(loc)) {

            Location norm =
                    LocationUtil.normalize(nearby);

            CableNode neighbor =
                    CableNetwork.getNode(norm);

            if (neighbor == null) {
                continue;
            }

            // =========================
            // NO BATTERY ↔ BATTERY
            // =========================
            if (node.getType() == NodeType.BATTERY
                    && neighbor.getType() == NodeType.BATTERY) {
                continue;
            }

            // =========================
            // CONNECTION VALIDATION
            // =========================
            if (!LocationUtil.isFullyConnected(loc, norm)) {
                continue;
            }

            // =========================
            // CONNECT BOTH WAYS
            // =========================
            node.connect(norm);

            neighbor.connect(loc);

            // =========================
            // SQLITE SAVE
            // =========================
            CableNetwork.saveNode(node);
            CableNetwork.saveNode(neighbor);
        }
    }
}