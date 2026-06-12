package com.mcplugin.energy;

import com.mcplugin.Main;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class CableLossTask extends BukkitRunnable {

    @Override
    public void run() {

        FileConfiguration config =
                Main.getInstance().getConfig();

        // =========================
        // ENABLE CHECK
        // =========================
        if (!config.getBoolean(
                "energy.cable.enabled",
                true
        )) {
            return;
        }

        int loss =
                config.getInt(
                        "energy.cable.loss_per_tick",
                        1
                );

        boolean log =
                config.getBoolean(
                        "energy.cable.log",
                        false
                );

        int maxCableEnergy =
                config.getInt(
                        "energy.cable.max_energy",
                        5000
                );

        // =========================
        // OVERLOAD SETTINGS
        // =========================
        boolean overloadEnabled =
                config.getBoolean(
                        "energy.cable.overload.enabled",
                        true
                );

        boolean overloadBreakBlocks =
                config.getBoolean(
                        "energy.cable.overload.break_blocks",
                        false
                );

        boolean overloadFire =
                config.getBoolean(
                        "energy.cable.overload.set_fire",
                        false
                );

        float overloadPower =
                (float) config.getDouble(
                        "energy.cable.overload.explosion_power",
                        2.0
                );

        // =========================
        // SAFE COPY
        // =========================
        Set<CableNode> nodes =
                new HashSet<>(
                        CableNetwork.getAllNodes()
                );

        // =========================
        // ENERGY LOSS + OVERLOAD CHECK
        // =========================
        for (CableNode node : nodes) {

            if (node == null) {
                continue;
            }

            // =========================
            // SKIP BATTERIES & GENERATORS
            // =========================
            if (node.getType() == NodeType.BATTERY || node.getType() == NodeType.GENERATOR) {
                continue;
            }

            // =========================
            // APPLY MAX ENERGY CAP FROM CONFIG
            // =========================
            node.setMaxEnergy(maxCableEnergy);

            Location nodeLoc = node.getLocation();
            Material type =
                    nodeLoc.getBlock()
                            .getType();

            int energy =
                    node.getEnergy();

            // =========================
            // OVERLOAD CHECK: energy >= maxEnergy → BOOM!
            // =========================
            int maxEnergy = node.getMaxEnergy();
            if (overloadEnabled && maxEnergy > 0 && energy >= maxEnergy) {

                World world = nodeLoc.getWorld();
                if (world != null) {
                    world.createExplosion(
                            nodeLoc.getX() + 0.5,
                            nodeLoc.getY() + 0.5,
                            nodeLoc.getZ() + 0.5,
                            overloadPower,
                            overloadFire,
                            overloadBreakBlocks
                    );
                }

                Main.getInstance().getLogger().warning(
                        "[CableOverload] Cable exploded at " + nodeLoc
                );

                // Remove the node entirely — it's gone
                CableNetwork.removeNode(nodeLoc);

                // Break the block as well
                nodeLoc.getBlock().setType(Material.AIR);

                continue;
            }

            if (energy <= 0) {
                continue;
            }

            int finalLoss;

            // =========================
            // CONFIG-BASED LOSS PER CABLE TYPE
            // =========================
            if (type == Material.WAXED_LIGHTNING_ROD) {
                // Straight cable — standard loss
                finalLoss = config.getInt(
                        "energy.cable.loss.lightning_rod",
                        loss
                );
            } else if (type == Material.WAXED_CHISELED_COPPER) {
                // Corner cable — reduced loss
                finalLoss = config.getInt(
                        "energy.cable.loss.chiseled_copper",
                        Math.max(1, loss / 2)
                );
            } else if (type == Material.WAXED_COPPER_GRATE) {
                // Battery block — uses battery config, skip loss here
                finalLoss = config.getInt(
                        "energy.cable.loss.copper_grate",
                        loss
                );
            } else {
                // Unknown block type
                finalLoss = loss;
            }

            // =========================
            // SAFE LOSS
            // =========================
            finalLoss =
                    Math.min(
                            finalLoss,
                            energy
                    );

            if (finalLoss <= 0) {
                continue;
            }

            node.removeEnergy(finalLoss);

            // =========================
            // SQLITE SAVE
            // =========================
            CableNetwork.saveNode(node);

            // =========================
            // LOG
            // =========================
            if (log) {

                Main.getInstance()
                        .getLogger()
                        .info(
                                "[CableLoss] Removed "
                                        + finalLoss
                                        + " energy from "
                                        + node.getLocation()
                        );
            }
        }
    }
}