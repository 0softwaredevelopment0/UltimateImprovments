package com.mcplugin.energy.machines.assembler;

import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.util.LocationUtil;
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

    private static final Component ASSEMBLER_TITLE = Component.text("Создатель предметов");

    @EventHandler
    public void onWorkbenchInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.CRAFTER) return;

        Location loc = LocationUtil.normalize(e.getClickedBlock().getLocation());
        if (!EnergyWorkbenchManager.exists(loc)) return;

        Player player = e.getPlayer();
        e.setCancelled(true);

        // Track which CRAFTER this player is using (for energy buffer)
        EnergyWorkbenchManager.setPlayerWorkbench(player, loc);

        // Open custom CRAFTER inventory with "Создатель предметов" title
        Inventory inv = Bukkit.createInventory(null, InventoryType.CRAFTER, ASSEMBLER_TITLE);
        player.openInventory(inv);
    }

}
