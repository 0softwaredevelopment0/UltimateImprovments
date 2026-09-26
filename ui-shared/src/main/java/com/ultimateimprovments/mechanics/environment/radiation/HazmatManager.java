package com.ultimateimprovments.mechanics.environment.radiation;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ☢ HazmatManager — radiation protection suit (replacement for the removed Lead Shield).
 * <p>
 * The suit consists of four leather armor pieces tagged with per-piece PDC keys
 * ({@link Keys#HAZMAT_HELMET}, {@link Keys#HAZMAT_CHESTPLATE},
 * {@link Keys#HAZMAT_LEGGINGS}, {@link Keys#HAZMAT_BOOTS}).
 * <p>
 * Each worn piece reduces <b>incoming</b> radiation by a percentage — radiation
 * is weakened, never removed: {@code rad *= 1 - protection} where
 * {@code protection = pieces * protection_per_piece} (config {@code hazmat.protection_per_piece},
 * default 0.2 → −20% per piece, −80% for the full set).
 * <p>
 * Armor is NOT scanned every tick: an inventory scan task runs every
 * {@code hazmat.scan_interval_ticks} ticks (default 40 = 2 seconds) and caches the
 * per-player piece count in {@link #pieceCount}. Consumers
 * ({@link RadiationManager}) read the cached value, so the hot radiation tick stays cheap.
 */
public final class HazmatManager {

    private HazmatManager() {}

    /** Worn hazmat piece count per player (0..4), refreshed by the scan task. */
    private static final Map<UUID, Integer> pieceCount = new ConcurrentHashMap<>();

    /** The running scan task (kept to cancel on shutdown/reload). */
    private static BukkitTask scanTask;

    // =========================
    // CONFIG
    // =========================
    private static boolean enabled = true;
    private static int scanIntervalTicks = 40;   // 2 seconds
    private static double protectionPerPiece = 0.2;
    private static double fullSuitBonus = 0.0;

    // =========================
    // LIFECYCLE
    // =========================
    public static void init(Main plugin) {
        shutdown();
        loadConfig(plugin);
        // Initial scan right away so protection works from the first seconds.
        scanAll();
        long interval = Math.max(1, scanIntervalTicks);
        scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    scanAll();
                } catch (Exception ignored) {
                    // Never let a scan error kill the task.
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    public static void shutdown() {
        if (scanTask != null) {
            try { scanTask.cancel(); } catch (Exception ignored) { }
            scanTask = null;
        }
        pieceCount.clear();
    }

    private static void loadConfig(Main plugin) {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("hazmat.enabled", true);
        scanIntervalTicks = cfg.getInt("hazmat.scan_interval_ticks", 40);
        protectionPerPiece = clamp01(cfg.getDouble("hazmat.protection_per_piece", 0.2));
        fullSuitBonus = clamp01(cfg.getDouble("hazmat.full_suit_bonus", 0.0));
    }

    /** Called by /ui reload — re-reads config and restarts the scan task. */
    public static void reloadConfig(Main plugin) {
        init(plugin);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // =========================
    // SCAN (every scan_interval_ticks)
    // =========================
    private static void scanAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            pieceCount.put(player.getUniqueId(), countPieces(player));
        }
    }

    private static int countPieces(Player player) {
        if (!enabled) return 0;
        int pieces = 0;
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length >= 4
                && isHazmat(armor[3], Keys.HAZMAT_HELMET)      // helmet
                && isHazmat(armor[2], Keys.HAZMAT_CHESTPLATE)  // chestplate
                && isHazmat(armor[1], Keys.HAZMAT_LEGGINGS)    // leggings
                && isHazmat(armor[0], Keys.HAZMAT_BOOTS)) {    // boots
            pieces = 4;
        } else {
            if (isHazmat(armor.length > 3 ? armor[3] : null, Keys.HAZMAT_HELMET)) pieces++;
            if (isHazmat(armor.length > 2 ? armor[2] : null, Keys.HAZMAT_CHESTPLATE)) pieces++;
            if (isHazmat(armor.length > 1 ? armor[1] : null, Keys.HAZMAT_LEGGINGS)) pieces++;
            if (isHazmat(armor.length > 0 ? armor[0] : null, Keys.HAZMAT_BOOTS)) pieces++;
        }
        return pieces;
    }

    /** Called on quit — drop the cache entry. */
    public static void handleQuit(UUID playerId) {
        pieceCount.remove(playerId);
    }

    // =========================
    // PUBLIC API (consumed by RadiationManager)
    // =========================

    /**
     * Total radiation protection fraction for the player (0 .. 1), based on the
     * cached scan: pieces * protection_per_piece, capped at 1.0.
     */
    public static double getProtection(Player player) {
        if (!enabled || player == null) return 0.0;
        int pieces = pieceCount.getOrDefault(player.getUniqueId(), 0);
        if (pieces <= 0) return 0.0;
        double protection = pieces * protectionPerPiece;
        if (pieces >= 4) protection += fullSuitBonus;
        return clamp01(protection);
    }

    /** Worn hazmat piece count from the last scan (0..4). */
    public static int getWornPieces(Player player) {
        if (player == null) return 0;
        return pieceCount.getOrDefault(player.getUniqueId(), 0);
    }

    // =========================
    // ITEM FACTORY
    // =========================

    /** The hazmat suit color (yellow-green protective suit). */
    private static final Color HAZMAT_COLOR = Color.fromRGB(0xC8D64B);

    /**
     * Builds a hazmat armor piece: leather base, yellow-green color, fixed lore
     * and the per-piece PDC tag. Enchants/durability are left untouched so
     * players can enchant the suit as regular leather armor.
     */
    public static ItemStack createPiece(ItemStack base) {
        if (base == null) return null;
        ItemMeta meta = base.getItemMeta();
        if (meta == null) return base;

        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
            lam.setColor(HAZMAT_COLOR);
        }

        String type = switch (base.getType()) {
            case LEATHER_HELMET -> "Hazmat Helmet";
            case LEATHER_CHESTPLATE -> "Hazmat Chestplate";
            case LEATHER_LEGGINGS -> "Hazmat Leggings";
            case LEATHER_BOOTS -> "Hazmat Boots";
            default -> "Hazmat Piece";
        };

        meta.displayName(MessageUtil.parse("<i:false><white>" + type + " *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Protective suit against radiation.</gray>"),
                MessageUtil.parse("<i:false><gray>Each worn piece: -" + Math.round(protectionPerPiece * 100) + "% incoming radiation.</gray>")
        ));

        NamespacedKey key = keyFor(base.getType());
        if (key != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        }

        base.setItemMeta(meta);
        return base;
    }

    /** Builds a fresh hazmat piece of the given material (null for non-leather-armor). */
    public static ItemStack createPiece(Material type) {
        return switch (type) {
            case LEATHER_HELMET -> createPiece(new ItemStack(Material.LEATHER_HELMET));
            case LEATHER_CHESTPLATE -> createPiece(new ItemStack(Material.LEATHER_CHESTPLATE));
            case LEATHER_LEGGINGS -> createPiece(new ItemStack(Material.LEATHER_LEGGINGS));
            case LEATHER_BOOTS -> createPiece(new ItemStack(Material.LEATHER_BOOTS));
            default -> null;
        };
    }

    private static NamespacedKey keyFor(Material type) {
        return switch (type) {
            case LEATHER_HELMET -> Keys.HAZMAT_HELMET;
            case LEATHER_CHESTPLATE -> Keys.HAZMAT_CHESTPLATE;
            case LEATHER_LEGGINGS -> Keys.HAZMAT_LEGGINGS;
            case LEATHER_BOOTS -> Keys.HAZMAT_BOOTS;
            default -> null;
        };
    }

    /** True if the item is the hazmat piece for the given PDC key. */
    public static boolean isHazmat(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != Material.LEATHER_HELMET
                && item.getType() != Material.LEATHER_CHESTPLATE
                && item.getType() != Material.LEATHER_LEGGINGS
                && item.getType() != Material.LEATHER_BOOTS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }


}
