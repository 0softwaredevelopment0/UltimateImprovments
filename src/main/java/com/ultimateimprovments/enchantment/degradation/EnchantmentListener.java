package com.ultimateimprovments.enchantment.degradation;

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
 * Listener: Curse of Degradation — the INTEGRITY drain engine.
 * <p>
 * Every {@value #SWEEP_INTERVAL_TICKS} ticks (1 second) every online player's
 * inventory is scanned. Each cursed item that belongs to the plugin's Integrity
 * system loses integrity exactly as if it had been used {@code level} times
 * (level 5 → the item is spent as much as after 5 uses). The damage is applied
 * through {@link ItemIntegrityAPI#decreaseItemIntegrity}, so the Integrity system
 * handles the lore update, the low-integrity warnings and the final break (with
 * its own sound &amp; message) when integrity reaches 0.
 * <p>
 * Scope: the player's own inventory — storage slots, armor, offhand, main hand
 * and the cursor. Items stored in chests/containers are NOT touched.
 * <p>
 * Works through the PDC failsafe: even if the datapack dies, {@code getLevel}
 * falls back to the PDC mirror, so the curse keeps eating integrity.
 */
public final class EnchantmentListener {

    /** Sweep interval: 20 ticks = 1 second. */
    static final long SWEEP_INTERVAL_TICKS = 20L;

    /** Kind constants for where the cursed item lives. */
    private static final int KIND_STORAGE = 0;
    private static final int KIND_ARMOR = 1;
    private static final int KIND_OFFHAND = 2;
    private static final int KIND_MAINHAND = 3;
    private static final int KIND_CURSOR = 4;

    /** A location of an item inside a player's inventory. */
    private record DegradSlot(int kind, int index) {}

    private EnchantmentListener() {}

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: drain integrity on every online player's cursed items. */
    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                drainPlayer(player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Degradation] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Drains integrity from every cursed item in the player's inventory. */
    private static void drainPlayer(Player player) {
        // Iterate over all five locations: storage, armor, offhand, main hand, cursor.
        drainStorage(player);
        drainArmor(player);
        drainSlot(player, new DegradSlot(KIND_OFFHAND, 0));
        drainSlot(player, new DegradSlot(KIND_MAINHAND, 0));
        drainSlot(player, new DegradSlot(KIND_CURSOR, 0));
    }

    private static void drainStorage(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            drainSlot(player, new DegradSlot(KIND_STORAGE, i));
        }
    }

    private static void drainArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            drainSlot(player, new DegradSlot(KIND_ARMOR, i));
        }
    }

    /** Drains integrity from one specific slot if it carries the curse. */
    private static void drainSlot(Player player, DegradSlot slot) {
        ItemStack item = readAt(player, slot);
        if (item == null || item.getType() == Material.AIR) return;

        int level = Enchantment.getLevel(item);
        if (level <= 0) return;

        // The curse works on items of the Integrity system (anything with durability).
        if (IntegrityManager.getMaxDurability(item) <= 0) return;
        // Integrity disabled in config → there is no integrity to drain.
        if (!IntegrityManager.isEnabled()) return;

        // Spend integrity exactly as if the item had been used `level` times.
        ItemIntegrityAPI.decreaseItemIntegrity(item, level, player);

        // The Integrity system breaks the item itself (setAmount(0)) when integrity
        // hits 0 — clear the slot so the empty stack doesn't linger.
        if (item.getAmount() <= 0) {
            writeAt(player, slot, null);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  INVENTORY HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Reads the stack at the given location. */
    private static ItemStack readAt(Player player, DegradSlot slot) {
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

    /** Writes the stack (or removes it when null) at the given location. */
    private static void writeAt(Player player, DegradSlot slot, ItemStack item) {
        PlayerInventory inv = player.getInventory();
        switch (slot.kind()) {
            case KIND_STORAGE -> inv.setItem(slot.index(), item);
            case KIND_ARMOR -> {
                ItemStack[] armor = inv.getArmorContents();
                if (slot.index() < armor.length) {
                    armor[slot.index()] = item;
                    inv.setArmorContents(armor);
                }
            }
            case KIND_OFFHAND -> inv.setItemInOffHand(item);
            case KIND_MAINHAND -> inv.setItemInMainHand(item);
            case KIND_CURSOR -> player.getOpenInventory().setCursor(item);
            default -> { /* unreachable */ }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the integrity drain sweep (no event handlers needed — the drain is
     * fully stateless, so only the repeating task is scheduled).
     */
    public static void register(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[Degradation] Listener registered (integrity drain every second).");
    }
}
