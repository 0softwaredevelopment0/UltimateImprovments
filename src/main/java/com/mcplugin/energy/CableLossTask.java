package com.mcplugin.energy;

import com.mcplugin.Main;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;

import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Cable loss task — no-op for CABLE nodes (cables don't store energy anymore).
 * Only applies max energy cap to batteries (done in BatteryDrainTask).
 * Kept for config compatibility; does nothing meaningful.
 */
public class CableLossTask extends BukkitRunnable {

    @Override
    public void run() {

        FileConfiguration config =
                Main.getInstance().getConfig();

        if (!config.getBoolean("energy.cable.enabled", true)) {
            return;
        }

        // Cables no longer store energy — nothing to lose, nothing to overload.
        // Energy loss and overload mechanics have been removed.
        // Batteries handle their own energy management via BatteryDrainTask.
    }
}