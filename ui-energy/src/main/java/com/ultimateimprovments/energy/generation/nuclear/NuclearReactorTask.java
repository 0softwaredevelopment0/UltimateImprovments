package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.StructureTemplate;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * One-second pass over every assembled compact nuclear reactor.
 * <p>
 * Gates, in order: chunk loaded → template intact → redstone-powered center
 * block → cable under the bottom port → fuel available. Only when all of them
 * are open is energy pushed into the network, and the fuel countdown advances
 * <b>proportionally to the accepted energy</b>: a full or missing network never
 * wastes an emerald, and a partial push never produces free energy.
 * <p>
 * There is no heat, meltdown or radiation — the reactor is deliberately safe.
 */
public class NuclearReactorTask extends BukkitRunnable {

    /** The task runs once per second; all rates below are per-second. */
    public static final int PASS_TICKS = 20;

    @Override
    public void run() {
        Main main = Main.getInstance();
        if (main == null) return;

        FileConfiguration cfg = main.getConfig();
        if (!cfg.getBoolean("nuclear_reactor.enabled", true)) return;

        int energyPerSecond = Math.max(0,
                cfg.getInt("nuclear_reactor.energy_per_second", 1000));
        if (energyPerSecond <= 0) return;

        int fuelIntervalSeconds = Math.max(1,
                cfg.getInt("nuclear_reactor.fuel_interval_seconds", 10));
        int fuelTicksPerItem = fuelIntervalSeconds * 20;
        Material fuel = NuclearReactorManager.fuelMaterial();
        boolean log = cfg.getBoolean("nuclear_reactor.log", false);

        StructureTemplate template = StructureTemplate.get(NuclearReactorManager.TEMPLATE_NAME);

        for (NuclearReactor reactor : NuclearReactorManager.getReactors()) {
            if (reactor == null || reactor.getAnchor() == null
                    || reactor.getAnchor().getWorld() == null) {
                continue;
            }
            if (!reactor.isChunkLoaded()) continue;

            // 1. Structure integrity — any broken template cell stops the reactor.
            if (template == null || !template.checkAt(reactor.getAnchor()).matched()) {
                apply(reactor, NuclearReactor.Status.DAMAGED, log);
                continue;
            }

            // 2. Redstone gate — the reinforced deepslate in the center must be
            //    powered. A lever/button on top of the barrel strongly powers
            //    the barrel, which weakly powers the adjacent center; checking
            //    the barrel too keeps the gate working regardless of how the
            //    server maps weak power. (Glass panes cannot hold a lever.)
            Block center = reactor.getCenterBlock();
            Block barrel = reactor.getBarrelBlock();
            boolean powered = center.isBlockPowered()
                    || center.isBlockIndirectlyPowered()
                    || barrel.isBlockPowered()
                    || barrel.isBlockIndirectlyPowered();
            if (!powered) {
                apply(reactor, NuclearReactor.Status.UNPOWERED, log);
                continue;
            }

            // 3. The bottom copper port must touch a cable node.
            CableNode node = NuclearReactorManager.findPortNode(reactor.getEnergyPortLocation());
            if (node == null) {
                apply(reactor, NuclearReactor.Status.NO_CABLE, log);
                continue;
            }

            // 4. Fuel — one emerald starts a fresh cycle.
            if (!reactor.hasFuel()
                    && !reactor.takeFuelItem(fuel, fuelTicksPerItem)) {
                apply(reactor, NuclearReactor.Status.NO_FUEL, log);
                continue;
            }

            // 5. Push. On the last fraction of a cycle only that fraction of the
            //    per-second output is offered, so an emerald is never over-spent.
            int request = energyPerSecond;
            if (reactor.getFuelTicksLeft() < PASS_TICKS) {
                request = Math.max(1, (int) Math.round(
                        (double) energyPerSecond * reactor.getFuelTicksLeft() / PASS_TICKS));
            }

            int accepted = EnergyFlow.push(node.getLocation(), request);
            if (accepted <= 0) {
                apply(reactor, NuclearReactor.Status.OUTPUT_BLOCKED, log);
                continue;
            }

            int burnedTicks = (int) Math.round(
                    (double) PASS_TICKS * accepted / energyPerSecond);
            reactor.burnFuelTicks(Math.min(PASS_TICKS, burnedTicks));
            apply(reactor, NuclearReactor.Status.RUNNING, log);
        }
    }

    private static void apply(NuclearReactor reactor, NuclearReactor.Status status, boolean log) {
        if (reactor.setStatus(status) && log) {
            ConsoleLogger.info("[NuclearReactor] " + reactor.getAnchor().getBlockX() + " "
                    + reactor.getAnchor().getBlockY() + " "
                    + reactor.getAnchor().getBlockZ() + " -> " + status);
        }
    }
}
