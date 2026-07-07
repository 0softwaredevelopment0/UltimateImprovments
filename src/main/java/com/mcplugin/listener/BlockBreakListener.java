package com.mcplugin.listener;

import com.mcplugin.energy.generation.basic.GeneratorManager;
import com.mcplugin.energy.machines.assembler.AssemblerManager;
import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.Materials;

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

    private static final Map<Material, Material> ORE_TO_STONE = Map.ofEntries(
        // Stone ores -> STONE
        Map.entry(Material.COAL_ORE, Material.STONE),
        Map.entry(Material.IRON_ORE, Material.STONE),
        Map.entry(Material.COPPER_ORE, Material.STONE),
        Map.entry(Material.GOLD_ORE, Material.STONE),
        Map.entry(Material.REDSTONE_ORE, Material.STONE),
        Map.entry(Material.LAPIS_ORE, Material.STONE),
        Map.entry(Material.DIAMOND_ORE, Material.STONE),
        Map.entry(Material.EMERALD_ORE, Material.STONE),
        // Deepslate ores -> DEEPSLATE
        Map.entry(Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE),
        Map.entry(Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE),
        // Nether ores -> NETHERRACK
        Map.entry(Material.NETHER_QUARTZ_ORE, Material.NETHERRACK),
        Map.entry(Material.NETHER_GOLD_ORE, Material.NETHERRACK)
    );

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
        // 🔥 ГЕНЕРАТОР (BLAST_FURNACE) — разобрать при ломании печи
        // =========================
        if (e.getBlock().getType() == Materials.BLAST_FURNACE && GeneratorManager.isAssembled(loc)) {
            GeneratorManager.removeGenerator(loc);
            if (breaker != null) {
                breaker.sendMessage("§e⚡ Генератор разобран!"
                        + " §8[§7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "§8]");
            }
        }

        // =========================
        // 🔋 BATTERY MULTIBLOCK (hot shrink + orphaned marker cleanup)
        // =========================
        if (e.getBlock().getType() == Materials.WAXED_COPPER_GRATE) {
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
        // ⛏ ORE -> STONE / DEEPSLATE / NETHERRACK
        // =========================
        Material replacement = ORE_TO_STONE.get(block.getType());
        if (replacement != null) {
            Material finalReplacement = replacement;
            Bukkit.getScheduler().runTask(
                Main.getInstance(),
                () -> {
                    if (block.getType() == Material.AIR) {
                        block.setType(finalReplacement, false);
                    }
                }
            );
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
}