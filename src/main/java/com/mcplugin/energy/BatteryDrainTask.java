package com.mcplugin.energy;

import com.mcplugin.Main;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;

import com.mcplugin.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class BatteryDrainTask extends BukkitRunnable {

    @Override
    public void run() {

        FileConfiguration cfg =
                Main.getInstance().getConfig();

        // =========================
        // ENABLE CHECK
        // =========================
        if (!cfg.getBoolean(
                "energy.battery_drain.enabled",
                true
        )) {
            return;
        }

        int transferAmount =
                cfg.getInt(
                        "energy.battery_drain.transfer_per_tick",
                        5
                );

        int maxBatteryEnergy =
                cfg.getInt(
                        "energy.battery.max_energy",
                        10000
                );

        int dischargeAmount =
                cfg.getInt(
                        "energy.battery.discharge_per_tick",
                        10
                );

        // Smooth charge settings
        boolean smoothEnabled =
                cfg.getBoolean(
                        "energy.battery.smooth_charge.enabled",
                        true
                );

        double chargeMultiplier =
                cfg.getDouble(
                        "energy.battery.smooth_charge.charge_multiplier",
                        2.0
                );

        double dischargeMultiplier =
                cfg.getDouble(
                        "energy.battery.smooth_charge.discharge_multiplier",
                        0.5
                );

        boolean log =
                cfg.getBoolean(
                        "energy.battery_drain.log",
                        false
                );

        // =========================
        // SAFE COPY
        // =========================
        Set<CableNode> nodes =
                new HashSet<>(
                        CableNetwork.getAllNodes()
                );

        // =========================
        // BATTERY LOOP
        // =========================
        for (CableNode battery : nodes) {

            if (battery == null) {
                continue;
            }

            // =========================
            // ONLY BATTERIES
            // =========================
            if (battery.getType() != NodeType.BATTERY) {
                continue;
            }

            Location batteryLoc =
                    LocationUtil.normalize(
                            battery.getLocation()
                    );

            // =========================
            // APPLY MAX ENERGY CAP FROM CONFIG
            // =========================
            battery.setMaxEnergy(maxBatteryEnergy);

            // Fill ratio: 0.0 = empty, 1.0 = full
            double fillRatio =
                    (double) battery.getEnergy()
                            / Math.max(maxBatteryEnergy, 1);

            Block batteryBlock = batteryLoc.getBlock();
            boolean isRedstonePowered = batteryBlock.isBlockPowered()
                    || batteryBlock.isBlockIndirectlyPowered();

            // =========================
            // DISCHARGE MODE — smooth dynamic rate
            // Starts slow when empty, speeds up as battery fills
            // =========================
            if (isRedstonePowered && battery.getEnergy() > 0) {

                int dynamicDischarge = dischargeAmount;
                if (smoothEnabled) {
                    // Empty (fillRatio~0): factor = dischargeMultiplier (e.g. 0.5x)
                    // Full (fillRatio=1):  factor = 1.0 (base rate)
                    double factor = dischargeMultiplier + (1.0 - dischargeMultiplier) * fillRatio;
                    dynamicDischarge = Math.max(1, (int) (dischargeAmount * factor));
                }

                for (Location targetLoc : battery.getConnections()) {
                    if (targetLoc == null) continue;

                    CableNode target = CableNetwork.getNode(targetLoc);
                    if (target == null) continue;

                    if (target.getType() == NodeType.BATTERY) continue;

                    if (!LocationUtil.isFullyConnected(batteryLoc, targetLoc)) continue;
                    if (!battery.isConnected(targetLoc) || !target.isConnected(batteryLoc)) continue;

                    int available = battery.getEnergy();
                    if (available <= 0) break;

                    int transfer = Math.min(
                            Math.min(dynamicDischarge, available),
                            target.getMaxEnergy() - target.getEnergy()
                    );

                    if (transfer <= 0) continue;

                    battery.removeEnergy(transfer);
                    target.addEnergy(transfer);

                    CableNetwork.saveNode(battery);
                    CableNetwork.saveNode(target);

                    if (log) {
                        Main.getInstance().getLogger().info(
                                "[Battery] Discharged " + transfer +
                                        " energy from " + batteryLoc + " to " + targetLoc
                        );
                    }
                }
            }

            // =========================
            // CHARGE MODE — battery charges from nearby cables (always works)
            // =========================
            for (Location fromLoc : battery.getConnections()) {

                if (fromLoc == null) {
                    continue;
                }

                Location normalizedFrom =
                        LocationUtil.normalize(fromLoc);

                CableNode from =
                        CableNetwork.getNode(normalizedFrom);

                if (from == null) {
                    continue;
                }

                // =========================
                // PREVENT BATTERY ↔ BATTERY
                // =========================
                if (from.getType() == NodeType.BATTERY) {
                    continue;
                }

                // =========================
                // REAL CONNECTION CHECK
                // =========================
                if (!LocationUtil.isFullyConnected(
                        normalizedFrom,
                        batteryLoc
                )) {
                    continue;
                }

                // =========================
                // DOUBLE LINK CHECK
                // =========================
                if (!from.isConnected(batteryLoc)
                        || !battery.isConnected(normalizedFrom)) {

                    continue;
                }

                int available =
                        from.getEnergy();

                if (available <= 0) {
                    continue;
                }

                int space =
                        maxBatteryEnergy - battery.getEnergy();

                if (space <= 0) {
                    continue;
                }

                // Dynamic transfer rate based on fill ratio (capacitor-like)
                int dynamicTransfer = transferAmount;
                if (smoothEnabled) {
                    // Empty (fillRatio=0): factor = 1 + chargeMultiplier (e.g. 3x)
                    // Full (fillRatio=1):  factor = 1.0 (base rate)
                    double factor = 1.0 + chargeMultiplier * (1.0 - fillRatio);
                    dynamicTransfer = Math.max(1, (int) (transferAmount * factor));
                }

                int transfer =
                        Math.min(
                                Math.min(dynamicTransfer, available),
                                space
                        );

                if (transfer <= 0) {
                    continue;
                }

                from.removeEnergy(transfer);
                battery.addEnergy(transfer);

                CableNetwork.saveNode(from);
                CableNetwork.saveNode(battery);

                if (log) {
                    Main.getInstance()
                            .getLogger()
                            .info(
                                    "[BatteryDrain] "
                                            + transfer
                                            + " energy moved from "
                                            + normalizedFrom
                                            + " to battery "
                                            + batteryLoc
                            );
                }
            }
        }
    }
}