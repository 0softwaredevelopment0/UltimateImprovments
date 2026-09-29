package com.ultimateimprovments.enchantment.itemstealing;

import com.ultimateimprovments.util.Registries;

import com.ultimateimprovments.util.Registries;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.Registries;
import org.bukkit.Material;
import com.ultimateimprovments.util.Registries;
import org.bukkit.NamespacedKey;
import com.ultimateimprovments.util.Registries;
import org.bukkit.Registry;
import com.ultimateimprovments.util.Registries;
import org.bukkit.inventory.ItemStack;
import com.ultimateimprovments.util.Registries;
import org.bukkit.inventory.meta.ItemMeta;
import com.ultimateimprovments.util.Registries;
import org.bukkit.persistence.PersistentDataType;
import com.ultimateimprovments.util.Registries;
import org.jetbrains.annotations.NotNull;
import com.ultimateimprovments.util.Registries;
import org.jetbrains.annotations.Nullable;

/**
 * Item Stealing enchantment — real datapack enchantment with a PDC failsafe.
 * <p>
 * Registers {@code ui:item_stealing} (file {@code data/ui/enchantment/item_stealing.json})
 * as a REAL data-driven enchantment: glint, description, anvil &amp; book compatibility,
 * {@code /enchant} support. Levels 1-10.
 * <p>
 * <b>Failsafe design (same as Magnet/AoE/AutoSmelt):</b> every item carrying the charm
 * ALSO stores the level in the {@code ui:item_stealing_level} PDC key — a backup mirror:
 * <ul>
 *   <li><b>Datapack alive:</b> the real enchantment is the source of truth;</li>
 *   <li><b>Datapack crashed:</b> {@link #getLevel} falls back to PDC, so rods keep working;</li>
 *   <li><b>Datapack restored:</b> items with PDC but no real charm get it re-applied.</li>
 * </ul>
 * <p>
 * Effect: when a player hooks another PLAYER with the enchanted fishing rod, the steal
 * rolls a {@code level × 10%} chance. On success the victim is NOT pulled — instead the
 * item he holds in his hand is THROWN out toward the fisher and flies to him (main hand
 * first, offhand as fallback); the fisher then picks it up normally. On a failed roll
 * (or when the victim holds nothing) the vanilla behavior stays: the player is pulled
 * normally.
 * <p>
 * Max level: 10 (level N = N×10% steal chance)<br>
 * Works on: fishing rod
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "item_stealing");

    /** PDC mirror key: {@code ui:item_stealing_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "item_stealing_level");

    /** Highest level: level N = N×10% steal chance (10 = always steal). */
    public static final int MAX_LEVEL = 10;

    private Enchantment() {}

    // ─────────────────────────────────────────────────────────────
    //  REAL ENCHANTMENT LOOKUP
    // ─────────────────────────────────────────────────────────────

    /**
     * The real {@link org.bukkit.enchantments.Enchantment} registered by the datapack,
     * or {@code null} if the datapack is not loaded (crashed / not yet installed).
     */
    public static @Nullable org.bukkit.enchantments.Enchantment getRegisteredEnchantment() {
        try {
            return Registries.enchantment().get(ENCHANTMENT_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET / SET / HAS / REMOVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns whether the given item has the Item Stealing enchantment.
     * Real enchantment first; falls back to the PDC mirror when the datapack
     * is unavailable, so rods keep working even if the datapack dies.
     *
     * @param item the item to check
     * @return enchantment level (1-10, 0 if not present)
     */
    public static int getLevel(@NotNull ItemStack item) {
        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            int lvl = item.getEnchantmentLevel(real);
            if (lvl > 0) return Math.max(1, Math.min(MAX_LEVEL, lvl));
        }
        // Datapack down or enchantment missing → PDC mirror
        return getPdcLevel(item);
    }

    /**
     * Checks if the given item has the Item Stealing enchantment.
     */
    public static boolean hasItemStealing(@NotNull ItemStack item) {
        return getLevel(item) > 0;
    }

    /**
     * Sets the Item Stealing enchantment level on the given item.
     * Applies the REAL enchantment when the datapack is loaded and always writes
     * the PDC mirror. No lore is touched.
     *
     * @param item  the item to modify
     * @param level enchantment level (1-10)
     */
    public static void setLevel(@NotNull ItemStack item, int level) {
        if (level < 1 || level > MAX_LEVEL) return;
        if (!isValidTool(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            item.addUnsafeEnchantment(real, level);
        }
        setPdcLevel(item, level);
    }

    /**
     * Removes the Item Stealing enchantment from the given item (real + PDC mirror).
     *
     * @param item the item to modify
     */
    public static void removeLevel(@NotNull ItemStack item) {
        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null && item.containsEnchantment(real)) {
            item.removeEnchantment(real);
        }
        clearPdcLevel(item);
    }

    // ─────────────────────────────────────────────────────────────
    //  FAILSAFE SYNC
    // ─────────────────────────────────────────────────────────────

    /**
     * Synchronizes an item between the real enchantment and the PDC mirror
     * (both directions — used by pickups, the join sweep and the periodic scan).
     * <p>
     * Idempotent and cheap when nothing changed:
     * <ul>
     *   <li>real enchantment present → mirror its level into PDC;</li>
     *   <li>PDC present but real enchantment missing (datapack was down) →
     *       re-apply the real enchantment from PDC;</li>
     *   <li>neither present → nothing to do.</li>
     * </ul>
     *
     * @param item the item to sync
     */
    public static void syncItem(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isValidTool(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        int pdcLevel = getPdcLevel(item);

        if (real != null) {
            int realLevel = item.getEnchantmentLevel(real);
            if (realLevel > 0) {
                // Datapack alive: mirror the real level into PDC (backup).
                if (realLevel != pdcLevel) setPdcLevel(item, realLevel);
            } else if (pdcLevel > 0) {
                // Datapack restored after a crash: re-apply the charm from PDC.
                item.addUnsafeEnchantment(real, pdcLevel);
            }
        }
        // Datapack down: leave the item as-is — PDC is the source until it returns.
    }

    /**
     * Mirrors the REAL enchantment into PDC, but NEVER re-applies the charm
     * from PDC. Used on hot inventory events (click/drag) where mutating stacks is
     * risky — the re-apply direction is left to pickups, the join sweep and the
     * periodic scan. Also protects the grindstone flow: after a legitimate
     * disenchantment the PDC mirror is cleared separately, so the charm isn't
     * silently put back.
     *
     * @param item the item to mirror
     */
    public static void mirrorItem(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isValidTool(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real == null) return; // datapack down — nothing to mirror from

        int realLevel = item.getEnchantmentLevel(real);
        if (realLevel > 0) {
            int pdcLevel = getPdcLevel(item);
            if (realLevel != pdcLevel) setPdcLevel(item, realLevel);
        }
    }

    /**
     * Clears ONLY the PDC mirror, leaving the real enchantment untouched.
     * Used when the charm is legitimately removed (e.g. grindstone disenchantment),
     * so the failsafe doesn't re-apply it later.
     *
     * @param item the item to clear
     */
    public static void clearPdcMirror(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        clearPdcLevel(item);
    }

    // ─────────────────────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Checks if the item can accept the Item Stealing enchantment.
     */
    public static boolean isValidTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidToolType(item.getType());
    }

    /**
     * Checks if the material can accept the Item Stealing enchantment (fishing rod only).
     */
    public static boolean isValidToolType(@NotNull Material material) {
        return material == Material.FISHING_ROD;
    }

    // ─────────────────────────────────────────────────────────────
    //  PDC MIRROR HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Reads the PDC mirror level (1-10) or 0 if absent. */
    private static int getPdcLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer level = meta.getPersistentDataContainer().get(LEVEL_KEY, PersistentDataType.INTEGER);
        return level != null ? Math.max(1, Math.min(MAX_LEVEL, level)) : 0;
    }

    /** Writes the PDC mirror level. */
    private static void setPdcLevel(@NotNull ItemStack item, int level) {
        item.editMeta(meta -> {
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, Math.max(1, Math.min(MAX_LEVEL, level)));
        });
    }

    /** Removes the PDC mirror key. */
    private static void clearPdcLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return;
        item.editMeta(meta -> {
        meta.getPersistentDataContainer().remove(LEVEL_KEY);
        });
    }
}
