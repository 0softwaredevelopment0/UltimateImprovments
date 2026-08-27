package com.ultimateimprovments.enchantment.igniting;

import com.ultimateimprovments.core.Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Igniting enchantment — real datapack enchantment with a PDC failsafe.
 * <p>
 * Registers {@code ui:igniting} (file {@code data/ui/enchantment/igniting.json})
 * as a REAL data-driven enchantment: glint, description with level, anvil &amp; book
 * compatibility, {@code /enchant} support. Levels 1-255.
 * <p>
 * <b>Failsafe design (same as AoE):</b> every item that carries the enchantment ALSO stores
 * the level in the {@code ui:igniting_level} PDC key — a backup mirror:
 * <ul>
 *   <li><b>Datapack alive:</b> the real enchantment is the source of truth. Whenever
 *       an item with it is detected, its level is mirrored into PDC.</li>
 *   <li><b>Datapack crashed:</b> {@link #getLevel} falls back to PDC, so enchanted
 *       items keep working; PDC-only legacy items work too.</li>
 *   <li><b>Datapack restored:</b> if an item has PDC but lost the real enchantment,
 *       the enchantment is re-applied from PDC automatically.</li>
 * </ul>
 * <p>
 * Effect: when a creature WEARING armor with the Igniting charm (player, mob, ...)
 * is hit by another creature, the ATTACKER is set on fire for a number of seconds
 * equal to the charm level (level 5 → 5 seconds). Water still extinguishes it —
 * it's just plain ignition, not an unquenchable flame.
 * <p>
 * Max level: 255<br>
 * Works on: helmet, chestplate, leggings, boots
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "igniting");

    /** PDC mirror key: {@code ui:igniting_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "igniting_level");

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
            return Registry.ENCHANTMENT.get(ENCHANTMENT_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET / SET / HAS / REMOVE LEVEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the Igniting enchantment level on the given item.
     * <p>
     * Real enchantment first; falls back to the PDC mirror when the datapack
     * is unavailable, so items keep working even if the datapack dies.
     *
     * @param item the item to check
     * @return enchantment level (1-255), or 0 if not present
     */
    public static int getLevel(@NotNull ItemStack item) {
        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            int lvl = item.getEnchantmentLevel(real);
            if (lvl > 0) return Math.max(1, Math.min(255, lvl));
        }
        // Datapack down or enchantment missing → PDC mirror
        return getPdcLevel(item);
    }

    /**
     * Sets the Igniting enchantment level on the given item.
     * <p>
     * Applies the REAL enchantment when the datapack is loaded and always writes
     * the PDC mirror. No lore is touched.
     *
     * @param item  the item to modify
     * @param level enchantment level (1-255)
     */
    public static void setLevel(@NotNull ItemStack item, int level) {
        if (level < 1 || level > 255) return;
        if (!isValidTool(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            item.addUnsafeEnchantment(real, level);
        }
        setPdcLevel(item, level);
    }

    /**
     * Removes the Igniting enchantment from the given item (real enchantment and PDC mirror).
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

    /**
     * Checks if the given item has the Igniting enchantment.
     */
    public static boolean hasIgniting(@NotNull ItemStack item) {
        return getLevel(item) > 0;
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
     *       re-apply the real enchantment from PDC, keeping the PDC;</li>
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
     * Mirrors the REAL enchantment level into PDC, but NEVER re-applies the charm
     * from PDC. Used on hot inventory events (click/drag) where mutating stacks is
     * risky — the re-apply direction is left to pickups, the join sweep and the
     * periodic scan. This also protects the grindstone flow: after a legitimate
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
     * Checks if the item can accept the Igniting enchantment.
     */
    public static boolean isValidTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidToolType(item.getType());
    }

    /**
     * Checks if the material can accept the Igniting enchantment.
     */
    public static boolean isValidToolType(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    // ─────────────────────────────────────────────────────────────
    //  PDC MIRROR HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Reads the PDC mirror level (1-255) or 0 if absent. */
    private static int getPdcLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer level = meta.getPersistentDataContainer().get(LEVEL_KEY, PersistentDataType.INTEGER);
        return level != null ? Math.max(1, Math.min(255, level)) : 0;
    }

    /** Writes the PDC mirror level. */
    private static void setPdcLevel(@NotNull ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, Math.max(1, Math.min(255, level)));
        item.setItemMeta(meta);
    }

    /** Removes the PDC mirror key. */
    private static void clearPdcLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(LEVEL_KEY);
        item.setItemMeta(meta);
    }
}
