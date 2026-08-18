package com.ultimateimprovments.enchantment.repairing;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Listener: Repairing — the INTEGRITY repair engine.
 * <p>
 * Every {@value #SWEEP_INTERVAL_TICKS} ticks (1 second) every online player's
 * inventory is scanned. Each enchanted item that belongs to the plugin's Integrity
 * system restores {@code level × 0.1%} of its integrity every {@code level} seconds
 * (level 1 → 0.1% every 1s, level 255 → 25.5% every 255s). The per-item cooldown
 * (tracked in the {@code ui:repairing_last_repair} PDC key) keeps the average
 * repair rate flat at 0.1%/s regardless of level. The repair is applied through
 * {@link ItemIntegrityAPI#increaseItemIntegrityPercent}, so the Integrity system
 * updates the lore and the vanilla durability bar automatically, and integrity
 * never exceeds 100%.
 * <p>
 * Scope: the player's own inventory — storage slots, armor, offhand, main hand
 * and the cursor. Items stored in chests/containers are NOT touched.
 * <p>
 * Works through the PDC failsafe: even if the datapack dies, {@code getLevel}
 * falls back to the PDC mirror, so the repair keeps working.
 */
public final class EnchantmentListener {

    /** Sweep interval: 20 ticks = 1 second. */
    static final long SWEEP_INTERVAL_TICKS = 20L;

    /** Kind constants for where the enchanted item lives. */
    private static final int KIND_STORAGE = 0;
    private static final int KIND_ARMOR = 1;
    private static final int KIND_OFFHAND = 2;
    private static final int KIND_MAINHAND = 3;
    private static final int KIND_CURSOR = 4;

    /** A location of an item inside a player's inventory. */
    private record RepairSlot(int kind, int index) {}

    private EnchantmentListener() {}

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: restore integrity on every online player's enchanted items. */
    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                repairPlayer(player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Repairing] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Restores integrity on every enchanted item in the player's inventory. */
    private static void repairPlayer(Player player) {
        // Iterate over all five locations: storage, armor, offhand, main hand, cursor.
        repairStorage(player);
        repairArmor(player);
        repairSlot(player, new RepairSlot(KIND_OFFHAND, 0));
        repairSlot(player, new RepairSlot(KIND_MAINHAND, 0));
        repairSlot(player, new RepairSlot(KIND_CURSOR, 0));
    }

    private static void repairStorage(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            repairSlot(player, new RepairSlot(KIND_STORAGE, i));
        }
    }

    private static void repairArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            repairSlot(player, new RepairSlot(KIND_ARMOR, i));
        }
    }

    /** Restores integrity on one specific slot if it carries the enchantment. */
    private static void repairSlot(Player player, RepairSlot slot) {
        ItemStack item = readAt(player, slot);
        if (item == null || item.getType() == Material.AIR) return;

        int level = Enchantment.getLevel(item);
        if (level <= 0) return;

        // The enchantment works on items of the Integrity system (anything with durability).
        if (IntegrityManager.getMaxDurability(item) <= 0) return;
        // Integrity disabled in config → there is no integrity to restore.
        if (!IntegrityManager.isEnabled()) return;

        // Cooldown: level seconds between repairs (level 1 → 1s, level 255 → 255s),
        // so the average repair rate stays flat at 0.1%/s regardless of level.
        long now = System.currentTimeMillis();
        long last = Enchantment.getLastRepairMillis(item);
        if (last > 0 && now - last < Enchantment.getCooldownMillis(level)) return;

        // Restore level × 0.1% of the item's integrity (capped at 100% by the system).
        ItemIntegrityAPI.increaseItemIntegrityPercent(item, Enchantment.getRepairPercent(level));
        Enchantment.setLastRepairMillis(item, now);
    }

    // ─────────────────────────────────────────────────────────────
    //  INVENTORY HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Reads the stack at the given location. */
    private static ItemStack readAt(Player player, RepairSlot slot) {
        PlayerInventory inv = player.getInventory();
        return switch (slot.kind()) {
            case KIND_STORAGE -> inv.getItem(slot.index());
            case KIND_ARMOR -> {
                ItemStack[] armor = inv.getArmorContents();
                yield slot.index() < armor.length ? armor[slot.index()] : null;
            }
            case KIND_OFFHAND -> inv.getItemInOffHand();
            case KIND_MAINHAND -> inv.getItemInMainHand();
            case KIND_CURSOR -> player.getOpenInventory().getCursor();
            default -> null; // unreachable
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the integrity repair sweep (no event handlers needed — the repair is
     * fully stateless, so only the repeating task is scheduled).
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[Repairing] Listener registered (integrity repair every second).");
    }
}
