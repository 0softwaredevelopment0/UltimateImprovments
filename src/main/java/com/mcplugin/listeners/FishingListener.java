package com.mcplugin.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Мгновенный клёв рыбы с проверкой объёма воды.
 *
 * Сканирование блоков WATER в радиусе 5 от поплавка:
 *   ≥ 128 → мгновенный клёв
 *   < 128 → "Полное отсутствие рыбы", поплавок удаляется
 *
 * Результат сканирования кэшируется по координатам блока.
 * Кэш сбрасывается при изменении любого блока в радиусе 5 от кэшированной позиции
 * (BlockBreak, BlockPlace, BlockFromTo).
 */
public class FishingListener extends BukkitRunnable implements Listener {

    private static FishingListener instance;

    /** Активные поплавки (entityId -> hook) */
    private final Map<Integer, FishHook> activeHooks = new HashMap<>();

    /**
     * Кэш результатов сканирования: "мир,x,y,z" -> true (есть рыба, ≥128 воды) / false (нет рыбы)
     */
    private final Map<String, Boolean> waterCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 500;

    // =========================
    // ПАРАМЕТРЫ ПРОВЕРКИ ВОДЫ
    // =========================
    private static final int WATER_CHECK_RADIUS = 5;
    private static final int MIN_WATER_BLOCKS = 128;

    // =========================
    // NMS REFLECTION (кэш)
    // =========================
    private static Method craftGetHandle;
    private static Field timeUntilLuredField;
    private static Field timeUntilHookedField;
    private static boolean reflectionReady = false;
    private static boolean reflectionAttempted = false;

    public static FishingListener getInstance() {
        if (instance == null) {
            instance = new FishingListener();
        }
        return instance;
    }

    // =========================
    // КЛЮЧ ДЛЯ КЭША
    // =========================
    private static String cacheKey(World world, int x, int y, int z) {
        return world.getName() + "," + x + "," + y + "," + z;
    }

    private static String cacheKey(Location loc) {
        return cacheKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // =========================
    // EVENT: ЗАБРОС УДОЧКИ
    // =========================
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING) return;

        FishHook hook = e.getHook();
        if (hook == null) return;

        // Обнуляем таймеры ДО попадания поплавка в воду
        hook.setWaitTime(0);
        hook.setMinWaitTime(0);
        hook.setMaxWaitTime(0);

