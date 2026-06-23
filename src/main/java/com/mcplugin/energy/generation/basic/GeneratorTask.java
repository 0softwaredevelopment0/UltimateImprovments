package com.mcplugin.energy.generation.basic;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.mcplugin.energy.transfer.cable.NodeType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class GeneratorTask extends BukkitRunnable {

    // =========================
    // MANUAL BURN TRACKING (per furnace location)
    // Generator works independently of furnace smelting state.
    // Fuel burn duration is tracked here so energy is generated
    // smoothly each tick WITHOUT fuel acceleration.
    // =========================
    private final Map<Location, Integer> burnTicks = new HashMap<>();

    // =========================
    // REMAINDER TRACKING: distributes the leftover energy
    // (totalEnergyPerFuel % burnDuration) over the first N ticks
    // so total energy added = energy_per_fuel exactly.
    // =========================
    private final Map<Location, Integer> extraTicks = new HashMap<>();

    @Override
    public void run() {

        FileConfiguration cfg = Main.getInstance().getConfig();

        if (!cfg.getBoolean("energy.generator.enabled", true)) {
            return;
        }

        int totalEnergyPerFuel =
                cfg.getInt("energy.generator.energy_per_fuel", 100);

        int burnDuration =
                cfg.getInt("energy.generator.fuel_burn_ticks", 1600);

        boolean log =
                cfg.getBoolean("energy.generator.log", false);

        // =========================
        // CALCULATE PER-TICK ENERGY
        // =========================
        int effectiveBurn = Math.max(burnDuration, 1);
        int energyPerTick = totalEnergyPerFuel / effectiveBurn;

        // =========================
        // CLEANUP: Remove entries for disassembled/broken generators
        // =========================
        burnTicks.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Material.BLAST_FURNACE;
        });

        extraTicks.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Material.BLAST_FURNACE;
        });

        // =========================
        // ONLY PROCESS ASSEMBLED GENERATORS
        // =========================
        Collection<Location> generators = GeneratorManager.getActiveGenerators();

        for (Location furnaceLoc : generators) {

            if (furnaceLoc == null) continue;

            Block block = furnaceLoc.getBlock();
            if (block.getType() != Material.BLAST_FURNACE) continue;

            // Find connected cable node
            CableNode node = GeneratorManager.findConnectedNode(furnaceLoc);
            if (node == null) continue;

            Location nodeLoc = node.getLocation();

            if (!LocationUtil.isFullyConnected(nodeLoc, furnaceLoc)) {
                continue;
            }

            BlockState state = block.getState();

            if (!(state instanceof Furnace furnace)) {
                continue;
            }

            ItemStack fuel = furnace.getInventory().getFuel();

            // =========================
            // SAFE BLOCK DATA
            // =========================
            org.bukkit.block.data.type.Furnace data =
                    (org.bukkit.block.data.type.Furnace) block.getBlockData();

            int remaining = burnTicks.getOrDefault(furnaceLoc, 0);

            // =========================
            // CASE 1: BURNING — generate energy smoothly each tick
            // =========================
            if (remaining > 0) {
                int add = energyPerTick;

                // Distribute the remainder (+1 extra for first N ticks)
                int extra = extraTicks.getOrDefault(furnaceLoc, 0);
                if (extra > 0) {
                    add += 1;
                    extraTicks.put(furnaceLoc, extra - 1);
                }

                if (add > 0) {
                    addEnergyToBatteryNetwork(nodeLoc, add);
                }

                burnTicks.put(furnaceLoc, remaining - 1);

                if (!data.isLit()) {
                    data.setLit(true);
                    block.setBlockData(data);
                }

                if (log) {
                    Main.getInstance().getLogger().info(
                            "[GENERATOR] +" + add +
                                    " energy from " + furnaceLoc +
                                    " (remaining burn: " + (remaining - 1) + " ticks)"
                    );
                }
                continue;
            }

            // =========================
            // CASE 2: NOT BURNING, HAS FUEL — consume one item, start burning
            // =========================
            if (fuel != null && !fuel.getType().isAir() && fuel.getType().isFuel()
                    && burnDuration > 0) {
                    // Consume one fuel item
                    ItemStack newFuel = fuel.clone();
                    int amount = newFuel.getAmount() - 1;
                    if (amount <= 0) {
                        furnace.getInventory().setFuel(null);
                    } else {
                        newFuel.setAmount(amount);
                        furnace.getInventory().setFuel(newFuel);
                    }

                    // Start burning (remaining - 1 because we generate energy THIS tick too)
                    burnTicks.put(furnaceLoc, burnDuration - 1);

                    // First tick: add energyPerTick (and extra if remainder > 0)
                    int firstAdd = energyPerTick;
                    int remainder = totalEnergyPerFuel % effectiveBurn;
                    if (remainder > 0) {
                        firstAdd += 1;
                        // Remaining extra ticks = remainder - 1 (one used this tick)
                        if (remainder > 1) {
                            extraTicks.put(furnaceLoc, remainder - 1);
                        }
                    }

                    if (firstAdd > 0) {
                        addEnergyToBatteryNetwork(nodeLoc, firstAdd);
                    }

                    if (!data.isLit()) {
                        data.setLit(true);
                        block.setBlockData(data);
                    }

                    if (log) {
                        Main.getInstance().getLogger().info(
                                "[GENERATOR] Fuel consumed at " + furnaceLoc +
                                        ", +" + firstAdd + " energy (burn: " + burnDuration + " ticks)"
                            );
                    }
                continue;
            }

            // =========================
            // CASE 3: NO FUEL — ensure furnace is unlit
            // =========================
            if (data.isLit()) {
                data.setLit(false);
                block.setBlockData(data);
            }
        }
    }

    // =========================
    // BFS TO FIND BATTERY AND ADD ENERGY
    // Cables don't store energy — we pathfind through them to find
    // the first battery and add energy there, marking cables as flowing.
    // =========================
    private void addEnergyToBatteryNetwork(Location startCable, int amount) {
        CableNode start = CableNetwork.getNode(startCable);
        if (start == null || amount <= 0) return;

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getLocation());

        while (!queue.isEmpty()) {
            CableNode node = queue.poll();
            if (node == null) continue;

            // Mark cable as flowing (energy is passing through)
            if (node.getType() == NodeType.CABLE) {
                CableNetwork.markFlowing(node.getLocation());
            }

            // Found a battery — add energy here
            if (node.getType() == NodeType.BATTERY) {
                node.addEnergy(amount);
                return;
            }

            for (Location conn : node.getConnections()) {
                if (visited.contains(conn)) continue;
                CableNode next = CableNetwork.getNode(conn);
                if (next == null) continue;
                visited.add(conn);
                queue.add(next);
            }
        }
    }

}