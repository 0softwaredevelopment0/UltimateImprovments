package com.mcplugin.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * Central Material constants loaded via {@link Registry#MATERIAL} instead of
 * direct enum references.
 * <p>
 * This avoids {@code No legacy enum constant} errors from Paper/Leaf
 * {@code Commodere} when certain modern Material constants (e.g. {@code WAXED_LIGHTNING_ROD},
 * {@code BLAST_FURNACE}) are referenced directly in bytecode.
 */
public final class Materials {

    private Materials() {
    }

    // ============================================================
    // ⚡ ENERGY NETWORK
    // ============================================================
    /** Straight cable block (and lightning rod for lightning structure). */
    public static final Material WAXED_LIGHTNING_ROD = fromKey("waxed_lightning_rod");
    /** Corner / junction cable block. */
    public static final Material WAXED_CHISELED_COPPER = fromKey("waxed_chiseled_copper");
    /** Battery multiblock block. */
    public static final Material WAXED_COPPER_GRATE = fromKey("waxed_copper_grate");

    // ============================================================
    // ⚙ GENERATOR / FURNACE / REACTOR
    // ============================================================
    /** Generator and Electric Furnace block. */
    public static final Material BLAST_FURNACE = fromKey("blast_furnace");
    /** Reactor structure block. */
    public static final Material WAXED_COPPER_BULB = fromKey("waxed_copper_bulb");

    // ============================================================
    // 🧱 COPPER VARIANTS (reactor walls, lightning structure)
    // ============================================================
    public static final Material WAXED_COPPER_BLOCK = fromKey("waxed_copper_block");
    public static final Material WAXED_CUT_COPPER = fromKey("waxed_cut_copper");
    public static final Material WAXED_CUT_COPPER_STAIRS = fromKey("waxed_cut_copper_stairs");
    public static final Material WAXED_COPPER_TRAPDOOR = fromKey("waxed_copper_trapdoor");

    // ============================================================
    // 📝 ITEMS
    // ============================================================
    /** Writable book (book and quill) for Notes GUI. */
    public static final Material WRITABLE_BOOK = fromKey("writable_book");

    // ============================================================
    // HELPER
    // ============================================================
    private static Material fromKey(String key) {
        Material m = Registry.MATERIAL.get(NamespacedKey.minecraft(key));
        if (m == null) {
            throw new IllegalStateException("Missing Material: " + key);
        }
        return m;
    }
}
