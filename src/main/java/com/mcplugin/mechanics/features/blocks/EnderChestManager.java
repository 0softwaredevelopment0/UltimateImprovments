package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.mechanics.features.structure.StructureIntegrityManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер эндер-сундуков.
 * <p>
 * <b>Механики:</b>
 * <ul>
 *   <li>При открытии/закрытии наносит 1 единицу урона (игнорирует защиту)</li>
 *   <li><b>Rate-limit:</b> если игрок открывает эндер-сундук больше {@code rate_limit.max_opens} раз
 *       в течение {@code rate_limit.window_ms} миллисекунд — открытие блокируется</li>
 * </ul>
 */
public class EnderChestManager implements Listener {

    private static boolean enabled = true;
    private static double damage = 1;

    // Rate-limit
    private static boolean rateLimitEnabled = true;
    private static int rateLimitMaxOpens = 5;
    private static long rateLimitWindowMs = 5000; // 5 секунд

    // Игроки, которые просматривают чужой эндер-сундук через /mp endersee — не дамажим их при закрытии
    private static final Set<UUID> enderseeViewers = ConcurrentHashMap.newKeySet();

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
        damage = cfg.getDouble("damage", 1);
        rateLimitEnabled = cfg.getBoolean("rate_limit.enabled", true);
        rateLimitMaxOpens = cfg.getInt("rate_limit.max_opens", 5);
        rateLimitWindowMs = cfg.getLong("rate_limit.window_ms", 5000);
    }

    /**
     * При ПКМ по эндер-сундуку:
     * <ul>
     *   <li>Записывает таймстемп для rate-limit</li>
     *   <li>Проверяет rate-limit — если превышен, блокирует открытие</li>
     * </ul>
     */
    private static final NamespacedKey INTEGRITY_INDICATOR_KEY =
            new NamespacedKey(Main.getInstance(), "is_structure_integrity_indicator");

    /** Проверяет, держит ли игрок индикатор целостности структуры */
    private static boolean isHoldingIntegrityIndicator(Player player) {
        var mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            var meta = mainHand.getItemMeta();
            if (meta != null) {
                Byte val = meta.getPersistentDataContainer().get(INTEGRITY_INDICATOR_KEY, PersistentDataType.BYTE);
                if (val != null && val == (byte) 1) return true;
            }
        }
        var offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            var meta = offHand.getItemMeta();
            if (meta != null) {
                Byte val = meta.getPersistentDataContainer().get(INTEGRITY_INDICATOR_KEY, PersistentDataType.BYTE);
                if (val != null && val == (byte) 1) return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        Player player = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        // Shift+RMB с индикатором целостности — показать инфо, без stress/урона
        if (player.isSneaking() && isHoldingIntegrityIndicator(player)) {
            e.setCancelled(true);
            Location normLoc = LocationUtil.normalize(loc);
            StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
            if (sim != null) {
                sim.showInfo(player, normLoc);
            } else {
                player.sendMessage(MessageUtil.parse("<red>❌ Structure Integrity system not available.</red>"));
            }
            return;
        }

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
     * Защита от ломания эндер-сундука, пока структура деградирует (integrity < 100%).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChestBreak(BlockBreakEvent e) {
        if (!enabled) return;
        if (e.getBlock().getType() != Material.ENDER_CHEST) return;

        StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
        if (sim == null) return;

        Location loc = e.getBlock().getLocation();
        if (sim.isDegrading(loc)) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            if (p != null) {
                p.sendMessage(MessageUtil.parse("<red>❌ You cannot break this ender chest while it's degrading!</red>"
                        + "\n<gray>Use the Structure Integrity Indicator to check its status.</gray>"));
            }
        }
    }

    /**
     * Добавляет таймстемп открытия, проверяет rate-limit и блокирует открытие.
     *
     * @param player игрок
     * @param chestLocation позиция сундука
     * @return true если rate-limit превышен
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

        // Проверяем лимит — если превышен, просто блокируем открытие
        if (timestamps.size() > rateLimitMaxOpens) {
            openTimestamps.remove(uuid);
            return true;
        }

        return false;
    }


}
