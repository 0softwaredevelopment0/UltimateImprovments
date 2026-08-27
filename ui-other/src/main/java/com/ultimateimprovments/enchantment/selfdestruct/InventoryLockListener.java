package com.ultimateimprovments.enchantment.selfdestruct;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * InventoryLockListener — while an item carries the Curse of Self-Destruct it is
 * STUCK in the player's inventory for the whole 10-second countdown. Every known
 * way of removing it is cancelled:
 * <ul>
 *   <li>{@link InventoryClickEvent} — clicks, shift-clicks, armor swap, anvil /
 *       grindstone / crafting transfers (the cursed stack is either the clicked
 *       slot or the cursor);</li>
 *   <li>{@link InventoryDragEvent} — drag-splitting that involves the stack;</li>
 *   <li>{@link PlayerDropItemEvent} — Q / throwing the item on the ground;</li>
 *   <li>{@link PlayerSwapHandItemsEvent} — F swap between main and off hand;</li>
 *   <li>{@link InventoryMoveItemEvent} — hoppers pulling the stack out;</li>
 *   <li>{@link PlayerDeathEvent} — cursed items never drop on death.</li>
 * </ul>
 */
public class InventoryLockListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Clicks that involve the cursed stack as the clicked slot or the cursor.
        if (Enchantment.isCursed(event.getCurrentItem())
                || Enchantment.isCursed(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        // NUMBER_KEY (1-9 while hovering a slot): the cursed item may sit in the
        // hotbar being swapped — it is neither the clicked slot nor the cursor.
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            if (button >= 0 && button <= 8) {
                PlayerInventory inv = player.getInventory();
                if (Enchantment.isCursed(inv.getItem(button))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        for (ItemStack item : event.getNewItems().values()) {
            if (Enchantment.isCursed(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (Enchantment.isCursed(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (Enchantment.isCursed(event.getMainHandItem())
                || Enchantment.isCursed(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (Enchantment.isCursed(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getDrops().isEmpty()) return;
        event.getDrops().removeIf(Enchantment::isCursed);
    }
}
