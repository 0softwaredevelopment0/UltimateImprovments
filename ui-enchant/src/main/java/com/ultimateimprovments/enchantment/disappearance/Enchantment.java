package com.ultimateimprovments.enchantment.disappearance;

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
 * Curse of Disappearance — the item may vanish from the inventory. A REAL
 * data-driven curse ({@code ui:disappearance}, registered by the UI-Datapack)
 * with a PDC failsafe.
 * <p>
 * Every second, each cursed item in a player's inventory rolls a vanish
 * chance of {@code level × 0.001%} — level 255 → 0.255% per second
 * (≈14% per minute), level 1 → 0.001% per second (≈0.06% per minute).
 * When the roll hits, the whole stack disappears silently (no drop).
 * <p>
 * <b>Failsafe design (same as Degradation):</b> the level is mirrored into the
 * {@code ui:disappearance_level} PDC key so the curse survives a datapack
 * crash; when the datapack returns, PDC-only items get the real charm re-applied.
 * <p>
 * Obtaining: only level 1 can appear in the enchanting table (only at the
 * table's max slot power — min cost 30); anvil combining cannot raise it
 * because level 2 exceeds any table slot (cost 50+).
 * <p>
 * Max level: 255 (admin/command only)<br>
 * Works on: any item with durability — tools, weapons, armor, shields
 * (vanilla {@code #minecraft:enchantable/durability}), copper items included.
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "disappearance");

    /** PDC mirror key: {@code ui:disappearance_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "disappearance_level");

    /** Highest level this enchantment can have. */
    public static final int MAX_LEVEL = 255;

    /** Vanish chance per second per level: 0.001% = 0.00001. */
    public static final double VANISH_CHANCE_PER_LEVEL = 0.00001;

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
     * Returns the Curse of Disappearance level on the given item.
     * Real enchantment first; falls back to the PDC mirror when the datapack
     * is unavailable.
     *
     * @param item the item to check
     * @return enchantment level (1-255), or 0 if not present
     */
    public static int getLevel(@NotNull ItemStack item) {
        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            int lvl = item.getEnchantmentLevel(real);
            if (lvl > 0) return Math.min(MAX_LEVEL, lvl);
        }
        return getPdcLevel(item);
    }

    /**
     * Sets the Curse of Disappearance level on the given item (real + PDC mirror).
     *
     * @param item  the item to modify
     * @param level enchantment level (1-255)
     */
    public static void setLevel(@NotNull ItemStack item, int level) {
        if (level < 1 || level > MAX_LEVEL) return;
        if (!isValidItem(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            item.addUnsafeEnchantment(real, level);
        }
        setPdcLevel(item, level);
    }

    /**
     * Removes the Curse of Disappearance from the given item (real + PDC mirror).
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
     * Full two-way sync (used by pickups, the join sweep and the periodic scan).
     *
     * @param item the item to sync
     */
    public static void syncItem(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isValidItem(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        int pdcLevel = getPdcLevel(item);

        if (real != null) {
            int realLevel = item.getEnchantmentLevel(real);
            if (realLevel > 0) {
                if (realLevel != pdcLevel) setPdcLevel(item, realLevel);
            } else if (pdcLevel > 0) {
                // Datapack restored after a crash: re-apply the charm from PDC.
                item.addUnsafeEnchantment(real, pdcLevel);
            }
        }
    }

    /**
     * Mirror-only sync — safe on hot inventory events (never re-applies the charm).
     *
     * @param item the item to mirror
     */
    public static void mirrorItem(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isValidItem(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real == null) return;

        int realLevel = item.getEnchantmentLevel(real);
        if (realLevel > 0) {
            int pdcLevel = getPdcLevel(item);
            if (realLevel != pdcLevel) setPdcLevel(item, realLevel);
        }
    }

    /**
     * Clears ONLY the PDC mirror (grindstone disenchantment support).
     */
    public static void clearPdcMirror(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        clearPdcLevel(item);
    }

    // ─────────────────────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────────────────────

    /**
     * The curse works on any item with durability (tools, weapons, armor,
     * shields — copper included).
     */
    public static boolean isValidItem(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidItemType(item.getType());
    }

    /** Material-level check (anything with a durability bar). */
    public static boolean isValidItemType(@NotNull Material material) {
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
        return level != null ? Math.max(1, Math.min(MAX_LEVEL, level)) : 0;
    }

    /** Writes the PDC mirror level. */
    private static void setPdcLevel(@NotNull ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, Math.max(1, Math.min(MAX_LEVEL, level)));
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
