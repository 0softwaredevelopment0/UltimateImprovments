package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.mechanics.features.structure.StructureIntegrityManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер эндер-сундуков.
 * <p>
 * <b>Механики:</b>
 * <ul>
 *   <li>При открытии — шанс {@code explosion_chance} что сундук взорвётся (random-взрыв)</li>
 *   <li><b>Rate-limit:</b> если игрок открывает эндер-сундук больше {@code rate_limit.max_opens} раз
 *       в течение {@code rate_limit.window_ms} миллисекунд — сундук взрывается,
 *       нанося {@code rate_limit.damage} урона (игнорируя защиту через {@code damage(double, EntityDamageSource)})</li>
 * </ul>
 */
public class EnderChestManager implements Listener {

    private static boolean enabled = true;
    private static double explosionChance = 0.001; // 0.1%
    private static double explosionPower = 10.0;
    private static double damage = 8192;

    // Rate-limit
    private static boolean rateLimitEnabled = true;
    private static int rateLimitMaxOpens = 5;
    private static long rateLimitWindowMs = 5000; // 5 секунд
    private static double rateLimitDamage = 10;
    private static double rateLimitExplosionPower = 10.0;

    // Игроки, которые просматривают чужой эндер-сундук через /mp endersee — не дамажим их при закрытии
    private static final Set<UUID> enderseeViewers = ConcurrentHashMap.newKeySet();

    private static final Random RANDOM = new Random();

    // Для rate-limit: UUID игрока → список таймстемпов открытий (отсортирован по возрастанию)
    private static final Map<UUID, List<Long>> openTimestamps = new ConcurrentHashMap<>();

    // Для Structure Integrity: отслеживаем последнюю локацию открытого эндер-сундука
    private static final Map<UUID, Location> lastOpenedChest = new ConcurrentHashMap<>();

    public static void init(Main plugin) {
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(new EnderChestManager(), plugin);
    }

    // =========================
    // /MP ENDERSEE — пометить игрока как просматривающего чужой сундук
    // (чтобы не дамажить при закрытии)
    // =========================
    public static void addEnderseeViewer(UUID uuid) {
        enderseeViewers.add(uuid);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.enderchest");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        explosionChance = cfg.getDouble("explosion_chance", 0.001);
        explosionPower = cfg.getDouble("explosion_power", 10.0);
        damage = cfg.getDouble("damage", 8192);
        rateLimitEnabled = cfg.getBoolean("rate_limit.enabled", true);
        rateLimitMaxOpens = cfg.getInt("rate_limit.max_opens", 5);
        rateLimitWindowMs = cfg.getLong("rate_limit.window_ms", 5000);
        rateLimitDamage = cfg.getDouble("rate_limit.damage", 10);
        rateLimitExplosionPower = cfg.getDouble("rate_limit.explosion_power", 10.0);
    }

