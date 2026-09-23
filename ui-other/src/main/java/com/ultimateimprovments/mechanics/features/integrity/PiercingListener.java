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
 * Extra vanilla armor wear on hits: when a player hits a target with a
 * weapon that has PIERCING (or shoots through blocks), the target's armor
 * takes the normal vanilla damage plus
 * {@code features.integrity.piercing.extra_integrity_cost} extra points.
 * <p>
 * Armor is NOT ignored — protection works exactly like vanilla.
 */
public class PiercingListener implements Listener {

    private static boolean reloadPending;

    public static void init(Main plugin) {
        reloadPending = false;
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(new PiercingListener(), plugin);
    }

    public static void reloadConfig() {
        reloadPending = true;
        // The actual values are re-read from the config by ItemDurabilityUtil;
        // if it has already reloaded after this call, the flag is cleared there.
        if (ItemDurabilityUtil.isPiercingEnabled()) {
            reloadPending = false;
        }
    }

    static boolean isReloadPending() { return reloadPending; }
    static void setReloaded() { reloadPending = false; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!ItemDurabilityUtil.isEnabled() || !ItemDurabilityUtil.isPiercingEnabled()) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return;
        if (weapon.getEnchantmentLevel(Enchantment.PIERCING) <= 0) return;

        int extra = (int) Math.round(ItemDurabilityUtil.getPiercingExtraCost());
        if (extra <= 0) return;

        // Extra vanilla armor damage on every armor piece (armor is NOT ignored)
        for (ItemStack armor : target.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) continue;
            ItemDurabilityUtil.decreaseItemIntegrity(armor, extra, target);
        }
    }
}
