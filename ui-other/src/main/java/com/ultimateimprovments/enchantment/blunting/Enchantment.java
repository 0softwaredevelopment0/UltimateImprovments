package com.ultimateimprovments.enchantment.blunting;

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
 * Curse of Blunting — sharpness reversed. A REAL data-driven enchantment
 * ({@code ui:blunting}, registered by the UI-Datapack) with a PDC failsafe.
 * <p>
 * Every level REDUCES the melee attack damage dealt with the held weapon by
 * 0.5 (one vanilla attack-damage "unit", the same unit Sharpness adds per
 * level at the data-component level). Level 255 → −127.5 damage — the weapon
 * becomes unable to break anything tougher than a flower.
 * <p>
 * <b>Failsafe design (same as Degradation):</b> the level is mirrored into the
 * {@code ui:blunting_level} PDC key so the effect survives a datapack crash;
 * when the datapack returns, PDC-only items get the real charm re-applied.
 * <p>
 * Obtaining: only level 1 can appear in the enchanting table (only at the
 * table's max slot power — min cost 30); anvil combining cannot raise it
 * because level 2 exceeds any table slot (cost 50+).
 * <p>
 * Max level: 255 (admin/command only)<br>
 * Works on: any melee weapon or tool — swords, axes, pickaxes, shovels, hoes
 * (vanilla {@code #minecraft:enchantable/weapon} + {@code #minecraft:enchantable/mining}),
 * copper tools included.
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "blunting");

    /** PDC mirror key: {@code ui:blunting_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "blunting_level");

    /** Highest level this enchantment can have. */
    public static final int MAX_LEVEL = 255;

    /** Damage reduction per level (vanilla attack-damage units). */
    public static final double DAMAGE_REDUCTION_PER_LEVEL = 0.5;

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
     * Returns the Blunting level on the given item.
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
     * Sets the Blunting level on the given item (real enchantment + PDC mirror).
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
     * Removes Blunting from the given item (real enchantment and PDC mirror).
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
     * Blunting works on any melee weapon or tool (swords, axes, pickaxes,
     * shovels, hoes — copper included).
     */
    public static boolean isValidItem(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidItemType(item.getType());
    }

    /** Material-level check (weapons + tools). */
    public static boolean isValidItemType(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.equals("MACE");
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
