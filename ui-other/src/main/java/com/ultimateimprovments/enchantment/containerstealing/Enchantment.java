package com.ultimateimprovments.enchantment.containerstealing;

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
 * Container Stealing enchantment — real datapack enchantment with a PDC failsafe.
 * <p>
 * Registers {@code ui:container_stealing} (file {@code data/ui/enchantment/container_stealing.json})
 * as a REAL data-driven enchantment: glint, description, anvil &amp; book compatibility,
 * {@code /enchant} support. Has exactly ONE level.
 * <p>
 * <b>Failsafe design (same as AoE/AutoSmelt):</b> every tool carrying the charm ALSO
 * stores the level (always 1) in the {@code ui:container_stealing_level} PDC key — a backup mirror:
 * <ul>
 *   <li><b>Datapack alive:</b> the real enchantment is the source of truth;</li>
 *   <li><b>Datapack crashed:</b> {@link #getLevel} falls back to PDC, so tools keep working;</li>
 *   <li><b>Datapack restored:</b> items with PDC but no real charm get it re-applied.</li>
 * </ul>
 * <p>
 * Effect: when a player breaks a CONTAINER (chest, barrel, furnace, dispenser, dropper,
 * hopper, shulker box, ...) with a Container Stealing tool, the single dropped container
 * retains ALL of its contents. Placing that container restores the items inside. The
 * contents are stored on the item under the {@link #CONTENTS_KEY} PDC key.
 * <p>
 * Max level: 1<br>
 * Works on: pickaxe, shovel, axe, hoe (including copper variants)
 */
public final class Enchantment {

    /** The real enchantment key registered by the datapack. */
    public static final NamespacedKey ENCHANTMENT_KEY = new NamespacedKey("ui", "container_stealing");

    /** PDC mirror key: {@code ui:container_stealing_level} (backup copy of the enchantment level). */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "container_stealing_level");

    /** PDC key holding the serialized container contents: {@code ui:container_stealing_contents}. */
    public static final NamespacedKey CONTENTS_KEY = new NamespacedKey(Main.getInstance(), "container_stealing_contents");

    /** The only level this enchantment can have. */
    public static final int MAX_LEVEL = 1;

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
    //  GET / SET / HAS / REMOVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns whether the given tool has the Container Stealing enchantment.
     * Real enchantment first; falls back to the PDC mirror when the datapack
     * is unavailable, so tools keep working even if the datapack dies.
     *
     * @param item the tool to check
     * @return enchantment level (1 if present, 0 if not)
     */
    public static int getLevel(@NotNull ItemStack item) {
        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null && item.containsEnchantment(real)) {
            return 1;
        }
        // Datapack down or enchantment missing → PDC mirror
        return getPdcLevel(item) > 0 ? 1 : 0;
    }

    /**
     * Checks if the given tool has the Container Stealing enchantment.
     */
    public static boolean hasContainerStealing(@NotNull ItemStack item) {
        return getLevel(item) > 0;
    }

    /**
     * Sets the Container Stealing enchantment on the given tool.
     * The enchantment has only ONE level — any level ≥ 1 is clamped to 1.
     * Applies the REAL enchantment when the datapack is loaded and always writes
     * the PDC mirror. No lore is touched.
     *
     * @param item  the tool to modify
     * @param level requested level (clamped to 1)
     */
    public static void setLevel(@NotNull ItemStack item, int level) {
        if (level < 1) return;
        if (!isValidTool(item)) return;

        org.bukkit.enchantments.Enchantment real = getRegisteredEnchantment();
        if (real != null) {
            item.addUnsafeEnchantment(real, 1);
        }
        setPdcLevel(item, 1);
    }

    /**
     * Removes the Container Stealing enchantment from the given tool (real + PDC mirror).
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
     *   <li>real enchantment present → mirror level 1 into PDC;</li>
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
        boolean hasPdc = getPdcLevel(item) > 0;

        if (real != null) {
            if (item.containsEnchantment(real)) {
                // Datapack alive: mirror level 1 into PDC (backup).
                if (!hasPdc) setPdcLevel(item, 1);
            } else if (hasPdc) {
                // Datapack restored after a crash: re-apply the charm from PDC.
                item.addUnsafeEnchantment(real, 1);
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

        if (item.containsEnchantment(real)) {
            if (getPdcLevel(item) <= 0) setPdcLevel(item, 1);
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
     * Checks if the item type can accept the Container Stealing enchantment.
     */
    public static boolean isValidTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidToolType(item.getType());
    }

    /**
     * Checks if the material can accept the Container Stealing enchantment.
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

    /** Reads the PDC mirror (1 if present, 0 if absent). */
    private static int getPdcLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer level = meta.getPersistentDataContainer().get(LEVEL_KEY, PersistentDataType.INTEGER);
        return level != null && level > 0 ? 1 : 0;
    }

    /** Writes the PDC mirror (always 1). */
    private static void setPdcLevel(@NotNull ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, 1);
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