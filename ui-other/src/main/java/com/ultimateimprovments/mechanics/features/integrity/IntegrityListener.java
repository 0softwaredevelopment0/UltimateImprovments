package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 🛡 Integrity Listener — intercepts vanilla item damage
 * and redirects it into the integrity system.
 * <p>
 * On every attempt to damage an item (mining blocks, attacking,
 * taking damage in armor, etc.) the {@link PlayerItemDamageEvent}
 * is cancelled and the item's custom integrity is decreased instead.
 * <p>
 * Items outside the integrity system (no durability, blacklisted,
 * not whitelisted) keep vanilla durability: their damage events are
 * left untouched, so vanilla wear applies as usual.
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

        // Only redirect damage for items tracked by the integrity system
        // (has durability AND passes blacklist/whitelist filters).
        // Other items keep vanilla durability.
        if (!IntegrityManager.isItemTracked(item)) return;

        // Cancel vanilla damage
        event.setCancelled(true);

        // Apply damage through the integrity system
        Player player = event.getPlayer();
        int vanillaDamage = event.getDamage();
        ItemIntegrityAPI.decreaseItemIntegrity(item, vanillaDamage, player);
    }

    // =========================
    // XP → INTEGRITY — collecting experience restores
    // the integrity of ALL items in the player's inventory
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isXpIntegrityEnabled()) return;

        int xpAmount = event.getAmount();
        if (xpAmount <= 0) return;

        double perXp = IntegrityManager.getXpIntegrityPerXp();
        if (perXp <= 0) return;

        double restore = xpAmount * perXp;

        PlayerInventory inv = event.getPlayer().getInventory();
        boolean restored = false;

        for (int i = 0; i <= 40; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!IntegrityManager.isItemTracked(item)) continue;

            // Skip items already at maximum
            double current = IntegrityManager.getCurrentIntegrity(item);
            if (current < 0 || current >= 100.0) continue;

            ItemIntegrityAPI.increaseItemIntegrityPercent(item, restore);
            restored = true;
        }

        if (restored) {
            String msg = IntegrityManager.getXpIntegrityMessage()
                    .replace("%amount%", IntegrityManager.formatPercent(restore));
            event.getPlayer().sendMessage(MessageUtil.parse(msg));

            Sound sound = SoundUtil.getSound("ENTITY_EXPERIENCE_ORB_PICKUP");
            if (sound != null) {
                event.getPlayer().playSound(event.getPlayer().getLocation(), sound, 0.4f, 1.6f);
            }
        }
    }
}
