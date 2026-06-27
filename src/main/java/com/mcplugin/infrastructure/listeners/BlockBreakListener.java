package com.mcplugin.infrastructure.listeners;

import com.mcplugin.energy.generation.basic.GeneratorManager;
import com.mcplugin.energy.machines.assembler.AssemblerManager;
import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.infrastructure.structure.StructureMarker;
import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

public class BlockBreakListener implements Listener {

    @EventHandler(ignoreCancelled = true)
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
        // 🔥 ГЕНЕРАТОР (BLAST_FURNACE) — разобрать при ломании печи
        // =========================
        if (e.getBlock().getType() == Material.BLAST_FURNACE && GeneratorManager.isAssembled(loc)) {
            GeneratorManager.removeGenerator(loc);
            if (breaker != null) {
                breaker.sendMessage("§e⚡ Генератор разобран!"
                        + " §8[§7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "§8]");
            }
        }

        // =========================
        // 🔋 BATTERY MULTIBLOCK (hot shrink + orphaned marker cleanup)
        // =========================
        if (e.getBlock().getType() == Material.WAXED_COPPER_GRATE) {
            if (BatteryManager.isActive(loc)) {
                BatteryManager.onBlockBroken(loc, breaker);
            } else if (StructureMarker.existsAt(loc)) {
                // Orphaned Marker — кластер был потерян, но Marker остался в мире
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot shrink + orphaned marker cleanup)
        // =========================
        if (e.getBlock().getType() == Material.REDSTONE_LAMP) {
            if (LightManager.isActive(loc)) {
                LightManager.onBlockBroken(loc, breaker);
            } else if (StructureMarker.existsAt(loc)) {
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // 🛠 СБОРЩИК (CRAFTER) — разобрать при ломании, очистить Marker
        // =========================
        if (e.getBlock().getType() == Material.CRAFTER) {
            if (AssemblerManager.isAssembled(loc)) {
                AssemblerManager.removeAssembler(loc);
            } else if (StructureMarker.existsAt(loc)) {
                // Orphaned Marker cleanup
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // 🧲 МАГНИТ (LODESTONE) — очистка orphaned Marker'ов
        // Активные магниты обрабатываются в ReactorListener.onBlockBreak
        // =========================
        if (e.getBlock().getType() == Material.LODESTONE) {
            if (StructureMarker.existsAt(loc)) {
                StructureMarker.removeAt(loc);
            }
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