package com.mcplugin.enchantment;

import com.mcplugin.core.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * AoE (Area of Effect) enchantment — реализация через PDC.
 * <p>
 * Уровень зачарования хранится в PersistentDataContainer предмета
 * по ключу {@code mcplugin:aoe_level}.
 * <p>
 * Макс. уровень: 255<br>
 * Работает на: кирка, лопата, топор, мотыга<br>
 * Радиус = уровень зачарования (1 → 3×3, 2 → 5×5, ...)
 */
public final class AOEEnchantment {

    /** PDC key: {@code mcplugin:aoe_level} */
    public static final NamespacedKey LEVEL_KEY = new NamespacedKey(Main.getInstance(), "aoe_level");

    /** Display name prefix, e.g. "AoE V" */
    private static final String DISPLAY_PREFIX = "AoE ";

    private AOEEnchantment() {}

    // ─────────────────────────────────────────────────────────────
    //  PDC API — GET / SET / HAS / REMOVE LEVEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the AoE enchantment level on the given item (via PDC).
     *
     * @param item the item to check
     * @return enchantment level (1-255), or 0 if not present
     */
    public static int getLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer level = meta.getPersistentDataContainer().get(LEVEL_KEY, PersistentDataType.INTEGER);
        return level != null ? Math.max(1, Math.min(255, level)) : 0;
    }

    /**
     * Sets the AoE enchantment level on the given item (via PDC + lore).
     *
     * @param item  the item to modify
     * @param level enchantment level (1-255)
     */
    public static void setLevel(@NotNull ItemStack item, int level) {
        if (level < 1 || level > 255) return;
        if (!isValidTool(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Store in PDC
        meta.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, level);

        // Update lore
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore = removeOldAoeLore(lore);
        lore.add(0, buildAoeLoreLine(level));
        meta.lore(lore);

        item.setItemMeta(meta);
    }

    /**
     * Removes the AoE enchantment from the given item.
     *
     * @param item the item to modify
     */
    public static void removeLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Remove from PDC
        meta.getPersistentDataContainer().remove(LEVEL_KEY);

        // Update lore
        List<Component> lore = meta.lore();
        if (lore != null) {
            lore = removeOldAoeLore(lore);
            meta.lore(lore.isEmpty() ? null : lore);
        }

        item.setItemMeta(meta);
    }

    /**
     * Checks if the given item has the AoE enchantment.
     */
    public static boolean hasAoe(@NotNull ItemStack item) {
        return getLevel(item) > 0;
    }

    // ─────────────────────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Checks if the item type can accept the AoE enchantment.
     */
    public static boolean isValidTool(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return isValidToolType(item.getType());
    }

    /**
     * Checks if the material can accept the AoE enchantment.
     */
    public static boolean isValidToolType(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE")
                || name.endsWith("_HOE");
    }

    // ─────────────────────────────────────────────────────────────
    //  LORE HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Roman numerals for display */
    private static final String[] ROMAN = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"
    };

    /**
     * Builds the AoE lore line component (aqua colored, no italic).
     */
    static @NotNull Component buildAoeLoreLine(int level) {
        String roman = toRoman(level);
        return Component.text(DISPLAY_PREFIX + roman)
                .color(TextColor.color(0x55FFFF))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Removes any existing AoE lore lines from the list.
     */
    static @NotNull List<Component> removeOldAoeLore(@NotNull List<Component> lore) {
        List<Component> result = new ArrayList<>(lore.size());
        for (Component c : lore) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(c);
            if (!plain.startsWith(DISPLAY_PREFIX)) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * Converts an integer to Roman numerals (supports 1-255).
     */
    static @NotNull String toRoman(int num) {
        if (num <= 0) return "0";
        if (num <= 20) return ROMAN[num - 1];

        StringBuilder sb = new StringBuilder();
        int remaining = num;

        // Hundreds
        if (remaining >= 200) { sb.append("CC"); remaining -= 200; }
        else if (remaining >= 100) { sb.append("C"); remaining -= 100; }

        // Tens
        if (remaining >= 90) { sb.append("XC"); remaining -= 90; }
        else if (remaining >= 80) { sb.append("LXXX"); remaining -= 80; }
        else if (remaining >= 70) { sb.append("LXX"); remaining -= 70; }
        else if (remaining >= 60) { sb.append("LX"); remaining -= 60; }
        else if (remaining >= 50) { sb.append("L"); remaining -= 50; }
        else if (remaining >= 40) { sb.append("XL"); remaining -= 40; }
        else if (remaining >= 10) { sb.append("X"); remaining -= 10; }
        // remaining is now < 10 for the next part

        // Units 1-9
        if (remaining >= 1 && remaining <= 20) {
            sb.append(ROMAN[remaining - 1]);
        }

        return sb.toString();
    }
}
