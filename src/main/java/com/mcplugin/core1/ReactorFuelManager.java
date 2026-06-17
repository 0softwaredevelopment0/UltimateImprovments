package com.mcplugin.core1;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Управляет топливом реактора — бочки с алмазными и золотыми блоками.
 */
public class ReactorFuelManager {

    /**
     * Проверяет, есть ли в бочке достаточно топлива.
     */
    private static boolean checkBarrelForFuel(Location base, int dx, int dy, int dz, Material fuelType, int minCount) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Material.BARREL) return false;

        Barrel barrel = (Barrel) block.getState();
        Inventory inv = barrel.getInventory();

        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == fuelType && item.getAmount() >= minCount) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет наличие топлива в обеих бочках.
     */
    public static boolean hasBarrelFuel(Location reactorLocation) {
        if (reactorLocation == null) return false;
        Location base = reactorLocation;
        return checkBarrelForFuel(base, 0, -4, -2, Material.DIAMOND_BLOCK, 1)
            && checkBarrelForFuel(base, 0, -4, 2, Material.GOLD_BLOCK, 1);
    }

    /**
     * Забирает 1 единицу топлива из бочки.
     */
    public static boolean consumeBarrelFuel(Location base, int dx, int dy, int dz, Material fuelType) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Material.BARREL) return false;

        Barrel barrel = (Barrel) block.getState(false);
        Inventory inv = barrel.getInventory();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == fuelType) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    inv.setItem(i, item);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Потребляет топливо из обеих бочек.
     */
    public static void consumeBothBarrels(Location base) {
        consumeBarrelFuel(base, 0, -4, -2, Material.DIAMOND_BLOCK);
        consumeBarrelFuel(base, 0, -4, 2, Material.GOLD_BLOCK);
    }
}
