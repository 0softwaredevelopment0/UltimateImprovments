package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Main;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 🎯 PiercingListener — handler for the PIERCING enchantment.
 * <p>
 * In vanilla, PIERCING on crossbows pierces entities,
 * but does NOT ignore armor. This listener:
 * <ul>
 *   <li>Does NOT let PIERCING ignore armor (protection works as usual)</li>
 *   <li>Adds +extraCost% to the target's armor integrity cost on hit</li>
 *   <li>Unbreaking is checked against the final cost (not ignored)</li>
 * </ul>
 */
public class PiercingListener implements Listener {

    private static boolean enabled = true;

    public static void init(Main plugin) {
        var listener = new PiercingListener();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public static void reloadConfig() {
        enabled = IntegrityManager.isPiercingEnabled();
    }

    /**
     * When a player hits with a PIERCING weapon:
     * - Armor is NOT ignored (protection works as in vanilla)
     * - Sets a flag that the next armor damage should get +extraCost%
     * - In decreaseIntegrity() the flag is checked and extraCost is added BEFORE Unbreaking
     * <p>
     * If the hit is WITHOUT PIERCING — the flag is reset so regular hits
     * do not get a bonus from a previous PIERCING hit in the same tick.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!enabled) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // Check whether the attacker has PIERCING on their weapon
        ItemStack weapon = getWeapon(e.getDamager());
        if (weapon == null || weapon.getType() == Material.AIR) {
            // No weapon — not PIERCING, reset the flag
            IntegrityManager.setPiercingActive(false);
            return;
        }

        if (weapon.containsEnchantment(Enchantment.PIERCING)) {
            // Set the flag — the next armor damage gets +extraCost%
            IntegrityManager.setPiercingActive(true);
        } else {
            // Weapon without PIERCING — reset the flag
            IntegrityManager.setPiercingActive(false);
        }
    }

    /**
     * Gets the attacker's weapon (if the attacker is a player).
     */
    private ItemStack getWeapon(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player attacker) {
            return attacker.getInventory().getItemInMainHand();
        }
        // For mobs/projectiles we do not check PIERCING (vanilla handles it)
        return null;
    }
}
