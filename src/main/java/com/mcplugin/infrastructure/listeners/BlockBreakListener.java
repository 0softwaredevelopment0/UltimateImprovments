package com.mcplugin.infrastructure.listeners;

import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

public class BlockBreakListener implements Listener {

    @EventHandler
    public void onBreak(BlockBreakEvent e) {

        Location loc = LocationUtil.normalize(e.getBlock().getLocation());

        // =========================
        // SAFETY CHECK
        // =========================
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        Player breaker = e.getPlayer();

        // =========================
        // 🔋 BATTERY MULTIBLOCK (hot shrink)
        // =========================
        if (e.getBlock().getType() == Material.WAXED_COPPER_GRATE && BatteryManager.isActive(loc)) {
            BatteryManager.onBlockBroken(loc, breaker);
        }

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot shrink)
        // =========================
        if (e.getBlock().getType() == Material.REDSTONE_LAMP && LightManager.isActive(loc)) {
            LightManager.onBlockBroken(loc, breaker);
        }

        // =========================
        // 🛠 ENERGY WORKBENCH
        // =========================
        if (e.getBlock().getType().name().equals("CRAFTING_TABLE")) {
            EnergyWorkbenchManager.remove(loc);
            return;
        }

        // =========================
        // ⚡ ONLY IF NODE EXISTS
        // =========================
        if (!CableNetwork.exists(loc)) {
            return;
        }

        CableNode node = CableNetwork.getNode(loc);

        if (node == null) {
            return;
        }

        // =========================
        // REMOVE CONNECTIONS FIRST
        // =========================
        Set<Location> connections = Set.copyOf(node.getConnections());

        for (Location conn : connections) {

            CableNode neighbor = CableNetwork.getNode(conn);

            if (neighbor != null) {

                neighbor.disconnect(loc);

                CableNetwork.saveNode(neighbor);
            }
        }

        // =========================
        // REMOVE NODE (MEMORY + DB)
        // =========================
        CableNetwork.removeNode(loc);
    }
}