package com.ultimateimprovments.energy.machines.assembler;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

/**
 * Utility for checking if a craft event happens inside a Crafter block.
 * <p>
 * The old "Item Assembler" structure (assembled Crafter + energy buffer) was
 * removed — custom items now craft directly in the vanilla Crafter block.
 */
public class AssemblerChecker {

    private AssemblerChecker() {}

    /**
     * @return true if the craft event happens inside any Crafter block inventory
     */
    public static boolean isAssemblerCraft(PrepareItemCraftEvent e) {
        return e.getInventory().getType() == InventoryType.CRAFTER;
    }
}
