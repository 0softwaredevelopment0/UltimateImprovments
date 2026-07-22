package com.ultimateimprovements.mechanics.features.world;

import com.ultimateimprovements.core.Keys;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 🧊 Управляет механикой "Ведра бетона".
 * <p>
 * При выливании Concrete Bucket:
 * <ol>
 *   <li>Вода окрашивается в серый цвет через установку биома {@code PALE_GARDEN}</li>
 *   <li>Исходный биом сохраняется для восстановления</li>
 *   <li>Если вода стоит &gt; 60 секунд → превращается в булыжник (бетон)</li>
 *   <li>Если вода исчезает раньше → биом восстанавливается</li>
 * </ol>
 */
public class ConcreteBucketManager extends BukkitRunnable implements Listener {

    private static ConcreteBucketManager instance;
    private static Main plugin;
    private static final long CONCRETE_DELAY_MS = 60_000; // 60 секунд

    // Отслеживаемые блоки воды: location -> {originalBiome, placeTime}
    private static final Map<Location, ConcreteWater> trackedWater = new HashMap<>();
    // Ожидающие установки биома (вода появится через 1 тик)
    private static final Map<Location, Biome> pendingBiomes = new HashMap<>();

    private record ConcreteWater(Biome originalBiome, long placeTime, UUID groupId) {}

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        ConcreteBucketManager.plugin = plugin;
        instance = new ConcreteBucketManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        // Проверяем каждый тик (плавное превращение)
        instance.runTaskTimer(plugin, 20L, 20L);
        ConsoleLogger.info("[ConcreteBucket] Initialized");
    }

    /**
     * Проверяет, является ли предмет Concrete Bucket.
     */
    private static boolean isConcreteBucket(ItemStack item) {
        if (item == null || item.getType() != Material.WATER_BUCKET) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.CONCRETE_BUCKET, PersistentDataType.BYTE);
    }

    // =========================
    // 🪣 ВЫЛИВАНИЕ — игрок использует ведро
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.getBucket() != Material.WATER_BUCKET) return;

        // Проверяем PDC на предмете в руке игрока
        ItemStack mainHand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isConcreteBucket(mainHand)) {
            ItemStack offHand = e.getPlayer().getInventory().getItemInOffHand();
            if (!isConcreteBucket(offHand)) return;
        }

        // Позиция, куда ставится вода (blockClicked + blockFace)
        Block clicked = e.getBlockClicked();
        if (clicked == null) return;

        Block waterBlock = clicked.getRelative(e.getBlockFace());
        Location waterLoc = waterBlock.getLocation();
        if (waterLoc.getWorld() == null) return;

        // Сохраняем исходный биом
        Biome originalBiome = waterLoc.getWorld().getBiome(waterLoc.getBlockX(), waterLoc.getBlockY(), waterLoc.getBlockZ());
        // Если локация уже отслеживалась — переиспользуем оригинальный биом (иначе PALE_GARDEN будет считаться "оригиналом")
        ConcreteWater existing = trackedWater.get(waterLoc);
        if (existing != null) {
            originalBiome = existing.originalBiome();
        }

        // Вода ещё НЕ поставлена — откладываем установку биома на 1 тик
        pendingBiomes.put(waterLoc, Biome.PALE_GARDEN);
        // Отслеживаем блок воды с уникальным groupId для этой заливки
        UUID groupId = UUID.randomUUID();
        trackedWater.put(waterLoc, new ConcreteWater(originalBiome, System.currentTimeMillis(), groupId));

        // 🪣 Сервер Paper сам заменяет WATER_BUCKET на обычный BUCKET после этого события.
        // PDC теряется при замене, поэтому ведро становится обычным.
    }

    // =========================
    // 💧 РАСТЕКАНИЕ ВОДЫ — новая вода тоже получает PALE_GARDEN биом
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        Block toBlock = e.getToBlock();
        Location toLoc = toBlock.getLocation();

        // Если вода течёт на блок, который уже был отслежен — это наш новый блок воды
        Block fromBlock = e.getBlock();
        Location fromLoc = fromBlock.getLocation();

        ConcreteWater fromData = trackedWater.get(fromLoc);
        if (fromData != null && toBlock.getType() == Material.WATER) {
            // Новая порция воды — наследуем groupId от источника
            pendingBiomes.put(toLoc, Biome.PALE_GARDEN);
            trackedWater.put(toLoc, new ConcreteWater(fromData.originalBiome(), System.currentTimeMillis(), fromData.groupId()));
        }

        // Если блок-назначение был отслеженной водой, но теперь заменяется чем-то другим
        // (например, лавой) — восстанавливаем биом
        ConcreteWater toData = trackedWater.remove(toLoc);
        if (toData != null && toBlock.getType() != Material.WATER) {
            restoreBiome(toLoc, toData.originalBiome());
        }
    }

    // =========================
    // 🔥 ИСЧЕЗНОВЕНИЕ ВОДЫ — физика блока изменилась
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent e) {
        Location loc = e.getBlock().getLocation();
        ConcreteWater data = trackedWater.get(loc);
        if (data == null) return;

        if (e.getBlock().getType() != Material.WATER) {
            trackedWater.remove(loc);
            restoreBiome(loc, data.originalBiome());
        }
    }

    // =========================
    // 🔨 БЛОК РАЗРУШЕН — если ломают воду (ведром или руками)
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        ConcreteWater data = trackedWater.remove(loc);
        if (data == null) return;
        restoreBiome(loc, data.originalBiome());
    }

    // =========================
    // ⏱ ТИК — установка отложенных биомов + проверка на бетон
    // =========================
    @Override
    public void run() {
        // ════════════════════════════════════════
        // Обрабатываем отложенные биомы
        // ════════════════════════════════════════
        if (!pendingBiomes.isEmpty()) {
            Iterator<Map.Entry<Location, Biome>> pit = pendingBiomes.entrySet().iterator();
            while (pit.hasNext()) {
                Map.Entry<Location, Biome> entry = pit.next();
                Location loc = entry.getKey();
                if (loc.getBlock().getType() == Material.WATER) {
                    // World.setBiome() отправляет обновление клиенту (в отличие от Block.setBiome())
                    loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), entry.getValue());
                    pit.remove();
                }
                // Если вода ещё не появилась — ждём следующего тика
            }
        }

        // ════════════════════════════════════════
        // Проверка на превращение в бетон через 60 секунд
        // ════════════════════════════════════════
        if (trackedWater.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Собираем groupId блоков, которые пора превращать в бетон
        Set<UUID> readyGroups = new HashSet<>();
        Iterator<Map.Entry<Location, ConcreteWater>> it = trackedWater.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Location, ConcreteWater> entry = it.next();
            Location loc = entry.getKey();
            ConcreteWater data = entry.getValue();

            // Всё ещё вода?
            if (loc.getBlock().getType() != Material.WATER) {
                it.remove();
                restoreBiome(loc, data.originalBiome());
                continue;
            }

            // Прошло 60 секунд? Добавляем ВСЮ группу в readyGroups
            if (now - data.placeTime() >= CONCRETE_DELAY_MS) {
                readyGroups.add(data.groupId());
            }
        }

        // Превращаем ВСЮ воду из readyGroups в бетон за один раз
        if (!readyGroups.isEmpty()) {
            it = trackedWater.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Location, ConcreteWater> entry = it.next();
                if (readyGroups.contains(entry.getValue().groupId())) {
                    Location loc = entry.getKey();
                    if (loc.getBlock().getType() == Material.WATER) {
                        loc.getBlock().setType(Material.GRAY_CONCRETE);
                    }
                    it.remove();
                    // Биом НЕ восстанавливаем — бетон окрасил землю
                }
            }
        }
    }

    // =========================
    // 🔄 ВОССТАНОВЛЕНИЕ БИОМА
    // =========================
    private static void restoreBiome(Location loc, Biome biome) {
        if (biome != null && biome != Biome.PALE_GARDEN && loc.getWorld() != null) {
            loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), biome);
        }
    }

    // =========================
    // 🧹 SHUTDOWN
    // =========================
    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
        trackedWater.clear();
        pendingBiomes.clear();
    }
}