        activeHooks.put(hook.getEntityId(), hook);
    }

    // =========================
    // TASK: ПРОВЕРКА КАЖДЫЙ ТИК
    // =========================
    @Override
    public void run() {
        if (activeHooks.isEmpty()) return;

        Iterator<Map.Entry<Integer, FishHook>> it = activeHooks.entrySet().iterator();

        while (it.hasNext()) {
            FishHook hook = it.next().getValue();

            // Поплавок мёртв — убираем
            if (hook.isDead() || !hook.isValid()) {
                it.remove();
                continue;
            }

            Location loc = hook.getLocation();

            // Ещё не в воде — ждём
            if (!loc.getBlock().isLiquid()) continue;

            // Игрок, которому принадлежит поплавок
            Player player = (Player) hook.getShooter();
            if (player == null) {
                it.remove();
                continue;
            }

            // =========================
            // ПРОВЕРКА КЭША / СКАНИРОВАНИЕ ВОДЫ
            // =========================
            String key = cacheKey(loc);

            Boolean cached = waterCache.get(key);

            if (cached == null) {
                // Нет в кэше — сканируем блоки воды
                int waterCount = countWaterBlocks(loc);
                cached = (waterCount >= MIN_WATER_BLOCKS);

                // Добавляем в кэш (с очисткой при переполнении)
                if (waterCache.size() >= MAX_CACHE_SIZE) {
                    waterCache.clear();
                }
                waterCache.put(key, cached);
            }

            if (!cached) {
                // Недостаточно воды — "полное отсутствие рыбы"
                hook.remove();
                it.remove();
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("fishing.no_fish", "<dark_gray>[<red>⛔</red>] <red>No fish at all!</red></dark_gray>")));
                continue;
            }

            // =========================
            // ДОСТАТОЧНО ВОДЫ — МГНОВЕННЫЙ КЛЁВ
            // =========================
            hook.setWaitTime(0);
            hook.setMinWaitTime(0);
            hook.setMaxWaitTime(0);

            // NMS-рефлексия (best-effort)
            forceInstantBiteNMS(hook);

            // Убираем из трекинга
            it.remove();
        }
    }

    // =========================
    // ИНВАЛИДАЦИЯ КЭША ПРИ ИЗМЕНЕНИИ БЛОКОВ
    // =========================

    /**
     * Блок сломан — инвалидируем кэш поблизости.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        invalidateCacheNear(e.getBlock());
    }

    /**
     * Блок поставлен — инвалидируем кэш поблизости.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        invalidateCacheNear(e.getBlock());
    }

    /**
     * Жидкость (вода/лава) потекла — инвалидируем кэш у источника и у цели.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        invalidateCacheNear(e.getBlock());
        invalidateCacheNear(e.getToBlock());
    }

    /**
     * Удаляет из кэша все записи, находящиеся в радиусе WATER_CHECK_RADIUS
     * от изменившегося блока.
     */
    private void invalidateCacheNear(Block changed) {
        if (waterCache.isEmpty()) return;

        String changedWorld = changed.getWorld().getName();
        int cx = changed.getX();
        int cy = changed.getY();
        int cz = changed.getZ();

        Iterator<Map.Entry<String, Boolean>> it = waterCache.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Boolean> entry = it.next();
            String key = entry.getKey();

            // Парсим ключ "мир,x,y,z"
            int firstComma = key.indexOf(',');
            if (firstComma == -1) { it.remove(); continue; }

            String worldName = key.substring(0, firstComma);
            if (!worldName.equals(changedWorld)) continue;

            int secondComma = key.indexOf(',', firstComma + 1);
            if (secondComma == -1) { it.remove(); continue; }

            int thirdComma = key.indexOf(',', secondComma + 1);
            if (thirdComma == -1) { it.remove(); continue; }

            try {
                int kx = Integer.parseInt(key.substring(firstComma + 1, secondComma));
                int ky = Integer.parseInt(key.substring(secondComma + 1, thirdComma));
                int kz = Integer.parseInt(key.substring(thirdComma + 1));

                int dx = Math.abs(cx - kx);
                int dy = Math.abs(cy - ky);
                int dz = Math.abs(cz - kz);

                if (dx <= WATER_CHECK_RADIUS && dy <= WATER_CHECK_RADIUS && dz <= WATER_CHECK_RADIUS) {
                    it.remove();
                }
            } catch (NumberFormatException ignored) {
                it.remove();
            }
        }
    }

    // =========================
    // ПОДСЧЁТ ВОДНЫХ БЛОКОВ
    // =========================
    private int countWaterBlocks(Location center) {
        int count = 0;
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();

        for (int dx = -WATER_CHECK_RADIUS; dx <= WATER_CHECK_RADIUS && count < MIN_WATER_BLOCKS; dx++) {
            for (int dz = -WATER_CHECK_RADIUS; dz <= WATER_CHECK_RADIUS && count < MIN_WATER_BLOCKS; dz++) {
                for (int dy = -WATER_CHECK_RADIUS; dy <= WATER_CHECK_RADIUS && count < MIN_WATER_BLOCKS; dy++) {
                    Block block = center.getWorld().getBlockAt(bx + dx, by + dy, bz + dz);
                    if (block.getType() == Material.WATER) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // =========================
    // NMS REFLECTION
    // =========================
    private static void initReflection(FishHook hook) {
        if (reflectionAttempted) return;
        reflectionAttempted = true;

        try {
            Class<?> craftClass = hook.getClass();
            craftGetHandle = craftClass.getMethod("getHandle");
            Object nmsHook = craftGetHandle.invoke(hook);
            Class<?> nmsClass = nmsHook.getClass();

            try {
                timeUntilLuredField = nmsClass.getDeclaredField("timeUntilLured");
                timeUntilLuredField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}

            try {
                timeUntilHookedField = nmsClass.getDeclaredField("timeUntilHooked");
                timeUntilHookedField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}

            reflectionReady = (timeUntilLuredField != null || timeUntilHookedField != null);
        } catch (Exception ignored) {}
    }

    private void forceInstantBiteNMS(FishHook hook) {
        if (!reflectionReady && !reflectionAttempted) {
            initReflection(hook);
        }
        if (!reflectionReady) return;

        try {
            Object nmsHook = craftGetHandle.invoke(hook);
            if (timeUntilLuredField != null) {
                timeUntilLuredField.setInt(nmsHook, 0);
            }
            if (timeUntilHookedField != null) {
                timeUntilHookedField.setInt(nmsHook, 0);
            }
        } catch (Exception ignored) {}
    }
}
