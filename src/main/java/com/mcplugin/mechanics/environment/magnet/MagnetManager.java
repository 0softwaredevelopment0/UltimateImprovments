package com.mcplugin.mechanics.environment.magnet;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MagnetManager extends BukkitRunnable {

    private static MagnetManager instance;

    // =========================
    // ⚙ УПАКОВКА КООРДИНАТ В long-клюЧ
    // =========================
    public static final int COORD_OFFSET = 33554432;
    public static final int Y_OFFSET = 64;

    public static long toKey(int x, int y, int z) {
        return ((long)(x + COORD_OFFSET) << 38)
             | ((long)(z + COORD_OFFSET) << 12)
             | ((y + Y_OFFSET) & 0xFFFL);
    }

    public static long toKey(Location loc) {
        return toKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static int getX(long key) {
        return (int)((key >>> 38) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int getZ(long key) {
        return (int)((key >>> 12) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int getY(long key) {
        return (int)(key & 0xFFFL) - Y_OFFSET;
	}

    // =========================
    // 🧲 MAGNET CLUSTER
    // =========================
    public static class MagnetCluster {
        public int id;
        public World world;
        public Set<Long> blockKeys = new HashSet<>();
        public Location center;
        public int power;

        // Running sums для O(1) пересчёта центра
        private long sumX, sumY, sumZ;

        /**
         * Добавить блок в кластер с обновлением центра за O(1).
         */
        void addBlock(long key) {
            if (blockKeys.add(key)) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
                power = blockKeys.size();
                updateCenterFromSums();
            }
        }

        /**
         * Удалить блок из кластера с обновлением центра за O(1).
         */
        void removeBlock(long key) {
            if (blockKeys.remove(key)) {
                sumX -= getX(key);
                sumY -= getY(key);
                sumZ -= getZ(key);
                power = blockKeys.size();
                if (!blockKeys.isEmpty()) {
                    updateCenterFromSums();
                } else {
                    center = null;
                }
            }
        }

        /**
         * Полный пересчёт центра (используется при загрузке из БД).
         */
        void recalculateCenter() {
            if (blockKeys.isEmpty()) return;
            sumX = 0; sumY = 0; sumZ = 0;
            for (long key : blockKeys) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
            }
            updateCenterFromSums();
        }

        private void updateCenterFromSums() {
            int size = blockKeys.size();
            if (size == 0) {
                center = null;
                power = 0;
                return;
            }
            center = new Location(world,
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
            power = size;
        }

        boolean contains(Location loc) {
            return blockKeys.contains(toKey(loc));
        }
    }

    // =========================
    // DATA
    // =========================
    private static final Map<Long, MagnetCluster> locationToCluster = new HashMap<>();
    private static final Map<Integer, MagnetCluster> clustersById = new HashMap<>();
    private static int nextId = 1;

    // Игроки, чей металлический статус нужно перепроверить (выбросили предмет)
    private static final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Пометить игрока для перепроверки металлического статуса.
     * Вызывается из MagnetEventListener при выбрасывании предмета.
     */
    public static void markPlayerDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new MagnetManager();
        MagnetConfig.reloadConfig();
        MagnetDatabase.loadAll();
        instance.runTaskTimer(plugin, 20L, MagnetConfig.getIntervalTicks());
    }

    // =========================
    // 🔥 БЫСТРЫЙ FLOOD-FILL
    // =========================
    private static final int[][] DIR = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private static Set<Long> floodFillFast(World world, int sx, int sy, int sz) {
        if (world == null) return new HashSet<>(0);
        
        // ════════════════════════════════════════
        // 🛡 Проверка: чанк начальной точки загружен?
        // Если нет — не можем сканировать структуру.
        // ════════════════════════════════════════
        if (!world.isChunkLoaded(sx >> 4, sz >> 4)) return new HashSet<>(0);
        
        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        long startKey = toKey(sx, sy, sz);
        visited.add(startKey);
        queue.add(new int[]{sx, sy, sz});
        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0], y = pos[1], z = pos[2];
            for (int[] d : DIR) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long nk = toKey(nx, ny, nz);
                if (visited.contains(nk)) continue;
                // ════════════════════════════════════════
                // 🛡 Проверка: чанк соседнего блока загружен?
                // Если нет — пропускаем (структура может быть
                // неполной, но это лучше, чем вылет)
                // ════════════════════════════════════════
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                if (world.getType(nx, ny, nz) == Material.LODESTONE) {
                    visited.add(nk);
                    queue.addLast(new int[]{nx, ny, nz});
                }
            }
        }
        return visited;
    }

    private static Set<Long> floodFillFast(Location start) {
        if (start == null || start.getWorld() == null) return new HashSet<>(0);
        return floodFillFast(start.getWorld(), start.getBlockX(), start.getBlockY(), start.getBlockZ());
    }

    // =========================
    // ⚡ ПОРОГ АСИНХРОННОСТИ
    // Если кластер больше этого порога — flood-fill выполняется асинхронно,
    // чтобы не фризить сервер. При активации игрок получает уведомление.
    // =========================
    private static final int ASYNC_THRESHOLD = 500;

    // =========================
    // ACTIVATE (синхронно — для внутреннего использования)
    // =========================
    public static void activate(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) return;

        Set<Long> connected = floodFillFast(loc);
        if (connected.isEmpty()) return;

        MagnetCluster cluster = new MagnetCluster();
        cluster.id = nextId++;
        cluster.world = loc.getWorld();
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long blockKey : connected) {
            locationToCluster.put(blockKey, cluster);
        }
        clustersById.put(cluster.id, cluster);

        // БД: не пишем сразу — данные сохранятся через AsyncAutoSaveManager каждые 5 мин
        addParticleEffect(cluster.center, cluster.blockKeys.size());

        Main.getInstance().getLogger().info(
                "[Magnet] Activated cluster #" + cluster.id
                        + " with " + connected.size() + " blocks"
                        + " at center " + cluster.center
        );
    }

    // =========================
    // ACTIVATE ASYNC (для сборки через команду)
    // Запускает flood-fill асинхронно, чтобы не фризить сервер.
    // Игрок получает уведомление о начале и завершении сборки.
    // =========================
    public static void activateAsync(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) {
            player.sendMessage("§4❌ §cНекорректная позиция!");
            return;
        }
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) {
            player.sendMessage("§eМагнит уже активен на этом месте!");
            return;
        }

        player.sendMessage("§8[§bМагнит§8] §7Начинаю сканирование структуры...");
        player.sendMessage("§8[§bМагнит§8] §7Пожалуйста, подождите. Это может занять время");
        player.sendMessage("§8[§bМагнит§8] §7при большом количестве блоков.");

        World world = loc.getWorld();
        int sx = loc.getBlockX(), sy = loc.getBlockY(), sz = loc.getBlockZ();

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                Set<Long> connected = floodFillFast(world, sx, sy, sz);

                Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                        finishActivation(connected, world, key, player)
                );
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    player.sendMessage("§4❌ §cОшибка при асинхронном сканировании!");
                    player.sendMessage("§7Пробую синхронный режим...");

                    // Fallback: синхронное выполнение
                    Set<Long> connected = floodFillFast(world, sx, sy, sz);
                    finishActivation(connected, world, key, player);
                });
                Main.getInstance().getLogger().severe(
                        "[Magnet] Async activation error: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Завершает активацию: создаёт кластер, регистрирует блоки,
     * сохраняет в БД, показывает частицы и шлёт результат игроку.
     */
    private static void finishActivation(Set<Long> connected, World world, long key, Player player) {
        if (connected.isEmpty()) {
            player.sendMessage("§4❌ §cМагнит не собран: структура из LODESTONE не найдена!");
            return;
        }

        // Повторная проверка — могла произойти параллельная активация
        if (locationToCluster.containsKey(key)) {
            player.sendMessage("§eМагнит уже активен на этом месте!");
            return;
        }

        MagnetCluster cluster = new MagnetCluster();
        cluster.id = nextId++;
        cluster.world = world;
        cluster.blockKeys = new HashSet<>(connected);
        cluster.recalculateCenter();

        for (long blockKey : connected) {
            locationToCluster.put(blockKey, cluster);
        }
        clustersById.put(cluster.id, cluster);

        // БД: не пишем сразу — данные сохранятся через AsyncAutoSaveManager каждые 5 мин
        addParticleEffect(cluster.center, cluster.blockKeys.size());

        // Отображаем результат игроку
        int power = cluster.blockKeys.size();
        String powerDesc = getMagnetPowerTierStatic(power);
        int magnetRadius = getClusterRadius(power);

        player.sendMessage("§a✅ §fМагнит собран!");
        player.sendMessage("§8┃ §7Блоков в структуре: §f" + power + " §7шт");
        player.sendMessage("§8┃ §7Сила притяжения: " + powerDesc);
        if (cluster.center != null) {
            player.sendMessage("§8┃ §7Центр притяжения: §f"
                    + cluster.center.getBlockX() + " "
                    + cluster.center.getBlockY() + " "
                    + cluster.center.getBlockZ());
        }
        player.sendMessage("§8┃ §7Радиус действия: §f" + magnetRadius + " §7блоков (мин. " + MagnetConfig.getMinRadius() + ")");

        Main.getInstance().getLogger().info(
                "[Magnet] Activated cluster #" + cluster.id
                        + " with " + connected.size() + " blocks"
                        + " at center " + cluster.center
        );
    }

    /**
     * Возвращает название тира магнита по мощности.
     */
    public static String getMagnetPowerTierStatic(int power) {
        if (power >= 10000000) return "§k✧ §4✧✧ АБСОЛЮТНАЯ БЕСКОНЕЧНОСТЬ ✧✧ §k✧ §8(" + power + ")";
        if (power >= 5000000) return "§4✧✧ БЕСКОНЕЧНАЯ БЕЗДНА ✧✧ §8(" + power + ")";
        if (power >= 2500000) return "§c✦ ВСЕЛЕНСКАЯ КАТАСТРОФА ✦ §8(" + power + ")";
        if (power >= 1000000) return "§d✧ ПЕРВОЗДАННАЯ СИНГУЛЯРНОСТЬ ✧ §8(" + power + ")";
        if (power >= 500000) return "§6☠ НЕПОСТИЖИМАЯ ☠ §8(" + power + ")";
        if (power >= 250000) return "§3✦ БОГОПОДОБНАЯ ✦ §8(" + power + ")";
        if (power >= 100000) return "§4✧✧✧ ВСЕСОКРУШАЮЩАЯ СИНГУЛЯРНОСТЬ ✧✧✧ §8(" + power + ")";
        if (power >= 50000) return "§c☠ АБСОЛЮТНАЯ СИНГУЛЯРНОСТЬ ☠ §8(" + power + ")";
        if (power >= 25000) return "§6⚡ БОЖЕСТВЕННАЯ СИНГУЛЯРНОСТЬ ⚡ §8(" + power + ")";
        if (power >= 10000) return "§d✧✧ НЕПРЕВЗОЙДЁННАЯ ✧✧ §8(" + power + ")";
        if (power >= 5000) return "§5✦ ТРАНСЦЕНДЕНТНАЯ ✦ §8(" + power + ")";
        if (power >= 2500) return "§9⚜ СИНГУЛЯРНАЯ ⚜ §8(" + power + ")";
        if (power >= 1000) return "§3✦ БЕСКОНЕЧНАЯ ✦ §8(" + power + ")";
        if (power >= 500) return "§5✧✧ АБСОЛЮТНАЯ ✧✧ §8(" + power + ")";
        if (power >= 300) return "§5☯ КОСМИЧЕСКАЯ ☯ §8(" + power + ")";
        if (power >= 200) return "§d✦ ТИТАНИЧЕСКАЯ ✦ §8(" + power + ")";
        if (power >= 150) return "§d◈ ЛЕГЕНДАРНАЯ ◈ §8(" + power + ")";
        if (power >= 100) return "§c☆ НЕВЕРОЯТНАЯ ☆ §8(" + power + ")";
        if (power >= 75) return "§c♦ ЧРЕЗВЫЧАЙНАЯ ♦ §8(" + power + ")";
        if (power >= 50) return "§6★ ИСКЛЮЧИТЕЛЬНАЯ ★ §8(" + power + ")";
        if (power >= 30) return "§6⬆ ОЧЕНЬ СИЛЬНАЯ ⬆ §8(" + power + ")";
        if (power >= 20) return "§e⬆ СИЛЬНАЯ ⬆ §8(" + power + ")";
        if (power >= 12) return "§e⬆ ВЫШЕ СРЕДНЕГО ⬆ §8(" + power + ")";
        if (power >= 7) return "§a➤ СРЕДНЯЯ ➤ §8(" + power + ")";
        if (power >= 4) return "§7➤ НИЖЕ СРЕДНЕГО ➤ §8(" + power + ")";
        if (power >= 2) return "§7▸ СЛАБАЯ ▸ §8(" + power + ")";
        return "§7▸ ОЧЕНЬ СЛАБАЯ ▸ §8(" + power + ")";
    }

    // =========================
    // DEACTIVATE
    // =========================
    public static void deactivate(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        if (cluster == null) return;
        deactivateCluster(cluster);
    }

    private static void deactivateCluster(MagnetCluster cluster) {
        for (long blockKey : cluster.blockKeys) {
            locationToCluster.remove(blockKey);
        }
        clustersById.remove(cluster.id);
        // БД: не пишем сразу — данные сохранятся через AsyncAutoSaveManager каждые 5 мин
        if (cluster.center != null && cluster.center.getWorld() != null) {
            addParticleEffect(cluster.center, cluster.blockKeys.size());
        }
        Main.getInstance().getLogger().info(
                "[Magnet] Deactivated cluster #" + cluster.id
                        + " (" + cluster.power + " blocks)"
        );
    }

    // =========================
    // БЛОК РАЗРУШЕН
    // Если кластер маленький — пересчёт синхронно (быстро).
    // Если большой — асинхронно, чтобы не фризить сервер.
    // =========================
    public static boolean onBlockBroken(Location loc, Player breaker) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        long key = toKey(loc);
        MagnetCluster cluster = locationToCluster.get(key);
        if (cluster == null) return false;

        locationToCluster.remove(key);
        cluster.removeBlock(key);

        if (cluster.blockKeys.isEmpty()) {
            deactivateCluster(cluster);
            if (breaker != null) {
                breaker.sendMessage(MessageUtil.parse("<dark_red>\u26a0</dark_red> <red>Магнит полностью разрушен и деактивирован!</red>"));
            }
            return true;
        }

        // ════════════════════════════════════════
        // Если кластер маленький — синхронный пересчёт
        // Если большой — асинхронный, чтобы не фризить
        // ════════════════════════════════════════
        if (cluster.blockKeys.size() < ASYNC_THRESHOLD) {
            recalculateClusterSync(cluster, breaker);
        } else {
            recalculateClusterAsync(cluster, breaker);
        }

        return false;
    }

    /**
     * Синхронный пересчёт кластера после разрушения блока.
     */
    private static void recalculateClusterSync(MagnetCluster cluster, Player breaker) {
        long anyKey = cluster.blockKeys.iterator().next();
        Set<Long> newKeys = floodFillFast(cluster.world, getX(anyKey), getY(anyKey), getZ(anyKey));

        // Удаляем блоки, которые больше не в структуре
        for (long oldKey : new HashSet<>(cluster.blockKeys)) {
            if (!newKeys.contains(oldKey)) {
                locationToCluster.remove(oldKey);
            }
        }

        cluster.blockKeys = newKeys;
        for (long newKey : newKeys) {
            locationToCluster.put(newKey, cluster);
        }
        cluster.recalculateCenter();
        // БД: не пишем сразу — AsyncAutoSaveManager сохранит каждые 5 мин

        if (breaker != null) {
            breaker.sendMessage(MessageUtil.parse("<yellow>\u26a1</yellow> <gray>Магнит перестроен! Блоков: </gray><white>" + cluster.blockKeys.size() + "</white> <gray>| Центр смещён</gray>"));
        }

        Main.getInstance().getLogger().info(
                "[Magnet] Cluster #" + cluster.id + " recalculated: "
                        + cluster.blockKeys.size() + " blocks, center at " + cluster.center
        );
    }

    /**
     * Асинхронный пересчёт кластера после разрушения блока.
     * Запускает flood-fill в асинхронном потоке, затем применяет результаты
     * на главном потоке.
     */
    private static void recalculateClusterAsync(MagnetCluster cluster, Player breaker) {
        if (breaker != null) {
            breaker.sendMessage("§8[§bМагнит§8] §7Перестраиваю структуру...");
            breaker.sendMessage("§8[§bМагнит§8] §7Пожалуйста, подождите.");
        }

        World world = cluster.world;
        long anyKey = cluster.blockKeys.iterator().next();
        int sx = getX(anyKey), sy = getY(anyKey), sz = getZ(anyKey);

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                Set<Long> newKeys = floodFillFast(world, sx, sy, sz);

                Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                        applyRecalculation(cluster, newKeys, breaker)
                );
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (breaker != null) {
                        breaker.sendMessage("§4❌ §cОшибка при перестроении магнита!");
                    }
                    // Fallback: синхронно
                    Set<Long> newKeys = floodFillFast(world, sx, sy, sz);
                    applyRecalculation(cluster, newKeys, breaker);
                });
                Main.getInstance().getLogger().severe(
                        "[Magnet] Async recalculation error: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Применяет результат пересчёта к кластеру (на главном потоке).
     */
    private static void applyRecalculation(MagnetCluster cluster, Set<Long> newKeys, Player breaker) {
        if (newKeys.isEmpty()) {
            // Структура полностью разрушена
            deactivateCluster(cluster);
            if (breaker != null) {
                breaker.sendMessage(MessageUtil.parse("<dark_red>\u26a0</dark_red> <red>Магнит полностью разрушен и деактивирован!</red>"));
            }
            return;
        }

        // Удаляем блоки, которые больше не в структуре
        for (long oldKey : new HashSet<>(cluster.blockKeys)) {
            if (!newKeys.contains(oldKey)) {
                locationToCluster.remove(oldKey);
            }
        }

        cluster.blockKeys = newKeys;
        for (long newKey : newKeys) {
            locationToCluster.put(newKey, cluster);
        }
        cluster.recalculateCenter();
        // БД: не пишем сразу — AsyncAutoSaveManager сохранит каждые 5 мин

        if (breaker != null) {
            breaker.sendMessage(MessageUtil.parse("<yellow>\u26a1</yellow> <gray>Магнит перестроен! Блоков: </gray><white>" + cluster.blockKeys.size() + "</white> <gray>| Центр смещён</gray>"));
        }

        Main.getInstance().getLogger().info(
                "[Magnet] Cluster #" + cluster.id + " recalculated: "
                        + cluster.blockKeys.size() + " blocks, center at " + cluster.center
        );
    }

    // =========================
    // БЛОК ПОСТАВЛЕН
    // Оптимизация: вместо полного flood-fill'а — просто добавляем блок
    // в соседний кластер (или объединяем кластеры).
    // Полный пересчёт (flood-fill) делаем только если кластер >= ASYNC_THRESHOLD
    // и блок может соединить два кластера — но даже тогда просто объединяем их.
    // =========================
    public static void onBlockPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        long key = toKey(loc);
        if (locationToCluster.containsKey(key)) return;

        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        long[] neighborKeys = {
            toKey(bx + 1, by, bz), toKey(bx - 1, by, bz),
            toKey(bx, by + 1, bz), toKey(bx, by - 1, bz),
            toKey(bx, by, bz + 1), toKey(bx, by, bz - 1)
        };

        // Собираем все уникальные кластеры рядом
        Set<MagnetCluster> adjacentClusters = new LinkedHashSet<>();
        for (long nk : neighborKeys) {
            MagnetCluster c = locationToCluster.get(nk);
            if (c != null) adjacentClusters.add(c);
        }

        if (adjacentClusters.isEmpty()) return;

        if (adjacentClusters.size() == 1) {
            // ════════════════════════════════════════
            // 🟢 Простой случай: один соседний кластер
            // Просто добавляем блок в кластер — O(1)!
            // ════════════════════════════════════════
            MagnetCluster cluster = adjacentClusters.iterator().next();
            cluster.addBlock(key);
            locationToCluster.put(key, cluster);
            // БД: не пишем сразу — AsyncAutoSaveManager сохранит каждые 5 мин

            Main.getInstance().getLogger().info(
                    "[Magnet] Cluster #" + cluster.id + " expanded: "
                            + cluster.blockKeys.size() + " blocks"
            );
        } else {
            // ════════════════════════════════════════
            // 🟡 Сложный случай: блок соединяет 2+ кластера
            // Объединяем все в первый кластер
            // ════════════════════════════════════════
            Iterator<MagnetCluster> it = adjacentClusters.iterator();
            MagnetCluster primary = it.next();

            while (it.hasNext()) {
                MagnetCluster other = it.next();
                // Переносим все блоки в primary
                for (long bk : other.blockKeys) {
                    locationToCluster.put(bk, primary);
                    primary.addBlock(bk);
                }
                clustersById.remove(other.id);
                // БД: не пишем сразу — AsyncAutoSaveManager сохранит каждые 5 мин
            }

            // Добавляем новый блок
            primary.addBlock(key);
            locationToCluster.put(key, primary);
            // БД: не пишем сразу — AsyncAutoSaveManager сохранит каждые 5 мин

            Main.getInstance().getLogger().info(
                    "[Magnet] Clusters merged into #" + primary.id
                            + ": " + primary.blockKeys.size() + " blocks"
            );
        }
    }

    // =========================
    // QUERIES
    // =========================
    public static boolean isActive(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && locationToCluster.containsKey(toKey(loc));
    }

    public static boolean isActiveAt(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        if (locationToCluster.containsKey(toKey(bx, by, bz))) return true;
        return locationToCluster.containsKey(toKey(bx + 1, by, bz))
            || locationToCluster.containsKey(toKey(bx - 1, by, bz))
            || locationToCluster.containsKey(toKey(bx, by + 1, bz))
            || locationToCluster.containsKey(toKey(bx, by - 1, bz))
            || locationToCluster.containsKey(toKey(bx, by, bz + 1))
            || locationToCluster.containsKey(toKey(bx, by, bz - 1));
    }

    public static int getMagnetPower(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return 1;
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        return cluster != null ? cluster.blockKeys.size() : 1;
    }

    public static Location getMagnetCenter(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return null;
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        return cluster != null ? cluster.center.clone() : null;
    }

    // =========================
    // ДИНАМИЧЕСКИЙ РАДИУС
    // =========================
    public static int getMagnetRadius(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return MagnetConfig.getMinRadius();
        MagnetCluster cluster = locationToCluster.get(toKey(loc));
        return cluster != null ? getClusterRadius(cluster.blockKeys.size()) : MagnetConfig.getMinRadius();
    }

    public static int getClusterRadiusForPower(int power) {
        double t = Math.sqrt((double) power / MagnetConfig.getPowerNormalize());
        return (int) Math.round(MagnetConfig.getMinRadius() + (MagnetConfig.getMaxRadius() - MagnetConfig.getMinRadius()) * t);
    }

    private static int getClusterRadius(int power) {
        return getClusterRadiusForPower(power);
    }

    // =========================
    // INTERNAL ACCESS (for MagnetDatabase)
    // =========================
    public static int getClusterCount() { return clustersById.size(); }
    public static Collection<MagnetCluster> getClusters() { return clustersById.values(); }
    static Map<Long, MagnetCluster> getLocationMapInternal() { return locationToCluster; }
    static Map<Integer, MagnetCluster> getClustersByIdInternal() { return clustersById; }
    static void setNextId(int id) { nextId = id; }

    public static Set<UUID> getDirtyPlayers() { return dirtyPlayers; }

    // =========================
    // ПАРТИКЛЫ ПРИ АКТИВАЦИИ
    // =========================
    public static void addParticleEffect(Location loc) {
        addParticleEffect(loc, 30);
    }

    public static void addParticleEffect(Location loc, int power) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        int particleCount = Math.min(30 + (int)(Math.sqrt(power) * 1.5), 80);
        world.spawnParticle(Particle.END_ROD, center, particleCount, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center,
                Math.max(1, particleCount / 3), 0.5, 0.5, 0.5, 0);

        if (power >= 100) {
            world.spawnParticle(Particle.CRIT, center,
                    Math.min(power / 10, MagnetConfig.getParticleCritMax()), 0.3, 0.3, 0.3, 0);
        }
        if (power >= 1000) {
            world.spawnParticle(Particle.PORTAL, center,
                    Math.min(power / 20, MagnetConfig.getParticlePortalMax()), 0.5, 0.5, 0.5, 0.02);
        }
        if (power >= 10000) {
            world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.5, 0.5, 0.5, 0);
        }
        if (power >= 100000) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        }

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE,
                Math.min(1.0f + (float)Math.sqrt(power) * 0.05f, 2.0f),
                Math.min(1.5f + (float)Math.sqrt(power) * 0.01f, 2.0f));
    }

    // =========================
    // RUN (каждый тик)
    // =========================
    @Override
    public void run() {
        if (!MagnetConfig.isEnabled() || clustersById.isEmpty()) return;

        List<Integer> toRemove = new ArrayList<>();

        for (MagnetCluster cluster : clustersById.values()) {
            try {
                World world = cluster.world;
                if (world == null) {
                    toRemove.add(cluster.id);
                    continue;
                }

                if (cluster.blockKeys.isEmpty()) {
                    toRemove.add(cluster.id);
                    continue;
                }

                long firstKey = cluster.blockKeys.iterator().next();
                int fx = getX(firstKey), fy = getY(firstKey), fz = getZ(firstKey);

                // ════════════════════════════════════════
                // 🛡 Проверка: чанк загружен?
                // Если чанк не загружен — пропускаем кластер,
                // НО НЕ удаляем его (он может загрузиться позже).
                // ════════════════════════════════════════
                if (!world.isChunkLoaded(fx >> 4, fz >> 4)) {
                    continue;
                }

                if (world.getType(fx, fy, fz) != Material.LODESTONE) {
                    toRemove.add(cluster.id);
                    continue;
                }

                Location center = cluster.center.clone().add(0.5, 0.5, 0.5);
                int power = cluster.blockKeys.size();

                // ════════════════════════════════════════
                // ПАРТИКЛЫ — лимиты из конфига
                // ════════════════════════════════════════
                int particleCount = Math.min(8 + (int)(Math.sqrt(power) * 1.5), MagnetConfig.getParticleCenterMax());
                world.spawnParticle(Particle.END_ROD, center, particleCount, 0.5, 0.5, 0.5, 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, center,
                        Math.max(1, particleCount / 2), 0.5, 0.5, 0.5, 0);

                if (power >= 5) {
                    List<Long> keyList = new ArrayList<>(cluster.blockKeys);
                    int maxBlock = Math.min(keyList.size(), MagnetConfig.getParticleBlocksMax());
                    int step = keyList.size() / maxBlock;
                    if (step == 0) step = 1;
                    for (int i = 0; i < maxBlock; i++) {
                        long k = keyList.get(i * step);
                        Location bp = new Location(world, getX(k) + 0.5, getY(k) + 0.5, getZ(k) + 0.5);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, bp, 1, 0.2, 0.2, 0.2, 0);
                    }
                }

                if (power >= 100) {
                    world.spawnParticle(Particle.CRIT, center,
                            Math.min(power / 10, MagnetConfig.getParticleCritMax()), 0.3, 0.3, 0.3, 0);
                }
                if (power >= 1000) {
                    world.spawnParticle(Particle.PORTAL, center,
                            Math.min(power / 20, MagnetConfig.getParticlePortalMax()), 0.4, 0.4, 0.4, 0.02);
                }
                if (power >= 10000) {
                    world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
                    world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.3, 0.3, 0.3, 0);
                }
                if (power >= 100000) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
                }

                // ════════════════════════════════════════
                // 🛡 Проверка: чанк центра всё ещё загружен?
                // (между партиклами и getNearbyEntities могло пройти время,
                // но на главном серверном потоке этого не произойдёт)
                // ════════════════════════════════════════
                int cx = center.getBlockX() >> 4, cz = center.getBlockZ() >> 4;
                if (!world.isChunkLoaded(cx, cz)) continue;

                int clusterRadius = getClusterRadius(power);
                Collection<Entity> nearby = world.getNearbyEntities(center, clusterRadius, clusterRadius, clusterRadius);

                for (Entity entity : nearby) {
                    if (!shouldAttract(entity)) {
                        // Если игрок больше не металлический — сбрасываем скорость
                        if (entity instanceof Player player && dirtyPlayers.remove(player.getUniqueId())) {
                            // Игрок только что выбросил последний металлический предмет
                            // Сбрасываем скорость, чтобы он не продолжал лететь по инерции
                            player.setVelocity(new Vector(0, 0, 0));
                        }
                        // Очистка: если игрок вышел, убираем из dirtyPlayers
                        // (штатно clean up через PlayerQuitEvent, но подстраховка не помешает)
                        if (entity instanceof Player && !((Player) entity).isOnline()) {
                            dirtyPlayers.remove(entity.getUniqueId());
                        }
                        continue;
                    }
                    applyMagneticForce(entity, center, power, clusterRadius);
                }
            } catch (Exception e) {
                Main.getInstance().getLogger().severe(
                        "[Magnet] Error processing cluster #" + cluster.id + ": " + e.getMessage()
                );
                e.printStackTrace();
            }
        }

        for (int id : toRemove) {
            MagnetCluster cluster = clustersById.get(id);
            if (cluster != null) deactivateCluster(cluster);
        }
    }

    // =========================
    // SHOULD ATTRACT
    // =========================
    private boolean shouldAttract(Entity entity) {
        if (entity == null || entity.isDead()) return false;
        if (entity instanceof Item item) {
            return isMetallic(item.getItemStack());
        }
        if (entity instanceof Player player) {
            // Не притягиваем офлайн-игроков (могут быть в процессе выгрузки)
            if (!player.isOnline()) return false;
            return hasMetallicItem(player);
        }
        if (entity instanceof Mob mob) {
            // Мобы могут быть уже мёртвыми при проверке экипировки
            try {
                return hasMetallicEquipment(mob);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private boolean hasMetallicItem(Player player) {
        try {
            if (isMetallic(player.getInventory().getItemInMainHand())) return true;
            if (isMetallic(player.getInventory().getItemInOffHand())) return true;
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (isMetallic(armor)) return true;
            }
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (isMetallic(item)) return true;
            }
        } catch (Exception ignored) {
            // Игрок мог отвалиться во время перебора инвентаря
        }
        return false;
    }

    private boolean hasMetallicEquipment(Mob mob) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return false;
        if (isMetallic(eq.getItemInMainHand())) return true;
        if (isMetallic(eq.getItemInOffHand())) return true;
        if (isMetallic(eq.getHelmet())) return true;
        if (isMetallic(eq.getChestplate())) return true;
        if (isMetallic(eq.getLeggings())) return true;
        if (isMetallic(eq.getBoots())) return true;
        return false;
    }

    private boolean isMetallic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material mat = item.getType();
        String name = mat.name();
        if (name.contains("IRON")) return true;
        if (name.startsWith("GOLD_") || name.equals("GOLDEN_SWORD") || name.equals("GOLDEN_SHOVEL")
                || name.equals("GOLDEN_PICKAXE") || name.equals("GOLDEN_AXE") || name.equals("GOLDEN_HOE")
                || name.equals("GOLDEN_HELMET") || name.equals("GOLDEN_CHESTPLATE")
                || name.equals("GOLDEN_LEGGINGS") || name.equals("GOLDEN_BOOTS")
                || name.equals("GOLDEN_HORSE_ARMOR") || name.equals("GOLD_BLOCK")
                || name.equals("GOLD_INGOT") || name.equals("GOLD_NUGGET")
                || name.equals("RAW_GOLD") || name.equals("RAW_GOLD_BLOCK")) return true;
        if (name.contains("NETHERITE")) return true;
        if (name.contains("COPPER")) return true;
        if (name.contains("CHAINMAIL")) return true;
        if (mat == Material.BUCKET || mat == Material.WATER_BUCKET || mat == Material.LAVA_BUCKET
                || mat == Material.MILK_BUCKET || mat == Material.COD_BUCKET
                || mat == Material.SALMON_BUCKET || mat == Material.PUFFERFISH_BUCKET
                || mat == Material.TROPICAL_FISH_BUCKET || mat == Material.AXOLOTL_BUCKET
                || mat == Material.TADPOLE_BUCKET) return true;
        if (mat == Material.SHEARS) return true;
        if (mat == Material.COMPASS) return true;
        if (mat == Material.RECOVERY_COMPASS) return true;
        if (name.contains("MINECART")) return true;
        if (name.contains("ANVIL")) return true;
        if (mat == Material.CAULDRON) return true;
        if (mat == Material.HOPPER) return true;
        if (mat == Material.RAIL || mat == Material.POWERED_RAIL || mat == Material.DETECTOR_RAIL
                || mat == Material.ACTIVATOR_RAIL) return true;
        if (mat == Material.PISTON || mat == Material.STICKY_PISTON) return true;
        if (mat == Material.STONECUTTER) return true;
        if (mat == Material.GRINDSTONE) return true;
        if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN) return true;
        if (mat == Material.NAUTILUS_SHELL) return true;
        if (mat == Material.HEAVY_CORE) return true;
        return false;
    }

    // =========================
    // ПРИМЕНЕНИЕ СИЛЫ — ВСЕ ПАРАМЕТРЫ ИЗ КОНФИГА
    // =========================
    private void applyMagneticForce(Entity entity, Location magnetCenter, int power, int clusterRadius) {
        // ════════════════════════════════════════
        // 🛡 Защита: энтити мог умереть между shouldAttract и вызовом
        // ════════════════════════════════════════
        if (entity == null || entity.isDead()) return;

        Location entityLoc = entity.getLocation();
        if (entityLoc == null || entityLoc.getWorld() == null) return;

        Vector direction = magnetCenter.toVector().subtract(entityLoc.toVector());
        double distance = direction.length();
        if (distance < 0.5) return;
        direction.normalize();

        // ════════════════════════════════════════
        // 🌀 КРИВАЯ МОЩНОСТИ: (power / powerNormalize) ^ powerExponent
        //    0.55 = мягкий старт | 0.5 = sqrt | 1.0 = линейная
        // ════════════════════════════════════════
        double t = power / MagnetConfig.getPowerNormalize();
        double powerMultiplier = Math.pow(t, MagnetConfig.getPowerExponent());

        // ════════════════════════════════════════
        // 📏 КРИВАЯ ДИСТАНЦИИ: smoothstep (плавно) или linear (старая)
        // ════════════════════════════════════════
        double nd = distance / clusterRadius;
        if (nd > 1.0) nd = 1.0;

        double distanceFactor;
        if ("linear".equalsIgnoreCase(MagnetConfig.getDistanceCurveType())) {
            // Линейная: была по умолчанию, с жёстким min_factor
            distanceFactor = Math.max(MagnetConfig.getDistanceMinFactor(), 1.0 - nd);
        } else {
            // Smoothstep (по умолчанию): 3t² - 2t³, производная = 0 на обоих концах
            double smoothT = nd * nd * (3.0 - 2.0 * nd);
            distanceFactor = 1.0 - smoothT;
            // Если min_factor > 0 — не даём упасть ниже
            if (distanceFactor < MagnetConfig.getDistanceMinFactor()) distanceFactor = MagnetConfig.getDistanceMinFactor();
        }

        double baseForce = MagnetConfig.getForceBase() * powerMultiplier;
        double proximityForce = distanceFactor * MagnetConfig.getForceDistanceMultiplier() * powerMultiplier;
        double force = baseForce + proximityForce;

        if (force > MagnetConfig.getForceMax() * powerMultiplier) {
            force = MagnetConfig.getForceMax() * powerMultiplier;
        }

        Vector forceVector = direction.multiply(force);

        if (entity instanceof Item) {
            forceVector.setY(forceVector.getY() + MagnetConfig.getItemYBoost() * powerMultiplier);
            double maxSpeed = MagnetConfig.getForceMaxSpeed() * powerMultiplier;
            if (forceVector.length() > maxSpeed) {
                forceVector.normalize().multiply(maxSpeed);
            }
            entity.setVelocity(forceVector);
        } else {
            Vector newVel = entity.getVelocity().add(forceVector);
            double maxSpeed = MagnetConfig.getForceMaxSpeed() * powerMultiplier;
            if (newVel.length() > maxSpeed) {
                newVel.normalize().multiply(maxSpeed);
            }
            entity.setVelocity(newVel);
        }
    }

    // =========================
    // 💾 БАЗА ДАННЫХ (delegated to MagnetDatabase)
    // =========================
    public static void saveAll() {
        MagnetDatabase.saveAll();
    }
}
