package com.mcplugin.mechanics.security.anticheat.misc;

import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * PortalInventory — взаимодействие с инвентарём в портале (невозможно в ванилле).
 * Детекция: клик в инвентаре, находясь в незерском портале.
 */
public class PortalInventoryCheck extends AbstractCheck {

    public PortalInventoryCheck() {
        super("PortalInventory", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
    }

    @Override
    public void onReload() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!isEnabled() || isExempted(player)) return;

        // Check if player is standing in a nether portal
        Location loc = player.getLocation();
        if (loc.getBlock().getType() == org.bukkit.Material.NETHER_PORTAL) {
            CheckResult result = flag(player, 2.0,
                    "PortalInventory: clicked inventory while in portal");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }
    }
}