    /**
     * При ПКМ по эндер-сундуку:
     * <ul>
     *   <li>Записывает таймстемп для rate-limit</li>
     *   <li>Проверяет rate-limit — если превышен, наносит урон и взрывает сундук</li>
     *   <li>С шансом {@code explosion_chance} сундук взрывается (GUI не открывается)</li>
     * </ul>
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        Player player = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        // Наносим урон при каждом открытии
        if (damage > 0) {
            player.damage(damage);
        }

        // Structure Integrity — добавляем stress
        StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
        if (sim != null) {
            sim.onEnderChestInteract(loc);
        }

        // Запоминаем последний открытый сундук (для структуры и закрытия)
        lastOpenedChest.put(player.getUniqueId(), loc);

        // Rate-limit проверка
        if (rateLimitEnabled) {
            if (checkAndApplyRateLimit(player, loc)) {
                // Rate-limit превышен — сундук взорвался, GUI не открываем
                e.setCancelled(true);
                return;
            }
        }

        // Шанс взрыва (random)
        double roll = RANDOM.nextDouble();
        if (roll >= explosionChance) return;

        explodeEnderChest(player, loc, (float) explosionPower, damage,
                "[EnderChest] " + player.getName()
                        + " opened an ender chest at "
                        + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                        + " and it EXPLODED! (roll=" + String.format("%.4f", roll)
                        + " < chance=" + explosionChance + ")");

        e.setCancelled(true);
    }

    /**
     * Урон при закрытии эндер-сундука.
     * Не срабатывает для /mp endersee (чужие сундуки).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChestClose(InventoryCloseEvent e) {
        if (!enabled) return;
        if (e.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        // Пропускаем если игрок использует /mp endersee (просмотр чужого сундука)
        if (enderseeViewers.remove(player.getUniqueId())) {
            lastOpenedChest.remove(player.getUniqueId());
            return;
        }

        if (damage > 0) {
            player.damage(damage);
        }

        // Structure Integrity — добавляем stress при закрытии
        StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
        if (sim != null) {
            Location chestLoc = lastOpenedChest.remove(player.getUniqueId());
            if (chestLoc != null) {
                sim.onEnderChestInteract(chestLoc);
            }
        }
    }

    /**
     * Резервный учёт открытий (на случай, если PlayerInteractEvent не сработал).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent e) {
        if (!enabled || !rateLimitEnabled) return;
        if (e.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        // Если данные уже есть от PlayerInteractEvent — не дублируем
        UUID uuid = player.getUniqueId();
        List<Long> timestamps = openTimestamps.get(uuid);
        if (timestamps != null && !timestamps.isEmpty()) {
            long lastTs = timestamps.get(timestamps.size() - 1);
            // Если последнее открытие было < 50 мс назад — это наш же вызов из onEnderChestInteract
            if (System.currentTimeMillis() - lastTs < 50) {
                return;
            }
        }

        // Пытаемся найти эндер-сундук
        var target = player.getTargetBlockExact(5);
        Location chestLoc = (target != null && target.getType() == Material.ENDER_CHEST)
                ? target.getLocation()
                : player.getLocation();

        checkAndApplyRateLimit(player, chestLoc);
    }

    /**
     * Добавляет таймстемп открытия, проверяет rate-limit и применяет наказание.
     * <p>
     * При превышении rate-limit: наносит урон, блокирует открытие, сбрасывает счётчик.
     * Взрыва НЕТ — эндер-сундук взрывается только от случайного шанса или 0% integrity.
     *
     * @param player игрок
     * @param chestLocation позиция сундука
     * @return true если rate-limit превышен и наказание применено
     */
    private static boolean checkAndApplyRateLimit(Player player, Location chestLocation) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Получаем или создаём список таймстемпов
        List<Long> timestamps = openTimestamps.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());

        // Добавляем текущий таймстемп
        timestamps.add(now);

        // Удаляем таймстемпы старше window_ms
        long cutoff = now - rateLimitWindowMs;
        timestamps.removeIf(ts -> ts < cutoff);

        // Проверяем лимит
        if (timestamps.size() > rateLimitMaxOpens) {
            // Наносим урон (без взрыва)
            if (rateLimitDamage > 0) {
                player.damage(rateLimitDamage);
            }

            ConsoleLogger.warn("[EnderChest] " + player.getName()
                    + " opened ender chest " + timestamps.size()
                    + " times in " + rateLimitWindowMs + "ms at "
                    + chestLocation.getBlockX() + " " + chestLocation.getBlockY() + " " + chestLocation.getBlockZ()
                    + " — RATE LIMIT EXCEEDED! (max=" + rateLimitMaxOpens + ")");

            // Сбрасываем счётчик после наказания
            openTimestamps.remove(uuid);
            return true;
        }

        return false;
    }

    /**
     * Взрывает эндер-сундук: удаляет блок, создаёт взрыв, наносит урон, логирует, даёт ачивку.
     */
    private static void explodeEnderChest(Player player, Location chestLocation,
                                           float explosionPower, double damageAmount,
                                           String logMessage) {
        // Удаляем сам блок сундука
        chestLocation.getBlock().setType(Material.AIR);

        // Взрыв с разрушением блоков
        if (explosionPower > 0) {
            chestLocation.getWorld().createExplosion(chestLocation, explosionPower, false, true);
        }

        // Урон игроку
        if (damageAmount > 0) {
            player.damage(damageAmount);
        }

        // Логирование
        ConsoleLogger.info(logMessage);

        // 🏆 Достижение: blowed_by_echest
        try {
            var adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("minecraft", "datapack/blowed_by_echest"));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception ignored) {}
    }
}
