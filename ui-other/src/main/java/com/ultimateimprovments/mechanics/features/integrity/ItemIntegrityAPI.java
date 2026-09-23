package com.ultimateimprovments.mechanics.features.integrity;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 🧰 ItemIntegrityAPI — compatibility facade for working with item integrity.
 * <p>
 * The old custom integrity backend (PDC counters + lore scanner) was removed;
 * all state now lives in <b>vanilla durability</b> ({@link ItemDurabilityUtil}).
 * Every method keeps its old name and signature so the call sites (commands,
 * custom enchantments, sunburn) work unchanged — only the semantics underneath
 * changed: the vanilla {@code damage} data component is the single source of
 * truth, so anvil/grindstone/Mending behave exactly like vanilla.
 * <p>
 * All write methods return the <b>actual</b> integrity % (0.0–100.0)
 * <i>after</i> the operation.
 */
public final class ItemIntegrityAPI {

    private ItemIntegrityAPI() {}

    // =========================
    // READ
    // =========================

    /** Whether the item participates in the durability system (has vanilla durability). */
    public static boolean hasItemIntegrity(ItemStack item) {
        return ItemDurabilityUtil.hasItemIntegrity(item);
    }

    /**
     * The item's current integrity in % (0.0–100.0) computed from vanilla
     * damage, or 100.0 for items without durability.
     */
    public static double getItemIntegrityPercent(ItemStack item) {
        return ItemDurabilityUtil.getItemIntegrityPercent(item);
    }

    /** The item's max integrity in % (always 100.0), or -1 if the item has no durability. */
    public static double getItemMaxIntegrityPercent(ItemStack item) {
        return ItemDurabilityUtil.getItemMaxIntegrityPercent(item);
    }

    /** No-op kept for API compatibility (plus one-time legacy PDC/lore migration). */
    public static void initializeItemIntegrity(ItemStack item) {
        ItemDurabilityUtil.initializeItemIntegrity(item);
    }

    // =========================
    // WRITE
    // =========================

    /** Sets the item's integrity to the given percentage (0.0 – 100.0). */
    public static double setItemIntegrity(ItemStack item, double percent) {
        return ItemDurabilityUtil.setItemIntegrity(item, percent);
    }

    /**
     * Decreases integrity as if the item was used {@code iterations} times
     * (1 iteration = 1 vanilla durability point). Returns the actual % after.
     */
    public static double decreaseItemIntegrity(ItemStack item, int iterations, Player owner) {
        return ItemDurabilityUtil.decreaseItemIntegrity(item, iterations, owner);
    }

    /** Increases integrity by as much as N uses would spend. Returns the actual % after repair. */
    public static double increaseItemIntegrity(ItemStack item, int iterations) {
        return ItemDurabilityUtil.increaseItemIntegrity(item, iterations);
    }

    /** Decreases integrity by exactly X% (double). At 0 the item breaks as usual. */
    public static double decreaseItemIntegrityPercent(ItemStack item, double percent, Player owner) {
        return ItemDurabilityUtil.decreaseItemIntegrityPercent(item, percent, owner);
    }

    /** Increases integrity by exactly X% (double). Returns the actual % after repair. */
    public static double increaseItemIntegrityPercent(ItemStack item, double percent) {
        return ItemDurabilityUtil.increaseItemIntegrityPercent(item, percent);
    }
}
