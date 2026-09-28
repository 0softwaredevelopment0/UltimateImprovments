package com.ultimateimprovments.listener;

import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.Set;

public class BlockBreakListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {

        Block block = e.getBlock();
        Location loc = LocationUtil.normalize(block.getLocation());

        // =========================
        // SAFETY CHECK
        // =========================
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        Player breaker = e.getPlayer();

        // =========================
        // 🔥 GENERATOR (BLAST_FURNACE) — dismantle when the furnace is broken
        // =========================
        if (e.getBlock().getType() == Materials.BLAST_FURNACE && GeneratorManager.isAssembled(loc)) {
            GeneratorManager.removeGenerator(loc);
            if (breaker != null) {
                breaker.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<yellow>⚡ Генератор демонтирован!</yellow>"
                        + " <dark_gray>[" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "]</dark_gray>"));
            }
        }

        // =========================
        // 🔋 BATTERY MULTIBLOCK (hot shrink + orphaned marker cleanup)
        // =========================
        if (e.getBlock().getType() == Materials.WAXED_COPPER_GRATE) {
            if (BatteryManager.isActive(loc)) {
                BatteryManager.onBlockBroken(loc, breaker);
            } else if (StructureMarker.existsAt(loc)) {
                // Orphaned Marker — the cluster was lost, but the Marker remained in the world
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // 💡 LIGHT MULTIBLOCK (hot shrink + orphaned marker cleanup)
        // =========================
        if (e.getBlock().getType() == Materials.WAXED_COPPER_BULB) {
            if (LightManager.isActive(loc)) {
                LightManager.onBlockBroken(loc, breaker);
            } else if (StructureMarker.existsAt(loc)) {
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // CRAFTER — clean up any orphaned Marker (old Item Assembler leftovers)
        // =========================
        if (e.getBlock().getType() == Material.CRAFTER) {
            if (StructureMarker.existsAt(loc)) {
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // 🧲 MAGNET (LODESTONE) — cleanup of orphaned Markers
        // Active magnets are handled in ReactorListener.onBlockBreak
        // =========================
        if (e.getBlock().getType() == Material.LODESTONE) {
            if (StructureMarker.existsAt(loc)) {
                StructureMarker.removeAt(loc);
            }
        }

        // =========================
        // ⛏ ORE -> STONE / DEEPSLATE / NETHERRACK
        // =========================
        scheduleStoneReplacement(block, block.getType());

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
        // REMOVE CONNECTIONS FIRST (via efficient long keys)
        // =========================
        Set<Long> connectionKeys = Set.copyOf(node.getConnectionKeys());
        String worldUid = loc.getWorld().getUID().toString();

        for (long connKey : connectionKeys) {
            CableNode neighbor = CableNetwork.getNodeByKey(worldUid, connKey);
            if (neighbor != null) {
                neighbor.disconnectKey(LocationUtil.toKey(loc));
            }
        }

        // =========================
        // REMOVE NODE (MEMORY + DB)
        // =========================
        CableNetwork.removeNode(loc);
    }

    /**
     * «Ore behavior change» mechanic: after mining an ore, its place
     * becomes stone (STONE / DEEPSLATE / NETHERRACK — depending on the ore type).
     * <p>
     * Works deferred (next tick) and only if the block is still AIR —
     * so the mechanic does not interfere with other listeners waiting for an empty block,
     * and does not overwrite a block placed by the player during that tick.
     * <p>
     * {@code oreType} is passed explicitly: for blocks broken by the AoE/VeinMiner
     * enchants via {@code breakNaturally()}, the block is already AIR after breaking,
     * so the type must be remembered BEFORE destruction.
     *
     * @param block   the broken block (may already be AIR at call time)
     * @param oreType the ore type the block was before breaking
     */
    public static void scheduleStoneReplacement(Block block, Material oreType) {
        CoreHooks.scheduleOreStone(block, oreType);
    }
}