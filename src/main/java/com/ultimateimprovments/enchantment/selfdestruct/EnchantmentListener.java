package com.ultimateimprovments.enchantment.selfdestruct;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener: Curse of Self-Destruct — the silent countdown engine.
 * <p>
 * While a player carries the cursed item in ANY inventory slot (storage, armor,
 * offhand, main hand or cursor), a 30-second timer runs in TOTAL silence: no
 * sounds, no particles. The only feedback is a lore line on the item itself —
 * {@code Self-destruct: Ns} — which ticks down every second.
 * <p>
 * When the timer ends, the item is destroyed and the holder takes 19 damage
 * (9.5 hearts, armor-reducible). No explosion.
 * <p>
 * The item can NOT be removed while the timer runs — {@link InventoryLockListener}
 * cancels clicks, drags, drops, hand swaps, hopper extraction and death drops.
 * If the player logs out, the timer resets (it restarts on their next login).
 * <p>
 * Uses {@link Bukkit#getCurrentTick()} for timing. The sweep runs every
 * {@value #SWEEP_INTERVAL_TICKS} tick.
 */
public final class EnchantmentListener implements Listener {

    /** Sweep interval: 1 tick — the timer must feel exact. */
    static final long SWEEP_INTERVAL_TICKS = 1L;

    /** Timer duration in ticks: 30 seconds. */
    static final long TIMER_TICKS = Enchantment.TIMER_SECONDS * 20L;

    /** Damage dealt to the holder when the timer ends (19 = 9.5 hearts). */
    private static final double DETONATION_DAMAGE = 19.0;

    /** player → tick when the countdown started. */
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    /** Kind constants for where the cursed item lives. */
    private static final int KIND_STORAGE = 0;
    private static final int KIND_ARMOR = 1;
    private static final int KIND_OFFHAND = 2;
    private static final int KIND_MAINHAND = 3;
    private static final int KIND_CURSOR = 4;

    /** A location of the cursed item inside a player's inventory. */
    private record CurseSlot(int kind, int index) {}

    private EnchantmentListener() {}

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: update every online player's countdown. */
    private static void sweepAllPlayers() {
        long now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                updatePlayer(player, now);
            } catch (Exception e) {
                ConsoleLogger.warn("[SelfDestruct] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Advances the countdown for one player. */
    static void updatePlayer(Player player, long now) {
        UUID id = player.getUniqueId();

        // No cursed item anywhere → nothing to do (and the timer resets).
        CurseSlot slot = findCursedSlot(player);
        if (slot == null) {
            ACTIVE.remove(id);
            return;
        }

        long start = ACTIVE.computeIfAbsent(id, k -> now);
        long elapsed = now - start;

        if (elapsed >= TIMER_TICKS) {
            ACTIVE.remove(id);
            detonate(player, slot);
            return;
        }

        // Update the lore countdown once per second — silence, no sound/particles.
        if (elapsed % 20L == 0L) {
            ItemStack item = readAt(player, slot);
            if (item != null && Enchantment.isCursed(item)) {
                int secondsLeft = (int) Math.ceil((TIMER_TICKS - elapsed) / 20.0);
                Enchantment.setCountdownLore(item, secondsLeft);
                writeAt(player, slot, item);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DETONATION
    // ─────────────────────────────────────────────────────────────

    /** Removes the cursed item and deals 19 damage to the holder. No explosion. */
    private static void detonate(Player player, CurseSlot slot) {
        removeAt(player, slot);

        // Normal (armor-reducible) damage to the player who carried the item.
        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        player.damage(DETONATION_DAMAGE, source);

        ConsoleLogger.warn("[SelfDestruct] " + player.getName()
                + " — Curse of Self-Destruct detonated (19 damage, item destroyed).");
    }

    // ─────────────────────────────────────────────────────────────
    //  INVENTORY HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Returns the location of the first cursed item in the player's inventory, or null. */
    private static CurseSlot findCursedSlot(Player player) {
        PlayerInventory inv = player.getInventory();

        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (Enchantment.isCursed(storage[i])) return new CurseSlot(KIND_STORAGE, i);
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (Enchantment.isCursed(armor[i])) return new CurseSlot(KIND_ARMOR, i);
        }
        if (Enchantment.isCursed(inv.getItemInOffHand())) return new CurseSlot(KIND_OFFHAND, 0);
        if (Enchantment.isCursed(inv.getItemInMainHand())) return new CurseSlot(KIND_MAINHAND, 0);
        if (Enchantment.isCursed(player.getOpenInventory().getCursor())) return new CurseSlot(KIND_CURSOR, 0);
        return null;
    }

    /** Reads the stack at the given location. */
    private static ItemStack readAt(Player player, CurseSlot slot) {
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
    private static void writeAt(Player player, CurseSlot slot, ItemStack item) {
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

    /** Removes the stack at the given location. */
    private static void removeAt(Player player, CurseSlot slot) {
        writeAt(player, slot, null);
    }

    // ─────────────────────────────────────────────────────────────
    //  EVENTS
    // ─────────────────────────────────────────────────────────────

    /** Leaving the game resets the countdown (restarts on next login). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ACTIVE.remove(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the countdown sweep and registers the quit cleanup.
     */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentListener(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[SelfDestruct] Listener registered (silent 30s countdown sweep every tick).");
    }
}
