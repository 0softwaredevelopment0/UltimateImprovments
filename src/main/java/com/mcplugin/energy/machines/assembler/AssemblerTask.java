package com.mcplugin.energy.machines.assembler;

import com.mcplugin.infrastructure.core.Main;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;

import java.util.Collection;

/**
 * Periodically scans all assembled auto-crafters and attempts to craft items
 * from their 3x3 grid, consuming energy from the internal buffer.
 * The buffer is charged via adjacent cables by {@link EnergyWorkbenchManager#chargeAllBuffers()}.
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

            // Check buffer energy (charged via cables by EnergyWorkbenchManager.chargeAllBuffers)
            if (!EnergyWorkbenchManager.hasBufferEnergy(crafterLoc, energyPerCraft)) continue;

            // Consume energy from buffer
            EnergyWorkbenchManager.consumeBufferEnergy(crafterLoc, energyPerCraft);

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


}
