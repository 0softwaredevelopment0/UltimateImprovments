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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

/**
 * InventoryLockListener — while an item carries the Curse of Self-Destruct it is
 * STUCK in the PLAYER's inventory for the whole 30-second countdown. Every known
 * way of removing it is cancelled:
 * <ul>
 *   <li>{@link InventoryClickEvent} — clicks, shift-clicks, armor swap, anvil /
 *       grindstone / crafting transfers;</li>
 *   <li>{@link InventoryDragEvent} — drag-splitting that involves the stack;</li>
 *   <li>{@link PlayerDropItemEvent} — Q / throwing the item on the ground;</li>
 *   <li>{@link PlayerSwapHandItemsEvent} — F swap between main and off hand;</li>
 *   <li>{@link InventoryMoveItemEvent} — hoppers pulling the stack out of the
 *       player's inventory;</li>
 *   <li>{@link PlayerDeathEvent} — cursed items never drop on death.</li>
 * </ul>
 * <p>
 * <b>Scope:</b> the lock applies ONLY to the player's own inventory. A cursed item
 * sitting in a chest / barrel / any foreign container is NOT locked — the player may
 * freely take it (which starts the countdown) or rearrange the container. Moving a
 * cursed stack INTO the player's own inventory is always allowed; only removing it
 * from there is blocked.
 */
public class InventoryLockListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean ownInventory = isOwnInventory(event);

        // A cursed stack clicked in the PLAYER's OWN inventory is stuck.
        if (ownInventory && Enchantment.isCursed(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        // A cursed stack held on the cursor must not be stashed into a FOREIGN
        // inventory (that would remove it from the player); putting it back into the
        // player's own inventory is fine — the countdown just keeps running.
        if (!ownInventory && Enchantment.isCursed(event.getCursor())) {
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

        // Drags hold the moved stack on the cursor. If that stack is cursed it must
        // not land in a FOREIGN inventory; dropping it back into the player's own
        // inventory is allowed.
        if (!Enchantment.isCursed(event.getOldCursor())) return;

        Inventory bottom = event.getView().getBottomInventory();
        for (int rawSlot : event.getNewItems().keySet()) {
            Inventory target = event.getView().getInventory(rawSlot);
            if (target != null && !target.equals(bottom)) {
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
        if (!Enchantment.isCursed(event.getItem())) return;
        // Only the player's OWN inventory is locked — a container holding a cursed
        // item stays fully accessible to hoppers and other automation.
        if (event.getSource() instanceof PlayerInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getDrops().isEmpty()) return;
        event.getDrops().removeIf(Enchantment::isCursed);
    }

    /** True when the clicked slot belongs to the player's OWN inventory (the bottom). */
    private static boolean isOwnInventory(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        return clicked != null && clicked.equals(event.getView().getBottomInventory());
    }
}
