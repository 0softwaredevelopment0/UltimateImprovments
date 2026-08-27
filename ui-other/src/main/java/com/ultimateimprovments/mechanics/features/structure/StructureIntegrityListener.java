package com.ultimateimprovments.mechanics.features.structure;

import com.ultimateimprovments.core.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener for the Structure Integrity Indicator item.
 * Shift+RMB on an ender chest shows structure data.
 */
public class StructureIntegrityListener implements Listener {

    private static final NamespacedKey KEY =
            new NamespacedKey(Main.getInstance(), "is_structure_integrity_indicator");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!e.getPlayer().isSneaking()) return; // Shift+RMB only

        Player player = e.getPlayer();
        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) return;
        if (clickedBlock.getType() != Material.ENDER_CHEST) return;

        // Check if holding the indicator item
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        ItemStack holdingItem = null;
        if (isIndicator(mainHand)) holdingItem = mainHand;
        else if (isIndicator(offHand)) holdingItem = offHand;

        if (holdingItem == null) return;

        e.setCancelled(true);

        Location loc = clickedBlock.getLocation();

        // Show info only — stress is added by EnderChestManager on open/close
        StructureIntegrityManager manager = StructureIntegrityManager.getInstance();
        if (manager != null) {
            manager.showInfo(player, loc);
            player.playSound(loc, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private boolean isIndicator(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Byte val = data.get(KEY, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }
}
