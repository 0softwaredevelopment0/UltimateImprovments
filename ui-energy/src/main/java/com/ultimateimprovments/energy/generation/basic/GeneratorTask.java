package com.ultimateimprovments.energy.generation.basic;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;

import com.ultimateimprovments.util.Materials;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GeneratorTask extends BukkitRunnable {

    /**
     * The task runs every 40 ticks (2s). All burn/energy math below is
     * scaled by this period, so the per-second fuel burn and energy output
     * stay exactly the same as with the old per-tick schedule.
     */
    private static final int TICKS_PER_RUN = 40;

    // =========================
    // BURN TRACKING (per furnace location)
    // fuelTimer: how many ticks the current fuel has left to burn (decrements every run, by up to TICKS_PER_RUN)
    // energyAccumulator: accumulated fractional energy (>= 1 → added to the battery)
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
        // Energy per tick (floor division). The remainder is distributed via extraTicks
        int energyPerTick = totalEnergyPerFuel / effectiveBurn;

        // =========================
        // CLEANUP: Remove entries for disassembled/broken generators
        // =========================
        fuelTimer.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Materials.BLAST_FURNACE;
        });

        energyAccumulator.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Materials.BLAST_FURNACE;
        });

        // =========================
        // ONLY PROCESS ASSEMBLED GENERATORS
        // =========================
        Collection<Location> generators = GeneratorManager.getActiveGenerators();

        for (Location furnaceLoc : generators) {

            if (furnaceLoc == null) continue;

            Block block = furnaceLoc.getBlock();
            if (block.getType() != Materials.BLAST_FURNACE) continue;

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
            // CASE 1: BURNING — generate energy continuously each run
            // =========================
            if (timer > 0) {
                int burnedNow = Math.min(TICKS_PER_RUN, timer);
                // Accumulate fractional energy for the ticks burned this run
                double energyPerTickDouble = (double) totalEnergyPerFuel / effectiveBurn;
                double acc = energyAccumulator.getOrDefault(furnaceLoc, 0.0) + energyPerTickDouble * burnedNow;

                // On the last burn tick we round, so no energy is lost due to double-precision
                int toAdd;
                if (burnedNow >= timer) {
                    toAdd = (int) Math.round(acc);
                    acc = 0.0;
                } else {
                    toAdd = (int) Math.floor(acc);
                    if (toAdd > 0) acc -= toAdd;
                }

                if (toAdd > 0) {
                    EnergyFlow.push(nodeLoc, toAdd);
                }
                energyAccumulator.put(furnaceLoc, acc);

                fuelTimer.put(furnaceLoc, timer - burnedNow);

                if (!data.isLit()) {
                    data.setLit(true);
                    block.setBlockData(data);
                }

                if (log) {
                    ConsoleLogger.info(
                            "[GENERATOR] +" + toAdd +
                                    " energy from " + furnaceLoc +
                                    " (remaining timer: " + (timer - burnedNow) + " ticks)"
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

                    // Reset timer and accumulator (the first run burns up to TICKS_PER_RUN ticks)
                    int firstBurn = Math.min(TICKS_PER_RUN, burnDuration);
                    fuelTimer.put(furnaceLoc, burnDuration - firstBurn);
                    energyAccumulator.put(furnaceLoc, 0.0);

                    // First-run energy
                    double energyPerTickDouble = (double) totalEnergyPerFuel / effectiveBurn;
                    double firstEnergy = energyPerTickDouble * firstBurn;
                    int firstAdd = (int) Math.floor(firstEnergy);
                    double rem = firstEnergy - firstAdd;
                    if (firstAdd > 0) {
                        EnergyFlow.push(nodeLoc, firstAdd);
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
    // Energy delivery is delegated to EnergyFlow.push() — the unified
    // network traversal. It routes energy to the chargeable batteries,
    // traverses THROUGH full/mode-blocked batteries (no dead ends) and
    // marks the cables along the way (blink + multimeter).
    // =========================

}