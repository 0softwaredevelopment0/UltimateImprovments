package com.ultimateimprovments.enchantment.veinminer;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * EnchantmentSyncListener — detects tools carrying the VeinMiner enchantment and
 * keeps the PDC mirror ({@code ui:veinminer_level}) in sync with the real enchantment.
 * <p>
 * This is the failsafe backbone: whenever a tool with the charm is seen, its
 * level (always 1) is mirrored into PDC, so a datapack crash never loses the
 * charm. When the datapack comes back, PDC-only tools get the real charm re-applied.
 * <p>
 * Sync triggers:
 * <ul>
 *   <li>item pickup ({@link EntityPickupItemEvent}) — full sync;</li>
 *   <li>inventory click / drag — mirror-only (no item mutation mid-click);</li>
 *   <li>grindstone result taken — the PDC mirror is cleared, so a legitimate
 *       disenchantment isn't undone by the failsafe;</li>
 *   <li>player join (full inventory sweep);</li>
 *   <li>periodic scan of all online players (every {@value #SCAN_INTERVAL_TICKS} ticks).</li>
 * </ul>
 */
public class EnchantmentSyncListener implements Listener {

    /** Periodic scan interval: 5 minutes (300 seconds). */
    private static final long SCAN_INTERVAL_TICKS = 20L * 60L * 5L;

    /** Grindstone result slot index. */
    private static final int GRINDSTONE_RESULT_SLOT = 2;

    // ─────────────────────────────────────────────────────────────
    //  EVENTS
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Enchantment.syncItem(event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Grindstone result taken → the charm was legitimately disenchanted.
        // Clear the PDC mirror so the failsafe doesn't re-apply it later.
        if (event.getInventory() instanceof GrindstoneInventory
                && event.getSlotType() == InventoryType.SlotType.RESULT) {
            Enchantment.clearPdcMirror(event.getCurrentItem());
            return;
        }

        // Mirror-only: safe to call, no stack mutation in the re-apply direction.
        Enchantment.mirrorItem(event.getCurrentItem());
        Enchantment.mirrorItem(event.getCursor());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        for (ItemStack item : event.getNewItems().values()) {
            Enchantment.mirrorItem(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        syncInventory(event.getPlayer());
    }

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** Syncs every tool in the player's inventory (slots, armor, offhand, cursor). */
    public static void syncInventory(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerInventory inv = player.getInventory();

        // Storage slots (36)
        for (ItemStack item : inv.getStorageContents()) {
            Enchantment.syncItem(item);
        }
        // Armor + offhand
        for (ItemStack item : inv.getArmorContents()) {
            Enchantment.syncItem(item);
        }
        Enchantment.syncItem(inv.getItemInOffHand());
        Enchantment.syncItem(inv.getItemInMainHand());
        // Cursor (open inventory screen)
        Enchantment.syncItem(player.getOpenInventory().getCursor());
    }

    /** Periodic sweep of every online player. */
    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncInventory(player);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers the listener and starts the periodic scan task.
     */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentSyncListener(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentSyncListener::sweepAllPlayers,
                SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
    }
}
