package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.Materials;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.UUID;

/**
 * Handles note GUI events: clicks, closing, book editing, quit.
 *
 * ⚠ Uses NotesGUI.openPlayers to identify the GUI instead of getTitle(),
 *   because in Paper 1.21.x InventoryView.getTitle() returns a Component,
 *   not a String, so .equals() comparison is always false.
 */
public class NotesGUIListener implements Listener {

    // =========================
    // PLAYER EDIT BOOK EVENT (Done / Sign)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Integer noteNumber = NotesGUI.editingSlots.remove(uuid);

        if (noteNumber == null) return;

        // Restore the item that was in hand before opening the book editor
        NotesGUI.restorePending(player, uuid);

        // 🕐 5-second save cooldown — prevents save spam
        if (!NotesDatabase.checkSaveCooldown(uuid)) {
            // Too often — do not save, but still open the GUI
            Main.getInstance().getLogger().fine("[Notes] Save skipped (cooldown) for " + player.getName() + " slot #" + noteNumber);
        } else {
            // Save the content
            try {
                BookMeta newMeta = event.getNewBookMeta();
                String content = NotesGUI.joinPages(newMeta.getPages());
                NotesDatabase.saveNote(uuid, noteNumber, content);
            } catch (Exception e) {
                ConsoleLogger.warn("[Notes] Failed to save note: " + e.getMessage());
            }
        }

        // Return to the GUI
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                NotesGUI.openMainGUI(player);
            }
        }, 1L);
    }

    // =========================
    // 🛡 DRAG HANDLER — prevent dragging notes
    // =========================
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!NotesGUI.openPlayers.contains(uuid)) return;

        for (int slot : event.getRawSlots()) {
            if (slot < NotesGUI.GUI_SIZE) {
                event.setCancelled(true);
                player.setItemOnCursor(null);
                player.updateInventory();
                return;
            }
        }
    }

    // =========================
    // INVENTORY CLICK (protection + handling)
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // Check only via openPlayers — getTitle() does not work in Paper 1.21.x
        if (!NotesGUI.openPlayers.contains(uuid)) return;

        event.setCancelled(true);
        player.setItemOnCursor(null);
        player.updateInventory();

        if (event.getCurrentItem() == null
                || (event.getCurrentItem().getType() != Materials.WRITABLE_BOOK
                    && event.getCurrentItem().getType() != Materials.WRITTEN_BOOK)) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= NotesGUI.GUI_SIZE) return;
        int noteNumber = slot + 1;

        // Mark the transition, close the GUI and open the book editor
        NotesGUI.transitioningToBook.add(uuid);
        player.closeInventory();
        NotesGUI.openPlayers.remove(uuid);
        NotesGUI.openBookEditor(player, noteNumber);
    }

    // =========================
    // INVENTORY CLOSE
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // Transition from main GUI to the book editor — ignore
        if (NotesGUI.transitioningToBook.contains(uuid)) {
            return;
        }

        // Closing the main GUI — clean up note items from the inventory
        if (NotesGUI.openPlayers.contains(uuid)) {
            if (!NotesGUI.editingSlots.containsKey(uuid)) {
                NotesGUI.openPlayers.remove(uuid);
                removeNoteItems(player);
            }
            return;
        }

        // Escape from the book: player closed the book without saving
        if (NotesGUI.editingSlots.containsKey(uuid)) {
            // 🛡 ANTI-DUP: restore the old item into the hand and clean notes from the inventory
            NotesGUI.restorePending(player, uuid);
            removeNoteItems(player);
            NotesGUI.editingSlots.remove(uuid);
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    NotesGUI.openMainGUI(player);
                }
            }, 1L);
        }
    }

    // =========================
    // PLAYER QUIT — clean up state + restore the item in hand
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        NotesGUI.restorePending(player, uuid);
        NotesGUI.openPlayers.remove(uuid);
        NotesGUI.editingSlots.remove(uuid);
        NotesGUI.transitioningToBook.remove(uuid);
        removeNoteItems(player);
    }

    // =========================
    // ANTI-DUP: prevent note books from entering player inventory
    // =========================

    /** Remove all WRITABLE_BOOK items that look like note books from a player's inventory. */
    private void removeNoteItems(Player player) {
        // Main inventory (36 slots)
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isNoteBook(item)) {
                player.getInventory().setItem(i, null);
            }
        }
        // Equipment slots: main hand + off hand
        if (isNoteBook(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(null);
        }
        if (isNoteBook(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
        // Armor slots: check each individually (36= boots, 37= leggings, 38= chestplate, 39= helmet)
        // Use setItem directly on armor slots if a note book somehow ended up there
        for (int slot = 36; slot < 40; slot++) {
            ItemStack armor = player.getInventory().getItem(slot);
            if (armor != null && isNoteBook(armor)) {
                player.getInventory().setItem(slot, null);
            }
        }
        // Also clear cursor
        if (isNoteBook(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /** Check if an item is a note book (WRITABLE/WRITTEN_BOOK with a "Note #" display name). */
    private boolean isNoteBook(ItemStack item) {
        if (item == null
                || (item.getType() != Materials.WRITABLE_BOOK && item.getType() != Materials.WRITTEN_BOOK)) return false;
        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items;
        // getItemMeta() always returns non-null for WRITABLE_BOOK
        BookMeta meta = (BookMeta) item.getItemMeta();
        return meta != null && meta.hasDisplayName()
                && meta.getDisplayName().contains("Заметка #");
    }

    /** Cancel any WRITABLE_BOOK note items dropping on the ground. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (isNoteBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Cancel note book item entities spawning (catches items that somehow still spawn). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isNoteBook(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
