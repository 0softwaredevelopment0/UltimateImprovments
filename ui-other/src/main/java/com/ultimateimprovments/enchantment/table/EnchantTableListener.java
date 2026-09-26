package com.ultimateimprovments.enchantment.table;

import com.ultimateimprovments.core.Main;

import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Enchanting table bridge for ALL UI custom (data-driven) enchantments.
 * <p>
 * <b>Why this exists:</b> a data-driven enchantment only appears in the vanilla enchanting
 * table when the modified enchantment level (base level ± enchantability roll, ±15% jitter)
 * lands inside the JSON {@code min_cost..max_cost} window of the enchantment's level 1.
 * The UI datapack uses {@code base=30 / per_level_above_first=20} for BOTH min and max cost,
 * which collapses the level-1 window to the single value 30 — practically unreachable with
 * the vanilla random roll. Result: the charms showed up in recipes/anvils/books but
 * NEVER in the table.
 * <p>
 * <b>Design (per spec):</b>
 * <ul>
 *   <li>ALL {@code ui:*} enchantments found in {@link Registry#ENCHANTMENT} are offered;</li>
 *   <li>the offer is ALWAYS level 1 (every UI charm ships with exactly one level);</li>
 *   <li>the offer appears ONLY on the third button — the one clicked for a level-30
 *       enchantment ({@code whichButton() == 2});</li>
 *   <li>clicking the button grants vanilla enchantments + all UI charms at level 1;
 *       lapis and the exp level cost are consumed by vanilla as usual;</li>
 *   <li>buttons 1 and 2 stay 100% vanilla.</li>
 * </ul>
 */
public final class EnchantTableListener implements Listener {

    /** Third option = the level-30 enchantment button (0-based index). */
    private static final int LEVEL_30_BUTTON = 2;

    /** UI namespace of our data-driven enchantments. */
    private static final String UI_NAMESPACE = "ui";

    /** Cached snapshot of all registered {@code ui:*} enchantments at level 1. */
    private static volatile Map<Enchantment, Integer> cachedCharms;

    private EnchantTableListener() {}

    /** Registers this listener with the plugin. */
    public static void register(Main main) {
        main.getServer().getPluginManager().registerEvents(new EnchantTableListener(), main);
    }

    // ─────────────────────────────────────────────────────────────
    //  OFFER PREPARATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Guarantees a clickable offer on the level-30 button. Vanilla almost always produces
     * one at table level 30, but if a bad roll leaves the button empty we place the first
     * charm there ourselves (cost 30) so the custom enchantments stay reachable.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareItemEnchantEvent event) {
        Map<Enchantment, Integer> charms = uiCharms();
        if (charms.isEmpty()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        org.bukkit.enchantments.EnchantmentOffer[] offers = event.getOffers();
        if (offers.length <= LEVEL_30_BUTTON) return;

        org.bukkit.enchantments.EnchantmentOffer slot = offers[LEVEL_30_BUTTON];
        if (slot == null) {
            Enchantment first = charms.keySet().iterator().next();
            offers[LEVEL_30_BUTTON] = new org.bukkit.enchantments.EnchantmentOffer(first, 1, 30);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CLICK HANDLING
    // ─────────────────────────────────────────────────────────────

    /**
     * Grants all UI charms at level 1 — but ONLY when the level-30 button was pressed.
     * Buttons 1 and 2 are left fully vanilla. Lapis and exp cost are consumed by vanilla
     * as usual, so the charms are never free.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (event.whichButton() != LEVEL_30_BUTTON) return;

        Map<Enchantment, Integer> charms = uiCharms();
        if (charms.isEmpty()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        // Add on top of whatever vanilla granted for this button.
        // Note: Bukkit ignores enchantments not allowed for the item — but our item tags
        // (ui:*_enchantable) make every charm legal for its own tool set, so all good.
        event.getEnchantsToAdd().putAll(charms);
    }

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * All registered {@code ui:*} data-driven enchantments, each at level 1.
     * The registry is immutable after startup, so the result is cached.
     */
    private static Map<Enchantment, Integer> uiCharms() {
        Map<Enchantment, Integer> cached = cachedCharms;
        if (cached != null) return cached;

        Map<Enchantment, Integer> map = new HashMap<>();
        try {
            for (Enchantment en : Registry.ENCHANTMENT) {
                if (en.getKey().getNamespace().equals(UI_NAMESPACE)) {
                    map.put(en, 1);
                }
            }
        } catch (Exception ignored) {
            // Registry unavailable — no offers this round.
            return map;
        }
        if (!map.isEmpty()) {
            cachedCharms = map;
        }
        return map;
    }
}
