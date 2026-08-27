package com.ultimateimprovments.mechanics.features.integrity;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 🛡 Integrity Listener — intercepts vanilla item damage
 * and redirects it into the integrity system.
 * <p>
 * On every attempt to damage an item (mining blocks, attacking,
 * taking damage in armor, etc.) the {@link PlayerItemDamageEvent}
 * is cancelled and the item's custom integrity is decreased instead.
 */
public class IntegrityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Unbreakable items take no damage at all
        if (IntegrityManager.isUnbreakable(item)) {
            event.setCancelled(true);
            return;
        }

        // Check whether the item has durability
        if (IntegrityManager.getMaxDurability(item) <= 0) return;

        // Cancel vanilla damage
        event.setCancelled(true);

        // Apply damage through the integrity system
        Player player = event.getPlayer();
        int vanillaDamage = event.getDamage();
        ItemIntegrityAPI.decreaseItemIntegrity(item, vanillaDamage, player);
    }
}
