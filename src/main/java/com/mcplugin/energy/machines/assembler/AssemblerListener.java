package com.mcplugin.energy.machines.assembler;

import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.infrastructure.util.LocationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public class AssemblerListener implements Listener {

    private static final Component ASSEMBLER_TITLE = Component.text("Item assembler");

    @EventHandler
    public void onWorkbenchInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.CRAFTING_TABLE) return;

        Location loc = LocationUtil.normalize(e.getClickedBlock().getLocation());
        if (!EnergyWorkbenchManager.exists(loc)) return;

        Player player = e.getPlayer();
        e.setCancelled(true);

        // Open custom WORKBENCH inventory with "Item assembler" title
        Inventory inv = Bukkit.createInventory(null, InventoryType.WORKBENCH, ASSEMBLER_TITLE);
        player.openInventory(inv);
    }

}
