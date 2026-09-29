package com.ultimateimprovments.enchantment.magnet;

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
 * Magnet enchantment — real datapack enchantment with a PDC failsafe.
 * <p>
 * Registers {@code ui:magnet} (file {@code data/ui/enchantment/magnet.json})
 * as a REAL data-driven enchantment: glint, description, anvil &amp; book compatibility,
 * {@code /enchant} support. Levels 1-16.
 * <p>
 * <b>Failsafe design (same as AoE/AutoSmelt/VeinMiner/TreeCapitator/Flight):</b> every item
 * carrying the charm ALSO stores the level in the {@code ui:magnet_level} PDC key —
 * a backup mirror:
 * <ul>
 *   <li><b>Datapack alive:</b> the real enchantment is the source of truth;</li>
 *   <li><b>Datapack crashed:</b> {@link #getLevel} falls back to PDC, so tools keep working;</li>
 *   <li><b>Datapack restored:</b> items with PDC but no real charm get it re-applied.</li>
 * </ul>
 * <p>
 * Effect: while a player holds a Magnet tool, all freshly-dropped items within
 * {@code level × 2} blocks (capped at 32) are attracted toward him at a steady speed
 * (1 block/second) — including drops produced by AoE / VeinMiner / TreeCapitator /
 * AutoSmelt (they are created inside the same {@code BlockBreakEvent}).
 * <p>
 * Max level: 16 (radius = level × 2 blocks, up to 32)<br>
 * Works on: pickaxe, shovel, axe, hoe
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "magnet");

    /** PDC mirror key: {@code ui:magnet_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "magnet_level");

    /** Highest level: attraction radius = level × 2 blocks (16 → 32). */
    public static final int MAX_LEVEL = 16;

    /** Blocks of attraction radius added per level. */
    public static final int RADIUS_PER_LEVEL = 2;

    /** Hard cap of the attraction radius in blocks. */
    public static final int MAX_RADIUS = MAX_LEVEL * RADIUS_PER_LEVEL;

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
     * Returns whether the given tool has the Magnet enchantment.
     * Real enchantment first; falls back to the PDC mirror when the datapack
     * is unavailable, so tools keep working even if the datapack dies.
     *
     * @param item the tool to check
     * @return enchantment level (1-16, 0 if not present)
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
     * Checks if the given tool has the Magnet enchantment.
     */
    public static boolean hasMagnet(@NotNull ItemStack item) {
        return getLevel(item) > 0;
    }

    /**
     * Sets the Magnet enchantment level on the given tool.
     * Applies the REAL enchantment when the datapack is loaded and always writes
     * the PDC mirror. No lore is touched.
     *
     * @param item  the tool to modify
     * @param level enchantment level (1-16)
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
     * Removes the Magnet enchantment from the given tool (real + PDC mirror).
     *
     * @param item the tool to modify
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
     * Synchronizes a tool between the real enchantment and the PDC mirror
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
     * @param item the tool to sync
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
     * @param item the tool to mirror
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
     * @param item the tool to clear
     */
    public static void clearPdcMirror(@NotNull ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        clearPdcLevel(item);
    }

    // ─────────────────────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Checks if the item type can accept the Magnet enchantment.
     */
    public static boolean isValidTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidToolType(item.getType());
    }

    /**
     * Checks if the material can accept the Magnet enchantment.
     */
    public static boolean isValidToolType(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE")
                || name.endsWith("_HOE");
    }

    // ─────────────────────────────────────────────────────────────
    //  PDC MIRROR HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Reads the PDC mirror level (1-16) or 0 if absent. */
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
