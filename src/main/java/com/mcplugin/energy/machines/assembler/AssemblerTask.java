package com.mcplugin.energy.machines.assembler;

import com.mcplugin.core.Main;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically scans all Item Creators and attempts to craft items
 * from their 3x3 grid when:
 * - Buffer is FULL (100⚡)
 * - CRAFTER is triggered by redstone pulse
 * - A matching custom recipe exists
 * <p>
 * Буфер заряжается через adjacent cables от {@link EnergyWorkbenchManager#chargeAllBuffers()}.
 * После крафта буфер обнуляется (0/100), CRAFTER разблокируется.
 */
public class AssemblerTask extends BukkitRunnable {

    private static final int BUFFER_REQUIRED = 100;

    @Override
    public void run() {
        for (Location crafterLoc : AssemblerManager.getActiveAssemblers()) {
            if (crafterLoc == null) continue;

            Block block = crafterLoc.getBlock();
            if (block.getType() != Material.CRAFTER) continue;

            // === REDSTONE CHECK: only craft if crafter is triggered (by redstone pulse) ===
            if (!(block.getBlockData() instanceof Crafter crafterData)) continue;
            if (!crafterData.isTriggered()) continue;

            // === ENERGY CHECK: buffer must be FULL (100) ===
            int buffer = EnergyWorkbenchManager.getBufferEnergy(crafterLoc);
            if (buffer < BUFFER_REQUIRED) continue;

            // === INVENTORY CHECK ===
            org.bukkit.block.Crafter crafterState = null;
            try {
                var state = block.getState();
                if (state instanceof org.bukkit.block.Crafter cs) {
                    crafterState = cs;
                }
            } catch (Exception e) {
                continue;
            }
            if (crafterState == null) continue;

            var inventory = crafterState.getInventory();
            if (inventory == null) continue;

            // Build item matrix from 9 slots (0-8 = 3x3 grid)
            ItemStack[] matrix = new ItemStack[9];
            boolean hasItems = false;
            for (int i = 0; i < 9; i++) {
                matrix[i] = inventory.getItem(i);
                if (matrix[i] != null && !matrix[i].getType().isAir()) {
                    hasItems = true;
                }
            }
            if (!hasItems) continue;

            // === RECIPE CHECK: match against ItemCreatorRecipe ===
            ItemCreatorRecipe.Recipe recipe = ItemCreatorRecipe.match(matrix);
            if (recipe == null) continue;

            ItemStack result = recipe.result();
            if (result == null || result.getType().isAir()) continue;

            // === CRAFT! ===
            // Consume ALL buffer energy (100⚡)
            EnergyWorkbenchManager.consumeBufferEnergy(crafterLoc, BUFFER_REQUIRED);

            // Consume ingredients from the grid (1 each)
            for (int i = 0; i < 9; i++) {
                ItemStack item = inventory.getItem(i);
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

            // Eject result next to the crafter
            ItemStack toDrop = result.clone();
            if (crafterLoc.getWorld() != null) {
                crafterLoc.getWorld().dropItemNaturally(
                        crafterLoc.clone().add(0.5, 1.0, 0.5),
                        toDrop
                );
            }

            // Reset triggered state — wait for next redstone pulse
            crafterData.setTriggered(false);
            block.setBlockData(crafterData, false);
        }
    }
}
