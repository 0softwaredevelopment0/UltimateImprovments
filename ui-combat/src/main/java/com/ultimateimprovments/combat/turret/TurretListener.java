package com.ultimateimprovments.combat.turret;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Opens the turret chat GUI when a player shift + right-clicks an end crystal.
 * <p>
 * Both {@link PlayerInteractEntityEvent} and {@link PlayerInteractAtEntityEvent}
 * are handled because the client sends one or the other depending on the version,
 * and end crystals have no vanilla interaction of their own.
 */
public class TurretListener implements Listener {

    private static final String PERMISSION = "ui.command.turret";

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    private void handle(Player player, Entity clicked, EquipmentSlot hand,
                        org.bukkit.event.Cancellable event) {
        // Only the main hand, so OFF_HAND does not reopen the GUI.
        if (hand != EquipmentSlot.HAND) return;
        if (!(clicked instanceof EnderCrystal crystal)) return;
        if (!player.isSneaking()) return;

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to use this command!</red>"));
            return;
        }

        event.setCancelled(true);
        TurretManager.getInstance().select(player, crystal);
    }
}
