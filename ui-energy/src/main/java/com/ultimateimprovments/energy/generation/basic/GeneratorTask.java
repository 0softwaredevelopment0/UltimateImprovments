package com.ultimateimprovments.energy.generation.basic;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.ConsoleLogger;

import com.ultimateimprovments.util.Materials;
import org.bukkit.Location;
import org.bukkit.World;
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
     * The task runs every {@value #PASS_TICKS} ticks (2s). All burn/energy
     * math below is scaled by this period, so the per-second fuel burn and
     * energy output stay exactly the same as with the old per-tick schedule.
     */
    public static final int PASS_TICKS = 40;

    // =========================
    // BURN TRACKING (per furnace location)
    // fuelTimer: how many ticks the current fuel has left to burn (decrements every run, by up to PASS_TICKS)
    // energyAccumulator: accumulated fractional energy (>= 1 → added to the battery)
    // Generator works independently of furnace smelting state.
    // =========================
    private final Map<Location, Integer> fuelTimer = new HashMap<>();
    private final Map<Location, Double> energyAccumulator = new HashMap<>();

    /** Live task instance (re-created on every UIEnergy startup/soft reload). */
    private static GeneratorTask instance;

    public GeneratorTask() {
        instance = this;
    }

    /**
     * Burn ticks left on the currently burning fuel item at this generator
     * (0 when idle). Frozen while the redstone gate or the buffer gate holds
     * the generator — the value does not decay during a freeze.
     */
    public static int burnTicksLeft(Location furnaceLoc) {
        GeneratorTask task = instance;
        if (task == null || furnaceLoc == null) return 0;
        Integer timer = task.fuelTimer.get(LocationUtil.normalize(furnaceLoc));
        return timer != null ? Math.max(0, timer) : 0;
    }

    @Override
    public void run() {

        FileConfiguration cfg = Main.getInstance().getConfig();

        if (!cfg.getBoolean("energy.generator.enabled", true)) {
            return;
        }

        int totalEnergyPerFuel =
                cfg.getInt("energy.generator.energy_per_fuel", 100);

        int burnDuration =
                cfg.getInt("energy.generator.fuel_burn_ticks", 200);

        boolean log =
                cfg.getBoolean("energy.generator.log", false);

        // =========================
        // CALCULATE PER-TICK ENERGY
        // =========================
        int effectiveBurn = Math.max(burnDuration, 1);

        // =========================
        // CLEANUP: Remove entries for disassembled/broken generators
        // (generators in unloaded chunks are KEPT — touching their blocks
        // would sync-load the chunk every pass)
        // =========================
        fuelTimer.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Materials.BLAST_FURNACE;
        });

        energyAccumulator.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }
            return !GeneratorManager.isAssembled(loc)
                    || loc.getBlock().getType() != Materials.BLAST_FURNACE;
        });

        // =========================
        // ONLY PROCESS ASSEMBLED GENERATORS
        // =========================
        Collection<Location> generators = GeneratorManager.getActiveGenerators();

        for (Location furnaceLoc : generators) {

            if (furnaceLoc == null) continue;

            World world = furnaceLoc.getWorld();
            if (world == null || !world.isChunkLoaded(furnaceLoc.getBlockX() >> 4, furnaceLoc.getBlockZ() >> 4)) {
                continue; // unloaded chunk: the generator resumes when its chunk loads
            }

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

            // =========================
            // REDSTONE GATE — the generator works ONLY while its furnace
            // block is powered by redstone. Unpowered: no fuel consumption,
            // no energy output; the burn timer freezes (the already-burning
            // fuel resumes where it stopped when power returns). No on/off
            // messages — the furnace visual (lit/unlit) shows the state.
            // =========================
            if (!block.isBlockPowered() && !block.isBlockIndirectlyPowered()) {
                if (data.isLit()) {
                    data.setLit(false);
                    block.setBlockData(data);
                }
                continue;
            }

            // =========================
            // BUFFER SYNC + BUFFER GATE — the generator's own GENERATOR-typed
            // node buffers the energy nobody took. Every pass:
            //   1. Re-offer the buffered energy to the network (batteries get
            //      it first): while ANY battery in the connected network has
            //      room, the buffer drains into it — a full-ish buffer does
            //      not stall the generator when consumers sit behind
            //      batteries.
            //   2. Freeze when the buffer still has no room for one more
            //      production pass: producing more would silently lose the
            //      surplus AND waste fuel. The burn timer freezes where it
            //      is (like the redstone gate), the furnace goes unlit, and
            //      burning resumes the moment a consumer pull (reactor
            //      junctions, furnace, lamp, accelerator, ...) drains the
            //      buffer through the network.
            // =========================
            GeneratorManager.ensureGeneratorNode(furnaceLoc);
            CableNode genNode = CableNetwork.getNode(furnaceLoc);
            if (genNode != null) {
                int buffered = genNode.getEnergy();
                if (buffered > 0) {
                    int moved = EnergyFlow.push(nodeLoc, buffered);
                    if (moved > 0) {
                        genNode.removeEnergy(moved);
                        // Activity trail: the energy moved from the buffer
                        // into the network — show it on the multimeter.
                        genNode.addTransferred(moved);
                    }
                }

                int maxPerPass = (int) Math.ceil(
                        (double) totalEnergyPerFuel * PASS_TICKS / effectiveBurn);
                if (genNode.getMaxEnergy() - genNode.getEnergy() < maxPerPass) {
                    if (data.isLit()) {
                        data.setLit(false);
                        block.setBlockData(data);
                    }
                    if (log) {
                        ConsoleLogger.info("[GENERATOR] Buffer full at " + furnaceLoc
                                + " (" + genNode.getEnergy() + "/" + genNode.getMaxEnergy()
                                + " ⚡) — fuel consumption frozen until a consumer pulls");
                    }
                    continue;
                }
            }

            int timer = fuelTimer.getOrDefault(furnaceLoc, 0);

            // =========================
            // CASE 1: BURNING — generate energy continuously each run
            // =========================
            if (timer > 0) {
                int burnedNow = Math.min(PASS_TICKS, timer);
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
                    deliverEnergy(furnaceLoc, nodeLoc, toAdd);
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

                    // Reset timer and accumulator (the first run burns up to PASS_TICKS ticks)
                    int firstBurn = Math.min(PASS_TICKS, burnDuration);
                    fuelTimer.put(furnaceLoc, burnDuration - firstBurn);
                    energyAccumulator.put(furnaceLoc, 0.0);

                    // First-run energy
                    double energyPerTickDouble = (double) totalEnergyPerFuel / effectiveBurn;
                    double firstEnergy = energyPerTickDouble * firstBurn;
                    int firstAdd = (int) Math.floor(firstEnergy);
                    double rem = firstEnergy - firstAdd;
                    if (firstAdd > 0) {
                        deliverEnergy(furnaceLoc, nodeLoc, firstAdd);
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
    // ENERGY DELIVERY — batteries first, then the generator's own buffer.
    // EnergyFlow.push routes energy to the chargeable batteries through the
    // network (traverses THROUGH full/mode-blocked batteries, marks the
    // cables along the way). Whatever no battery takes is deposited into
    // the generator's own GENERATOR-typed node (a small leftover buffer):
    // any consumer pulling through the network — the reactor's case
    // junctions, the accelerator, ... — drains it, so a direct
    // generator → wires → consumer setup works WITHOUT a battery.
    // =========================
    private void deliverEnergy(Location furnaceLoc, Location cableLoc, int amount) {
        int accepted = EnergyFlow.push(cableLoc, amount);
        int leftover = amount - accepted;
        if (leftover <= 0) return;

        // The node is RAM-only — recreate it lazily (idempotent, a map
        // lookup when it already exists).
        GeneratorManager.ensureGeneratorNode(furnaceLoc);
        CableNode genNode = CableNetwork.getNode(furnaceLoc);
        if (genNode == null || genNode.getType() != NodeType.GENERATOR) return;

        genNode.addEnergy(leftover); // clamped to the buffer capacity

        // Activity trail: the energy DID move (into the buffer) — show it
        // on the multimeter and the blink even when no battery took it.
        genNode.addTransferred(leftover);
        CableNode cable = CableNetwork.getNode(cableLoc);
        if (cable != null) {
            CableNetwork.markFlowingKey(cableLoc.getWorld().getUID().toString(), cable.getKey());
            cable.addTransferred(leftover);
        }
    }

}