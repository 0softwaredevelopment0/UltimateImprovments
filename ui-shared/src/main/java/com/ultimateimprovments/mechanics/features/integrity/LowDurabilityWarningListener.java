package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * ⚠ LowDurabilityWarningListener — warns the player when an item's durability
 * percentage crosses a warning threshold downward (75/50/25/10/5% by default).
 * <p>
 * This is the migrated replacement of the old integrity system's
 * low-integrity warning: the percentage is now computed from the
 * <b>vanilla damage component</b> ({@code 100% × (1 - damage / maxDamage)}),
 * so the warning works for any wear source — vanilla tool use, armor hits,
 * or the plugin's custom wear — with no state stored anywhere.
 * <p>
 * The listener runs at MONITOR priority without cancelling anything: it only
 * observes the damage the item is about to take.
 */
public class LowDurabilityWarningListener implements Listener {

    public static void init(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new LowDurabilityWarningListener(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (!ItemDurabilityUtil.isEnabled()) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        Player player = event.getPlayer();
        int max = ItemDurabilityUtil.getMaxDurability(item);
        if (max <= 0) return;

        int before = ItemDurabilityUtil.getVanillaDamage(item);
        int after = before + event.getDamage();
        ItemDurabilityUtil.warnOnWear(player, item, max, before, after);
    }
}
