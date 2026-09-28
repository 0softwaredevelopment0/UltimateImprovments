package com.ultimateimprovments.enchantment.disappearance;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * EnchantmentSyncListener — keeps the Disappearance PDC mirror
 * ({@code ui:disappearance_level}) in sync with the real datapack curse.
 * <p>
 * Same failsafe backbone as all other charms: pickup / click / drag / join /
 * periodic scan; grindstone result clears the mirror.
 */
public class EnchantmentSyncListener implements Listener {

    /** Periodic scan interval: 5 minutes. */
    private static final long SCAN_INTERVAL_TICKS = 20L * 60L * 5L;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Enchantment.syncItem(event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        if (event.getInventory() instanceof GrindstoneInventory
                && event.getSlotType() == InventoryType.SlotType.RESULT) {
            Enchantment.clearPdcMirror(event.getCurrentItem());
            return;
        }

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

    /** Syncs every item in the player's inventory (slots, armor, offhand, cursor). */
    public static void syncInventory(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getStorageContents()) {
            Enchantment.syncItem(item);
        }
        for (ItemStack item : inv.getArmorContents()) {
            Enchantment.syncItem(item);
        }
        Enchantment.syncItem(inv.getItemInOffHand());
        Enchantment.syncItem(inv.getItemInMainHand());
        Enchantment.syncItem(player.getOpenInventory().getCursor());
    }

    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncInventory(player);
        }
    }

    /** Registers the listener and starts the periodic scan task. */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentSyncListener(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentSyncListener::sweepAllPlayers,
                SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
    }
}
