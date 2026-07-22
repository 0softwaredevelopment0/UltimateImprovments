package com.ultimateimprovments.energy.machines.assembler;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.InventoryView;

/**
 * Utility for checking if a craft event happens inside an Item Assembler GUI.
 */
public class AssemblerChecker {

    private static final Component ASSEMBLER_TITLE = Component.text("Создатель предметов");

    private AssemblerChecker() {}

    /**
     * @return true if the craft event is inside the Item Assembler GUI
     */
    public static boolean isAssemblerCraft(PrepareItemCraftEvent e) {
        if (e.getInventory().getType() != InventoryType.CRAFTER) return false;

        InventoryView view = e.getView();
        if (view == null) return false;

        return ASSEMBLER_TITLE.equals(view.title());
    }
}
