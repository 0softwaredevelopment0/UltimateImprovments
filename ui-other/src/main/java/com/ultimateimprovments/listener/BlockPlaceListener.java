package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.transfer.cable.CableBlock;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.NodeType;

import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;

import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class BlockPlaceListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {

        Location loc =
                LocationUtil.normalize(
                        e.getBlock().getLocation()
                );

        Material type =
                e.getBlock().getType();

        ItemStack item = e.getItemInHand();

        // =========================
        // ⚡ GENERATOR (BLAST_FURNACE with PDC)
        // =========================
        if (type == Materials.BLAST_FURNACE
                && item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(Keys.GENERATOR, PersistentDataType.BYTE)) {
            GeneratorManager.savePdcToBlock(loc);
            GeneratorManager.onGeneratorPlaced(loc);
        }

        // =========================
        // 🔋 BATTERY MULTIBLOCK — a copper grate is INERT until the structure is
        // assembled with an item frame (ReactorListener). Placing a grate next to
        // an assembled battery expands that battery; an isolated grate gets no
        // node, no marker and no capacity. Never fall through to the cable path.
        // =========================
        if (type == Materials.WAXED_COPPER_GRATE) {
            BatteryManager.onBlockPlaced(loc);
            return;
        }

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot expand) — a waxed copper bulb is a lamp of an
        // assembled structure and never a cable. A bulb placed against an
        // assembled structure joins it (the next tick lights it if needed).
        // =========================
        if (type == Materials.WAXED_COPPER_BULB) {
            LightManager.onBlockPlaced(loc);
            return; // Light is not a cable
        }

        // =========================
        // ⚡ CABLE (WAXED_LIGHTNING_ROD / WAXED_CHISELED_COPPER)
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
        node.setType(NodeType.CABLE);

        // =========================
        // AUTO CONNECT
        // =========================
        autoConnect(loc, node);

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot expand) — a waxed chiseled copper block is a
        // junction of the energy network AND a possible energy port of an
        // assembled light structure, so a junction placed against one joins it.
        // =========================
        if (type == Materials.WAXED_CHISELED_COPPER) {
            LightManager.onBlockPlaced(loc);
        }
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
        }
    }
}