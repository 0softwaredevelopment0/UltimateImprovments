package com.ultimateimprovments.enchantment.degradation;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Curse of Degradation — real datapack enchantment with a PDC failsafe.
 * <p>
 * Registers {@code ui:degradation} (file {@code data/ui/enchantment/degradation.json})
 * as a REAL data-driven curse: red tooltip text (it's in the {@code #minecraft:curse}
 * tag), anvil &amp; book compatibility, {@code /enchant} support. Levels 1-255.
 * <p>
 * <b>Failsafe design (same as AoE/Igniting/SelfDestruct):</b> every item carrying the
 * charm ALSO stores the level in the {@code ui:degradation_level} PDC key — a backup
 * mirror:
 * <ul>
 *   <li><b>Datapack alive:</b> the real enchantment is the source of truth;</li>
 *   <li><b>Datapack crashed:</b> {@link #getLevel} falls back to PDC, so the curse
 *       keeps eating durability even if the datapack dies;</li>
 *   <li><b>Datapack restored:</b> items with PDC but no real charm get it re-applied.</li>
 * </ul>
 * <p>
 * Effect: while a cursed item of the plugin's Integrity system sits in ANY player
 * inventory slot, every second it loses INTEGRITY exactly as if it had been used
 * {@code level} times (level 5 → spent as much as after 5 uses). The drain goes
 * through {@code ItemIntegrityAPI.decreaseItemIntegrity}, so the Integrity system
 * updates the lore, sends low-integrity warnings and breaks the item when it hits 0.
 * <p>
 * Max level: 255<br>
 * Works on: any item that has durability (i.e. belongs to the Integrity system)
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "degradation");

    /** PDC mirror key: {@code ui:degradation_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "degradation_level");

    /** Highest level this enchantment can have. */
    public static final int MAX_LEVEL = 255;

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
     * Returns the Curse of Degradation level on the given item.
     * <p>
     * Real enchantment first; falls back to the PDC mirror when the datapack
     * is unavailable, so the curse keeps working even if the datapack dies.
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
     * Sets the Curse of Degradation level on the given item.
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
     * Removes the Curse of Degradation from the given item (real enchantment and PDC mirror).
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
     * Checks if the given item has the Curse of Degradation.
     */
    public static boolean hasDegradation(@NotNull ItemStack item) {
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
     * True if the stack carries the Curse of Degradation (non-air + level &gt; 0).
     * Used by the durability sweep.
     */
    public static boolean isCursed(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR && getLevel(item) > 0;
    }

    /**
     * The curse works on ANY item of the Integrity system — i.e. anything with a
     * durability bar. Uses {@link IntegrityManager#getMaxDurability} (component
     * → legacy → NMS fallback) instead of {@link Material#getMaxDurability()},
     * which may return 0 for fresh items in 1.21.4+.
     */
    public static boolean isValidTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return IntegrityManager.getMaxDurability(item) > 0;
    }

    /**
     * Material-level approximation (durability is a data component in 1.21.4+,
     * so {@link Material#getMaxDurability()} may be 0 for some items — prefer
     * {@link #isValidTool(ItemStack)}).
     */
    public static boolean isValidToolType(@NotNull Material material) {
        return material.isItem() && material.getMaxDurability() > 0;
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
