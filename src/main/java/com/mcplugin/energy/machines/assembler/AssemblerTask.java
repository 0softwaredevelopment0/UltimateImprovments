package com.mcplugin.energy.machines.assembler;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Periodically scans all assembled auto-crafters and attempts to craft items
 * from their 3x3 grid, consuming energy from the cable network.
 */
public class AssemblerTask extends BukkitRunnable {

    @Override
    public void run() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        if (!cfg.getBoolean("energy_crafting.enabled", true)) {
            return;
        }

        int energyPerCraft = cfg.getInt("energy_crafting.energy_per_craft", 100);

        Collection<Location> assemblers = AssemblerManager.getActiveAssemblers();

        for (Location crafterLoc : assemblers) {
            if (crafterLoc == null) continue;

            Block block = crafterLoc.getBlock();
            if (block.getType() != Material.CRAFTER) continue;

            // Get the crafter's inventory (block state)
            org.bukkit.block.Crafter crafterState = null;
            try {
                var state = block.getState();
                if (state instanceof org.bukkit.block.Crafter cs) {
                    crafterState = cs;
                }
            } catch (Exception ignored) {}
            if (crafterState == null) continue;

            var inventory = crafterState.getInventory();
            if (inventory == null) continue;

            // Build item matrix from the 9 slots (0-8 = 3x3 grid)
            org.bukkit.inventory.ItemStack[] matrix = new org.bukkit.inventory.ItemStack[9];
            boolean hasItems = false;
            for (int i = 0; i < 9; i++) {
                matrix[i] = inventory.getItem(i);
                if (matrix[i] != null && !matrix[i].getType().isAir()) {
                    hasItems = true;
                }
            }
            if (!hasItems) continue;

            // Try to find a matching recipe
            org.bukkit.inventory.Recipe recipe;
            try {
                recipe = Main.getInstance().getServer().getCraftingRecipe(matrix, crafterLoc.getWorld());
            } catch (Exception e) {
                continue;
            }
            if (recipe == null) continue;

            org.bukkit.inventory.ItemStack result = recipe.getResult();
            if (result == null || result.getType().isAir()) continue;

            // Check cable connection and energy
            CableNode start = findConnectedNode(crafterLoc);
            if (start == null) continue;

            if (!hasNetworkEnergy(start, energyPerCraft)) continue;

            // Consume energy
            takeNetworkEnergy(start, energyPerCraft);

            // Consume ingredients from the grid
            for (int i = 0; i < 9; i++) {
                org.bukkit.inventory.ItemStack item = inventory.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    int newAmount = item.getAmount() - 1;
                    if (newAmount <= 0) {
                        inventory.setItem(i, null);
                    } else {
                        item.setAmount(newAmount);
                        inventory.setItem(i, item);
                    }
                }
            }

            // Eject result item at the crafter's location
            org.bukkit.inventory.ItemStack toDrop = result.clone();
            crafterLoc.getWorld().dropItemNaturally(
                    crafterLoc.clone().add(0.5, 1.0, 0.5),
                    toDrop
            );
        }
    }

    // =========================
    // FIND CONNECTED CABLE NODE
    // =========================
    private CableNode findConnectedNode(Location crafterLoc) {
        for (Location near : LocationUtil.getNeighbors(crafterLoc)) {
            Location norm = LocationUtil.normalize(near);
            if (norm == null) continue;
            CableNode node = CableNetwork.getNode(norm);
            if (node != null
                    && LocationUtil.isFullyConnected(crafterLoc, norm)) {
                return node;
            }
        }
        return null;
    }

    // =========================
    // NETWORK ENERGY CHECK
    // =========================
    private boolean hasNetworkEnergy(CableNode start, int amount) {
        if (start == null || amount <= 0) return false;

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getLocation());

        int total = 0;
        while (!queue.isEmpty()) {
            CableNode node = queue.poll();
            if (node == null) continue;

            // Уважаем режим батареи: проверяем только DISCHARGE/CHARGE_DISCHARGE
            if (node.getType() == com.mcplugin.energy.transfer.cable.NodeType.BATTERY) {
                BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                if (bc != null && !bc.canDischarge()) continue;
            }

            total += node.getEnergy();

            for (Location conn : node.getConnections()) {
                if (visited.contains(conn)) continue;
                CableNode next = CableNetwork.getNode(conn);
                if (next == null) continue;
                visited.add(conn);
                queue.add(next);
            }
        }

        return total >= amount;
    }

    // =========================
    // CONSUME NETWORK ENERGY
    // =========================
    private void takeNetworkEnergy(CableNode start, int amount) {
        if (start == null || amount <= 0) return;

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getLocation());

        int remaining = amount;
        while (!queue.isEmpty() && remaining > 0) {
            CableNode node = queue.poll();
            if (node == null) continue;

            // Уважаем режим батареи: берём только из DISCHARGE/CHARGE_DISCHARGE
            if (node.getType() == com.mcplugin.energy.transfer.cable.NodeType.BATTERY) {
                BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                if (bc != null && !bc.canDischarge()) continue;
            }

            int energy = node.getEnergy();
            if (energy > 0) {
                int take = Math.min(energy, remaining);
                node.removeEnergy(take);
                remaining -= take;
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
