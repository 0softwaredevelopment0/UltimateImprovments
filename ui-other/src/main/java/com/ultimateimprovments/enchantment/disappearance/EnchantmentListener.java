package com.ultimateimprovments.enchantment.disappearance;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener (engine): Curse of Disappearance — the vanish roll.
 * <p>
 * Every {@value #SWEEP_INTERVAL_TICKS} ticks (1 second) every online player's
 * inventory is scanned. Each cursed item rolls a vanish chance of
 * {@code level × 0.001%}: level 255 → 0.255%/s, level 1 → 0.001%/s.
 * When the roll hits, the whole stack vanishes SILENTLY (no drop) — a puff of
 * smoke particles and a soft extinguish sound mark the spot.
 * <p>
 * Scope: the player's own inventory — storage slots, armor, offhand, main hand
 * and the cursor. Items stored in chests/containers are NOT touched.
 * <p>
 * Works through the PDC failsafe: even if the datapack dies, {@code getLevel}
 * falls back to the PDC mirror, so the curse keeps working.
 */
public final class EnchantmentListener {

    /** Sweep interval: 20 ticks = 1 second. */
    static final long SWEEP_INTERVAL_TICKS = 20L;

    private EnchantmentListener() {}

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: roll vanish chances on every online player's cursed items. */
    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                rollPlayer(player);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Disappearance] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Rolls the vanish chance for every cursed item in the player's inventory.
     * Vanished slots are collected first and cleared afterwards, so an unlucky
     * streak can't desync the live arrays while iterating.
     */
    private static void rollPlayer(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        List<int[]> doomed = new ArrayList<>(); // {kind, index}
        List<ItemStack> doomedStacks = new ArrayList<>();

        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (roll(storage[i])) {
                doomed.add(new int[]{0, i});
                doomedStacks.add(storage[i]);
            }
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (roll(armor[i])) {
                doomed.add(new int[]{1, i});
                doomedStacks.add(armor[i]);
            }
        }
        if (roll(inv.getItemInOffHand())) {
            doomed.add(new int[]{2, 0});
            doomedStacks.add(inv.getItemInOffHand());
        }
        if (roll(inv.getItemInMainHand())) {
            doomed.add(new int[]{3, 0});
            doomedStacks.add(inv.getItemInMainHand());
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (roll(cursor)) {
            doomed.add(new int[]{4, 0});
            doomedStacks.add(cursor);
        }

        if (doomed.isEmpty()) return;

        // Clear the doomed slots after the rolls.
        for (int[] slot : doomed) {
            switch (slot[0]) {
                case 0 -> inv.setItem(slot[1], null);
                case 1 -> {
                    ItemStack[] a = inv.getArmorContents();
                    a[slot[1]] = null;
                    inv.setArmorContents(a);
                }
                case 2 -> inv.setItemInOffHand(null);
                case 3 -> inv.setItemInMainHand(null);
                case 4 -> player.getOpenInventory().setCursor(null);
            }
        }

        // Feedback: smoke puff + soft sound at the player's location.
        Location loc = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.3, 0.4, 0.3, 0.01);
        player.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.4f);

        Main plugin = Main.getInstance();
        Bukkit.getLogger().info("[Disappearance] " + player.getName() + " lost "
                + doomedStacks.size() + " cursed stack(s): "
                + doomedStacks.stream().map(s -> s.getType().name()).distinct().toList());
    }

    /** True when the stack rolls its vanish chance (level × 0.001% per second). */
    private static boolean roll(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        int level = Enchantment.getLevel(item);
        if (level <= 0) return false;
        double chance = level * Enchantment.VANISH_CHANCE_PER_LEVEL;
        return Math.random() < chance;
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /** Registers the vanish sweep task. */
    public static void register(Main plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                sweepAllPlayers();
            }
        }.runTaskTimer(plugin, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }
}
