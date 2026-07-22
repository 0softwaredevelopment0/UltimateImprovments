package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.transfer.cable.CableBlock;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.NodeType;

import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;

import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

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
        // ⚡ ГЕНЕРАТОР (BLAST_FURNACE с PDC)
        // =========================
        if (type == Materials.BLAST_FURNACE
                && item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(Keys.GENERATOR, PersistentDataType.BYTE)) {
            GeneratorManager.savePdcToBlock(loc);
            GeneratorManager.onGeneratorPlaced(loc);
        }

        // =========================
        // 🔋 BATTERY MULTIBLOCK (hot expand) — НЕ пропускаем через CableNetwork.addNode()
        // ensureNode уже вызывает autoConnectNode внутри себя
        // =========================
        if (type == Materials.WAXED_COPPER_GRATE) {
            BatteryManager.onBlockPlaced(loc);
            // Если BatteryManager не добавил узел (изолированный блок) — создаём сами
            if (!CableNetwork.exists(loc)) {
                CableNetwork.ensureNode(loc, NodeType.BATTERY);
                // Создаём Marker с типом "battery", а не "cable"
                StructureMarker.place(loc, "battery", UUID.randomUUID());
            }
            return; // Не идём в CableNetwork.addNode — он создал бы Marker "cable"
        }

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot expand) — WAXED_COPPER_BULB, а не REDSTONE_LAMP!
        // =========================
        if (type == Materials.WAXED_COPPER_BULB) {
            LightManager.onBlockPlaced(loc);
            return; // Light не является кабелем
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