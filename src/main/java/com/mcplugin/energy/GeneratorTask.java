package com.mcplugin.energy;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
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
                    node.addEnergy(add);
                }

                burnTicks.put(furnaceLoc, remaining - 1);

                if (!data.isLit()) {
                    data.setLit(true);
                    block.setBlockData(data);
                }

                if (log) {
                    Main.getInstance().getLogger().info(
                            "[GENERATOR] +" + add +
                                    " energy at " + nodeLoc +
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
                        node.addEnergy(firstAdd);
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

}