package com.mcplugin.energy.generation.basic;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.mcplugin.energy.storage.battery.BatteryManager;
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
    // BURN TRACKING (per furnace location)
    // fuelTimer: сколько тиков осталось гореть текущему топливу (decrements every tick)
    // energyAccumulator: накопленная дробная энергия (>= 1 → добавляем к батарее)
    // Generator works independently of furnace smelting state.
    // =========================
    private final Map<Location, Integer> fuelTimer = new HashMap<>();
    private final Map<Location, Double> energyAccumulator = new HashMap<>();

    @Override
    public void run() {

        FileConfiguration cfg = Main.getInstance().getConfig();

        if (!cfg.getBoolean("energy.generator.enabled", true)) {
            return;
        }

        int totalEnergyPerFuel =
                cfg.getInt("energy.generator.energy_per_fuel", 100);

        int burnDuration =
                cfg.getInt("energy.generator.fuel_burn_ticks", 100);

        boolean log =
                cfg.getBoolean("energy.generator.log", false);

        // =========================
        // CALCULATE PER-TICK ENERGY
        // =========================
        int effectiveBurn = Math.max(burnDuration, 1);
        // Энергия за тик (floor-деление). Остаток распределяется через extraTicks
        int energyPerTick = totalEnergyPerFuel / effectiveBurn;

        // =========================
        // CLEANUP: Remove entries for disassembled/broken generators
        // =========================
        fuelTimer.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Material.BLAST_FURNACE;
        });

        energyAccumulator.entrySet().removeIf(entry -> {
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

            int timer = fuelTimer.getOrDefault(furnaceLoc, 0);

            // =========================
            // CASE 1: BURNING — generate energy continuously each tick
            // =========================
            if (timer > 0) {
                // Accumulate fractional energy each tick
                double energyPerTickDouble = (double) totalEnergyPerFuel / effectiveBurn;
                double acc = energyAccumulator.getOrDefault(furnaceLoc, 0.0) + energyPerTickDouble;

                // На последнем тике горения округляем, чтобы не потерять энергию из-за double-precision
                int toAdd;
                if (timer == 1) {
                    toAdd = (int) Math.round(acc);
                    acc = 0.0;
                } else {
                    toAdd = (int) Math.floor(acc);
                    if (toAdd > 0) acc -= toAdd;
                }

                if (toAdd > 0) {
                    addEnergyToBatteryNetwork(nodeLoc, toAdd);
                }
                energyAccumulator.put(furnaceLoc, acc);

                fuelTimer.put(furnaceLoc, timer - 1);

                if (!data.isLit()) {
                    data.setLit(true);
                    block.setBlockData(data);
                }

                if (log) {
                    ConsoleLogger.info(
                            "[GENERATOR] +" + toAdd +
                                    " energy from " + furnaceLoc +
                                    " (remaining timer: " + (timer - 1) + " ticks)"
                    );
                }
                continue;
            }

            // =========================
            // CASE 2: TIMER EXPIRED, HAS FUEL — consume one item, reset timer
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

                    // Reset timer and accumulator
                    fuelTimer.put(furnaceLoc, burnDuration - 1);
                    energyAccumulator.put(furnaceLoc, 0.0);

                    // First tick energy
                    double energyPerTickDouble = (double) totalEnergyPerFuel / effectiveBurn;
                    int firstAdd = (int) Math.floor(energyPerTickDouble);
                    double rem = energyPerTickDouble - firstAdd;
                    if (firstAdd > 0) {
                        addEnergyToBatteryNetwork(nodeLoc, firstAdd);
                    }
                    if (rem > 0) {
                        energyAccumulator.put(furnaceLoc, rem);
                    }

                    if (!data.isLit()) {
                        data.setLit(true);
                        block.setBlockData(data);
                    }

                    if (log) {
                        ConsoleLogger.info(
                                "[GENERATOR] Fuel consumed at " + furnaceLoc +
                                        ", +" + firstAdd + " energy (timer: " + burnDuration + " ticks)"
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

            // Found a battery — add energy here (только если режим позволяет зарядку)
            if (node.getType() == NodeType.BATTERY) {
                BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                if ((bc == null || bc.canCharge()) && node.getEnergy() < node.getMaxEnergy()) {
                    node.addEnergy(amount);
                    return;
                }
                // Если батарея в режиме DISCHARGE или полная — продолжаем BFS к другим батареям
                continue;
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