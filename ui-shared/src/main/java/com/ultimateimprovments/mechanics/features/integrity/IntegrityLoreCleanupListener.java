package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * 🧹 IntegrityLoreCleanupListener — removes the legacy "Integrity: N%" lore
 * line left on items by the removed custom integrity system.
 * <p>
 * The system is gone; the lore it stamped on items must disappear from the
 * game too. The listener sweeps items at the moments they enter or change
 * hands — the set of events below covers every realistic path an old item
 * can take to become visible or usable:
 * <ul>
 *   <li>{@link PlayerJoinEvent} — the whole inventory + armor + offhand + cursor;</li>
 *   <li>{@link PlayerItemHeldEvent} — hotbar swap to a dirty item;</li>
 *   <li>{@link InventoryOpenEvent} — every item in an opened chest/barrel/shulker;</li>
 *   <li>{@link InventoryClickEvent} / {@link InventoryDragEvent} — picking items up;</li>
 *   <li>{@link ItemSpawnEvent} — items dropped or spawned by any mechanism.</li>
 * </ul>
 * The strip is idempotent and non-destructive: only the line containing
 * "integrity:" (case-insensitive) is removed, everything else stays.
 */
public final class IntegrityLoreCleanupListener implements Listener {

    private IntegrityLoreCleanupListener() {}

    public static void init(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new IntegrityLoreCleanupListener(), plugin);
    }

    // ─────────────────────────────────────────────────────────────
    //  EVENT HANDLERS
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        cleanAll(player.getInventory().getContents());
        cleanAll(player.getInventory().getArmorContents());
        cleanAll(player.getInventory().getExtraContents());
        ItemStack cursor = player.getItemOnCursor();
        if (cleanOne(cursor)) player.setItemOnCursor(cursor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (cleanOne(item)) {
            event.getPlayer().getInventory().setItem(event.getNewSlot(), item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        cleanAll(event.getInventory().getContents());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // What the player is about to receive: clicked slot, hotbar-swap target, cursor.
        cleanOne(event.getCurrentItem());
        cleanOne(event.getHotbarButton() >= 0
                ? event.getView().getBottomInventory().getItem(event.getHotbarButton())
                : null);
        ItemStack cursor = event.getCursor();
        if (cleanOne(cursor)) event.setCursor(cursor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        cleanAll(event.getNewItems().values().toArray(new ItemStack[0]));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        cleanOne(event.getEntity().getItemStack());
    }

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Strips the lore from every non-null, non-air stack in the array (in place). */
    private static void cleanAll(ItemStack[] items) {
        if (items == null) return;
        for (ItemStack item : items) {
            cleanOne(item);
        }
    }

    /** Strips the lore from one stack in place; returns true if changed. */
    private static boolean cleanOne(ItemStack item) {
        return ItemDurabilityUtil.stripLegacyIntegrityLore(item);
    }
}
