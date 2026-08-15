package com.ultimateimprovments.mechanics.features.integrity;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 🧰 ItemIntegrityAPI — a single facade for working with item integrity.
 * <p>
 * Units are baked into the method names to avoid confusion and accidental
 * editing of the wrong system (vanilla durability vs integrity):
 * <ul>
 *   <li>{@code setItemIntegrity} — sets an exact integrity % (0.0–100.0)</li>
 *   <li>{@code decreaseItemIntegrity / increaseItemIntegrity} — changes by
 *       «X uses» (int): as much integrity as would be spent over X actions
 *       that consume durability (block mining, attack, etc.)</li>
 *   <li>{@code decreaseItemIntegrityPercent / increaseItemIntegrityPercent} —
 *       changes by exactly X% (double)</li>
 * </ul>
 * <p>
 * <b>Important for correct output and synchronization:</b>
 * <ul>
 *   <li>All write methods return the <b>actual</b> item integrity
 *       (0.0–100.0) <i>after</i> the operation — the source of truth for messages,
 *       no need to recompute the value on the command side.</li>
 *   <li>All write methods update the item lore immediately so the tooltip
 *       shows the up-to-date value without waiting for the next tick.</li>
 * </ul>
 * All low-level PDC and vanilla durability work stays in
 * {@link IntegrityManager} — here only high-level operations.
 */
public final class ItemIntegrityAPI {

    private ItemIntegrityAPI() {}

    // =========================
    // READ
    // =========================

    /** Whether the item is registered in the integrity system. */
    public static boolean hasItemIntegrity(ItemStack item) {
        return IntegrityManager.hasIntegrity(item);
    }

    /**
     * The item's current integrity in % (0.0–100.0),
     * or -1 if the item isn't in the integrity system.
     */
    public static double getItemIntegrityPercent(ItemStack item) {
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * The item's max integrity in % (always 100.0),
     * or -1 if the item isn't in the integrity system.
     */
    public static double getItemMaxIntegrityPercent(ItemStack item) {
        return IntegrityManager.getMaxIntegrity(item);
    }

    /** Guarantees the item is initialized in the system (100%) and updates the lore right away. */
    public static void initializeItemIntegrity(ItemStack item) {
        IntegrityManager.ensureInitialized(item);
        refreshLore(item);
    }

    // =========================
    // WRITE
    // =========================

    /**
     * Sets the item's integrity to the given percentage (0.0 – 100.0).
     * Returns the actual integrity after setting.
     */
    public static double setItemIntegrity(ItemStack item, double percent) {
        IntegrityManager.setCurrentIntegrity(item, percent);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Decreases integrity as if the item was used {@code iterations} times
     * with actions consuming durability (1 iteration = 1 use).
     * If the item broke earlier — the remaining iterations are skipped.
     * Returns the actual integrity after the deduction.
     */
    public static double decreaseItemIntegrity(ItemStack item, int iterations, Player owner) {
        if (item == null || iterations <= 0) return IntegrityManager.getCurrentIntegrity(item);
        for (int i = 0; i < iterations; i++) {
            if (item.getAmount() <= 0) break; // the item broke — nothing left to spend
            IntegrityManager.decreaseIntegrity(item, 1, owner);
        }
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Increases integrity by as much as {@code iterations} durability-consuming
     * actions would have spent (mirror of {@link #decreaseItemIntegrity}).
     * The result can't exceed 100%. Returns the actual integrity after repair.
     */
    public static double increaseItemIntegrity(ItemStack item, int iterations) {
        if (item == null || iterations <= 0) return IntegrityManager.getCurrentIntegrity(item);
        int maxDura = IntegrityManager.getMaxDurability(item);
        if (maxDura <= 0) return IntegrityManager.getCurrentIntegrity(item);
        double costPerUse = 100.0 * IntegrityManager.getCostMultiplier() / maxDura;
        IntegrityManager.increaseIntegrity(item, costPerUse * iterations);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Decreases integrity by exactly the given percentage (double, 0.0 – 100.0).
     * At 0 the item breaks as usual.
     * Returns the actual integrity after the deduction (0 if it broke).
     */
    public static double decreaseItemIntegrityPercent(ItemStack item, double percent, Player owner) {
        IntegrityManager.decreaseIntegrityPercent(item, percent, owner);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Increases integrity by exactly the given percentage (double, 0.0 – 100.0).
     * The result can't exceed 100%. Returns the actual integrity after repair.
     */
    public static double increaseItemIntegrityPercent(ItemStack item, double percent) {
        IntegrityManager.increaseIntegrity(item, percent);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Updates the integrity lore right after a change (without waiting for a tick).
     * <p>
     * The update itself is content-aware: {@link IntegrityManager#updateItemLore}
     * rewrites the item meta only if the lore actually differs (value + lore
     * content comparison), so unchanged data causes no writes.
     * In the tick scanner {@code IntegrityManager.run()} meta is rewritten only
     * on an actual lore change.
     */
    private static void refreshLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getAmount() <= 0) return; // broken item — no lore needed
        IntegrityManager.updateItemLore(item);
    }
}
