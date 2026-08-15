package com.ultimateimprovments.enchantment.itemstealing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Listener: Item Stealing enchantment — steal, don't pull.
 * <p>
 * When a player hooks another PLAYER with an Item Stealing fishing rod and reels
 * in, the victim is NOT pulled toward the fisher. Instead the item he holds in
 * his hand is taken and given to the fisher:
 * <ul>
 *   <li>main hand item first, offhand as fallback;</li>
 *   <li>the whole stack is stolen;</li>
 *   <li>if the fisher's inventory is full, the item drops at his feet.</li>
 * </ul>
 * If the hooked player holds NOTHING in both hands, the vanilla behavior stays:
 * the player is pulled normally.
 * <p>
 * Only {@link PlayerFishEvent.State#REEL_IN} (the "try to pull" moment) is handled.
 */
public class EnchantmentListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // Only the reel-in (pull attempt) moment matters.
        if (event.getState() != PlayerFishEvent.State.REEL_IN) return;

        // The caught entity must be a player — we steal from players only.
        if (!(event.getCaught() instanceof Player victim)) return;

        Player fisher = event.getPlayer();
        // Can't hook yourself.
        if (victim.equals(fisher)) return;

        // The rod must carry the Item Stealing charm.
        ItemStack rod = fisher.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() == Material.AIR) return;
        if (Enchantment.getLevel(rod) <= 0) return;

        // Look for an item in the victim's hands: main hand first, offhand as fallback.
        boolean fromOffhand = false;
        ItemStack stolen = victim.getInventory().getItemInMainHand();
        if (stolen == null || stolen.getType().isAir()) {
            stolen = victim.getInventory().getItemInOffHand();
            fromOffhand = true;
        }

        // Nothing in the hands → the player is pulled normally (vanilla).
        if (stolen == null || stolen.getType().isAir()) return;

        // Take the item away from the victim.
        if (fromOffhand) {
            victim.getInventory().setItemInOffHand(null);
        } else {
            victim.getInventory().setItemInMainHand(null);
        }

        // Give it to the fisher; leftovers drop at his feet.
        Map<Integer, ItemStack> leftovers = fisher.getInventory().addItem(stolen);
        for (ItemStack left : leftovers.values()) {
            fisher.getWorld().dropItemNaturally(fisher.getLocation(), left);
        }

        // Cancel the pull — the player stays in place, only the item "comes" to us.
        event.setCancelled(true);
        // Make sure the bobber retracts instead of staying stuck in the world.
        event.getHook().remove();
    }
}
