package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Elytra wear — the elytra can suddenly tear during flight and drop to a few
 * durability points.
 * <p>
 * Every {@code elytra_wear.interval_ticks} (default 20 ticks = 1 second) each
 * online player is checked; the roll happens only while the player is ACTUALLY
 * gliding ({@link Player#isGliding()}) and the chest slot holds a glider item
 * ({@link ItemMeta#isGlider()} — a plain elytra or any custom glider).
 * <p>
 * On a successful roll the elytra's DURABILITY VALUE is set to
 * {@code elytra_wear.min_durability} (default 2) — that is the X in the item tooltip
 * {@code X/N}, and X is 2 no matter what N is (a vanilla elytra shows 2/432, a
 * modded one with another maximum also shows 2/<its max>). The item survives;
 * only the remaining durability is clamped.
 * <p>
 * The roll happens once per check cycle — {@code elytra_wear.interval_ticks}
 * (default 80 ticks = 4 seconds), so {@code chance_percent} is the chance PER ROLL.
 * <p>
 * The roll is skipped when the item is already at or below 2 durability, so a torn
 * elytra keeps flying instead of re-rolling every cycle.
 */
public final class ElytraWearManager {

    /** Repeating check task; cancelled on module shutdown/reload. */
    private static BukkitTask task;

    private ElytraWearManager() {}

    // =========================
    // LIFECYCLE
    // =========================
    public static void init(Main plugin) {
        shutdown();
        if (!isEnabled()) {
            ConsoleLogger.info("[ElytraWear] Disabled in config (elytra_wear.enabled = false).");
            return;
        }
        long interval = Math.max(1L, getIntervalTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, ElytraWearManager::tick,
                interval, interval);
        ConsoleLogger.info("[ElytraWear] ✔ Enabled — " + getChancePercent()
                + "% per roll (every " + interval + " ticks) while gliding; "
                + "durability after a tear: " + getMinDurability() + "/<max>.");
    }

    /** Cancels the repeating task (module shutdown, /ui reload, plugin disable). */
    public static void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // =========================
    // CHECK CYCLE
    // =========================
    private static void tick() {
        // Read the settings ONCE per cycle instead of once per player per value
        // (with 100 online players that used to be 500 config lookups every second).
        Settings settings = Settings.load();
        if (!settings.enabled() || settings.chance() <= 0.0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                check(player, settings);
            } catch (Throwable t) {
                // A single broken player must not kill the whole task.
                ConsoleLogger.warn("[ElytraWear] Check failed for " + player.getName()
                        + ": " + t.getMessage());
            }
        }
    }

    /** Immutable snapshot of {@code elytra_wear.*} for one cycle. */
    private record Settings(boolean enabled, boolean ignoreCreative, double chance,
                           int minDurability, int intervalTicks) {

        static Settings load() {
            return new Settings(isEnabled(), isIgnoreCreative(), getChancePercent(),
                    getMinDurability(), (int) getIntervalTicks());
        }
    }

    /**
     * One wear roll for the player.
     *
     * @return true when the elytra was actually torn by this roll (used by tests)
     */
    static boolean check(Player player, Settings settings) {
        if (player == null || settings == null) return false;
        if (settings.ignoreCreative()) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        }

        // "Именно полёта": gliding only — standing/walking/driving with the elytra
        // on, falling without it deployed, or riptiding must never wear it down.
        if (!player.isGliding()) return false;

        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() == Material.AIR) return false;

        ItemMeta meta = chest.getItemMeta();
        if (meta == null || !meta.isGlider()) return false; // worn but not a glider
        if (meta.isUnbreakable()) return false;
        if (!(meta instanceof Damageable damageable)) return false;

        short maxDurability = chest.getType().getMaxDurability();
        if (maxDurability <= 0) return false;

        // Tooltip X/N: X is ALWAYS min_durability (default 2), independent of N.
        // Vanilla damage is stored as "lost" points, so damage = N - 2 → shows 2/N.
        int remainingDurability = (int) Math.min(settings.minDurability(), maxDurability);
        int targetDamage = maxDurability - remainingDurability;
        if (damageable.getDamage() >= targetDamage) return false; // already torn

        if (ThreadLocalRandom.current().nextDouble(100.0) >= settings.chance()) return false;

        // Tear it: durability drops to the configured value, the item survives.
        damageable.setDamage(targetDamage);
        chest.setItemMeta(meta);
        player.getInventory().setChestplate(chest);

        playTearEffects(player, remainingDurability);
        notifyPlayer(player, remainingDurability);
        return true;
    }

    // =========================
    // FEEDBACK
    // =========================
    private static void playTearEffects(Player player, int remaining) {
        Location loc = player.getLocation();
        player.playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.8f, 0.6f);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.ITEM, loc.add(0, 1.2, 0), 18,
                    0.35, 0.35, 0.35, 0.02, new ItemStack(Material.ELYTRA));
        }
    }

    private static void notifyPlayer(Player player, int remaining) {
        String raw = MessagesManager.getString("elytra_wear.torn",
                "<red>✦</red> <white>Ваши элитры треснули! Осталось прочности: </white><red>%durability%</red>");
        if (raw == null || raw.isBlank()) return; // blank message = silent mode
        player.sendMessage(MessageUtil.parse(raw.replace("%durability%", String.valueOf(remaining))));
    }

    // =========================
    // CONFIG (elytra_wear.*)
    // =========================
    private static boolean isEnabled() {
        return getBool("elytra_wear.enabled", true);
    }

    private static boolean isIgnoreCreative() {
        return getBool("elytra_wear.ignore_creative", true);
    }

    private static double getChancePercent() {
        return clamp(getDouble("elytra_wear.chance_percent", 0.1), 0.0, 100.0);
    }

    private static int getMinDurability() {
        return Math.max(0, getInt("elytra_wear.min_durability", 2));
    }

    private static long getIntervalTicks() {
        return Math.max(1, getInt("elytra_wear.interval_ticks", 80));
    }

    private static boolean getBool(String path, boolean def) {
        try {
            return Main.getInstance().getConfig().getBoolean(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static int getInt(String path, int def) {
        try {
            return Main.getInstance().getConfig().getInt(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static double getDouble(String path, double def) {
        try {
            return Main.getInstance().getConfig().getDouble(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
