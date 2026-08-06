package com.ultimateimprovments.mechanics.features.collapse;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🏗 Block Collapse — stickiness/heaviness system for world structures.
 * <p>
 * Every solid block discovered around a player's block action (16×16×16 chunk area)
 * gets attributes:
 * <ul>
 *   <li><b>Stickiness</b> — 0..100 (double). Every block starts at 100. While the
 *       block has support (a solid block directly below it), stickiness is not
 *       consumed. Without support the block "spreads": stickiness drains at
 *       {@code heaviness × rate} percent per second (e.g. heaviness 5 → -5% per
 *       second).</li>
 *   <li><b>Heaviness</b> — the block's weight (from the material table + config overrides).</li>
 * </ul>
 * When stickiness reaches 0% the block <b>collapses</b>: it breaks and drops as an
 * item, as if mined with a netherite tool. When a block collapses, only the blocks
 * above it that ALSO have 0% stickiness fall with it (cascade stops at any block
 * with stickiness &gt; 0 — those are still holding). Placing a block onto the edge of
 * a block with 0% stickiness also triggers a collapse.
 * <p>
 * <b>Async chunk scanning</b>: when a player places or breaks a block, the 16×16×16
 * chunk area around the action is queued for scanning. Scans run in a dedicated
 * executor thread (via {@link ChunkSnapshot}, thread-safe), processed lazily one job
 * per second, and are paused while the server MSPT is above the configured threshold.
 * This lets even natural structures (overhangs, floating islands) be discovered and
 * eventually collapse.
 * <p>
 * The lore of block items in inventories shows only «Heaviness: X» (stickiness is
 * always 100 when a block is freshly placed).
 * <p>
 * 💾 State is kept in RAM and periodically flushed to the plugin DB
 * (table {@code block_collapse}, every 10 minutes by default) so data is not lost
 * on shutdown.
 */
public class BlockCollapseManager extends BukkitRunnable {

    private static final String DB_TABLE = "block_collapse";

    private static BlockCollapseManager instance;

    private final Main plugin;

    // ===== КОНФИГ =====
    private boolean enabled = false;
    /** Множитель формулы: липкость -= heaviness × rate (в секунду). rate=1 → heaviness% в сек. */
    private double rate = 1.0;
    private int intervalTicks = 20;
    private int saveIntervalSeconds = 600; // 10 min
    private boolean loreEnabled = true;
    private int loreIntervalTicks = 100;
    private double defaultHeaviness = 5.0;
    private final Map<String, Double> overrides = new HashMap<>();

    // ===== СКАН-КОНФИГ =====
    private boolean scanEnabled = true;
    private int scanDelaySeconds = 1;        // задержка перед первым сканом
    private int scanIntervalSeconds = 1;     // одна операция в N секунд (lazy)
    private int scanArea = 16;               // сторона куба 16x16x16
    private double scanMaxMspt = 50.0;       // ждать, пока MSPT не упадёт ниже
    private boolean logBlockActions = false; // логировать каждое действие игрока с блоками

    // ===== СОСТОЯНИЕ (ОЗУ) =====
    /** world name → (block key → текущая липкость 0..100). Все отслеживаемые твёрдые блоки. */
    private final Map<String, Map<Long, Double>> tracked = new ConcurrentHashMap<>();
    /** ключи, изменившиеся с прошлого сохранения в БД. */
    private final Set<DirtyKey> dirtyKeys = ConcurrentHashMap.newKeySet();

    /** Очередь сканирований чанков (lazy — одна операция в секунду). */
    private final ConcurrentLinkedQueue<ScanJob> scanQueue = new ConcurrentLinkedQueue<>();
    /** Дедупликация: chunk key, уже стоящий в очереди (world|cx|cz). */
    private final Set<String> queuedChunks = ConcurrentHashMap.newKeySet();
    /** Поток-исполнитель асинхронного сканирования. */
    private ExecutorService scanExecutor;
    /** Таск, обрабатывающий очередь раз в секунду (main thread). */
    private BukkitTask scanTask;

    private int tickCounter = 0;
    private boolean taskScheduled = false;

    private BlockCollapseManager(Main plugin) {
        this.plugin = plugin;
    }

    // =========================
    // INIT / SHUTDOWN / RELOAD
    // =========================

    public static void init(Main plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new BlockCollapseManager(plugin);
        instance.taskScheduled = false;
        instance.reloadConfig();
        instance.loadFromDb();
        instance.runTaskTimer(plugin, 40L, instance.intervalTicks);
        instance.taskScheduled = true;
        instance.startScanTask();
        ConsoleLogger.info("[BLOCK_COLLAPSE] Initialized (enabled=" + instance.enabled
                + ", rate=" + instance.rate + ", interval=" + instance.intervalTicks + "t"
                + ", scan=" + instance.scanEnabled + ")");
    }

    public static BlockCollapseManager getInstance() {
        return instance;
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
            instance.startScanTask();
            ConsoleLogger.info("[BLOCK_COLLAPSE] Config reloaded (enabled=" + instance.enabled + ")");
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.saveDirty();
            instance.cancel();
            instance.stopScanTask();
            if (instance.scanExecutor != null) {
                instance.scanExecutor.shutdownNow();
                instance.scanExecutor = null;
            }
            instance.scanQueue.clear();
            instance.queuedChunks.clear();
            instance = null;
            ConsoleLogger.info("[BLOCK_COLLAPSE] Shutdown: state flushed to DB.");
        }
    }

    public void reloadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("features.block_collapse");
        if (sec == null) {
            enabled = false;
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Config section 'features.block_collapse' not found"
                    + " — system DISABLED. Add the section to config.yml to enable it.");
            return;
        }

        enabled = sec.getBoolean("enabled", false);
        rate = sec.getDouble("rate", 1.0);
        saveIntervalSeconds = Math.max(1, sec.getInt("save_interval_seconds", 600));
        loreEnabled = sec.getBoolean("lore_enabled", true);
        loreIntervalTicks = Math.max(1, sec.getInt("lore_interval_ticks", 100));
        defaultHeaviness = sec.getDouble("default_heaviness", 5.0);

        overrides.clear();
        ConfigurationSection ov = sec.getConfigurationSection("heaviness_overrides");
        if (ov != null) {
            for (String key : ov.getKeys(false)) {
                try {
                    overrides.put(key.toUpperCase(Locale.ROOT), ov.getDouble(key));
                } catch (Exception ignored) {
                    // невалидное значение — пропускаем
                }
            }
        }

        // ── Scan-настройки ──
        logBlockActions = sec.getBoolean("log_block_actions", false);
        ConfigurationSection sc = sec.getConfigurationSection("scan");
        if (sc != null) {
            scanEnabled = sc.getBoolean("enabled", true);
            scanDelaySeconds = Math.max(0, sc.getInt("delay_seconds", 1));
            scanIntervalSeconds = Math.max(1, sc.getInt("interval_seconds", 1));
            scanArea = Math.max(1, Math.min(64, sc.getInt("area", 16)));
            scanMaxMspt = Math.max(1.0, sc.getDouble("max_mspt", 50.0));
        } else {
            scanEnabled = true;
            scanDelaySeconds = 1;
            scanIntervalSeconds = 1;
            scanArea = 16;
            scanMaxMspt = 50.0;
        }

        int newInterval = Math.max(1, sec.getInt("interval_ticks", 20));
        if (newInterval != intervalTicks) {
            intervalTicks = newInterval;
            if (instance != null && taskScheduled) {
                try {
                    instance.cancel();
                    instance.runTaskTimer(plugin, 40L, intervalTicks);
                } catch (Exception e) {
                    ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to restart task: " + e.getMessage());
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =========================
    // СВОЙСТВА БЛОКОВ
    // =========================

    /** Участвует ли блок в механике: твёрдый блок (цветы и т.п. — нет). */
    public static boolean isStructural(Material mat) {
        return mat != null && mat != Material.AIR && mat.isBlock() && mat.isSolid();
    }

    /** Тяжесть блока: override из конфига или таблица по названию материала. */
    public double heaviness(Material mat) {
        Double ov = overrides.get(mat.name());
        if (ov != null) {
            return ov;
        }
        return defaultHeaviness(mat);
    }

    /**
     * Таблица тяжести по названию материала (чем «тяжелее» блок — тем быстрее
     * тратится липкость). Самое специфичное — раньше.
     */
    private double defaultHeaviness(Material mat) {
        String n = mat.name();

        if (n.startsWith("NETHERITE")) return 20;
        if (n.contains("ANCIENT_DEBRIS")) return 18;
        if (n.contains("DIAMOND")) return 16;
        if (n.contains("EMERALD")) return 15;
        if (n.contains("GOLD")) return 14;
        if (n.contains("IRON")) return 13;
        if (n.contains("COPPER")) return 12;
        if (n.contains("OBSIDIAN")) return 12;
        if (n.contains("CONCRETE")) return 10;
        if (n.contains("BRICK")) return 9;
        // Важно: SANDSTONE / END_STONE / REDSTONE содержат подстроку «STONE»,
        // поэтому 7-группа проверяется ДО правила STONE (8).
        if (n.contains("SANDSTONE") || n.contains("QUARTZ") || n.contains("TUFF")
                || n.contains("BASALT") || n.contains("BLACKSTONE") || n.contains("PRISMARINE")
                || n.contains("PURPUR") || n.contains("END_STONE") || n.contains("GRANITE")
                || n.contains("DIORITE") || n.contains("ANDESITE") || n.contains("AMETHYST")
                || n.contains("CALCITE") || n.contains("DRIPSTONE")) return 7;
        if (n.contains("DEEPSLATE") || n.contains("COBBLESTONE") || n.contains("STONE")) return 8;
        if (n.contains("TERRACOTTA")) return 6;
        if (n.contains("PLANKS") || n.contains("LOG") || n.contains("WOOD") || n.contains("FENCE")
                || n.contains("GATE") || n.contains("BARREL") || n.contains("BOOKSHELF")
                || n.contains("CRAFTING_TABLE")) return 4;
        if (n.contains("GRAVEL") || n.contains("CLAY")) return 5;
        if (n.contains("DIRT") || n.contains("GRASS_BLOCK") || n.contains("MUD") || n.contains("SAND")
                || n.contains("PODZOL") || n.contains("MYCELIUM") || n.contains("NETHERRACK")) return 4;
        if (n.contains("GLASS")) return 2;
        if (n.contains("ICE")) return 2;
        if (n.contains("WOOL")) return 2;
        if (n.contains("LEAVES") || n.contains("MOSS") || n.contains("HAY") || n.contains("SPONGE")
                || n.contains("SLIME") || n.contains("HONEY")) return 1;

        return defaultHeaviness;
    }

    // =========================
    // СОБЫТИЯ (вызываются из BlockCollapseListener)
    // =========================

    /** Блок поставлен игроком. */
    public void onBlockPlaced(Block block) {
        if (!enabled || block == null) return;
        if (!isStructural(block.getType())) return;
        // Функциональные блоки (сундуки, печи, маяки...) не участвуют —
        // их обрушение потеряло бы инвентарь/данные тил-эннити.
        if (block.getState() instanceof TileState) return;

        String worldName = block.getWorld().getName();
        long key = LocationUtil.toKey(block.getLocation());
        tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>()).put(key, 100.0);
        dirtyKeys.add(new DirtyKey(worldName, key));

        // Логируем операцию игрока с блоком (если включено в конфиге)
        if (logBlockActions) {
            ConsoleLogger.info("[BLOCK_COLLAPSE] PLACE " + worldName
                    + " " + block.getX() + " " + block.getY() + " " + block.getZ()
                    + " " + block.getType().name() + " (queued)");
        }

        // Сканируем область вокруг действия (лениво, в фоне)
        scheduleScan(block.getWorld(), block.getX(), block.getY(), block.getZ());

        // Правило: постановка на грань блока с липкостью 0% → обрушение.
        Block below = block.getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ());
        long belowKey = LocationUtil.toKey(below.getLocation());
        Map<Long, Double> map = tracked.get(worldName);
        if (map != null) {
            Double belowStick = map.get(belowKey);
            if (belowStick != null && belowStick <= 0) {
                collapseColumn(block.getWorld(), block.getX(), block.getY() - 1, block.getZ());
            }
        }
    }

    /** Блок сломан (игроком, взрывом, поршнем и т.п.). */
    public void onBlockBroken(Block block) {
        if (!enabled || block == null) return;
        removeTracked(block.getWorld().getName(), LocationUtil.toKey(block.getLocation()));

        // Логируем операцию игрока с блоком (если включено в конфиге)
        if (logBlockActions) {
            ConsoleLogger.info("[BLOCK_COLLAPSE] BREAK " + block.getWorld().getName()
                    + " " + block.getX() + " " + block.getY() + " " + block.getZ()
                    + " " + block.getType().name() + " (queued)");
        }

        // Сканируем область вокруг действия (лениво, в фоне)
        scheduleScan(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /** Пакет блоков уничтожен взрывом. */
    public void onBlocksDestroyed(List<Block> blocks) {
        if (!enabled || blocks == null) return;
        for (Block b : blocks) {
            if (b != null) onBlockBroken(b);
        }
    }

    private void removeTracked(String worldName, long key) {
        Map<Long, Double> map = tracked.get(worldName);
        if (map != null && map.remove(key) != null) {
            deleteFromDb(worldName, key);
            dirtyKeys.remove(new DirtyKey(worldName, key));
        }
    }

    // =========================
    // 🔭 АСИНХРОННОЕ СКАНИРОВАНИЕ ЧАНКОВ (16x16x16 вокруг действия)
    // =========================

    /**
     * Ставит чанк в очередь сканирования (дедупликация по world|cx|cz).
     * Сам скан выполняется в отдельном потоке, лениво — одна операция в секунду.
     */
    private void scheduleScan(World world, int x, int y, int z) {
        if (!enabled || !scanEnabled || world == null) return;
        int cx = x >> 4;
        int cz = z >> 4;
        String key = world.getName() + "|" + cx + "|" + cz;
        if (!queuedChunks.add(key)) return; // уже в очереди
        scanQueue.add(new ScanJob(world.getName(), cx, cz, y));
    }

    /** Запускает (или перезапускает) lazy-таск обработки очереди. */
    private void startScanTask() {
        stopScanTask();
        if (!enabled || !scanEnabled) return;
        if (scanExecutor == null || scanExecutor.isShutdown()) {
            scanExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "UI-BlockCollapse-Scanner");
                t.setDaemon(true);
                return t;
            });
        }
        long delayTicks = Math.max(1L, scanDelaySeconds * 20L);
        long periodTicks = Math.max(1L, scanIntervalSeconds * 20L);
        scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                processOneScanJob();
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
    }

    private void stopScanTask() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    /** Берёт ОДИН чанк из очереди и запускает его async-скан (main thread, дёшево). */
    private void processOneScanJob() {
        if (!enabled || !scanEnabled) return;
        if (Bukkit.getAverageTickTime() > scanMaxMspt) {
            return; // сервер нагружен — ждём, пока MSPT не упадёт ниже порога
        }
        ScanJob job = scanQueue.poll();
        if (job == null) return;
        queuedChunks.remove(job.world() + "|" + job.cx() + "|" + job.cz());

        World world = Bukkit.getWorld(job.world());
        if (world == null) return;

        // Не загружаем чанки ради механики: если чанк уже выгрузился (игрок
        // телепортнулся за 1 сек), скан дропаем — поставленный блок уже
        // отслеживается напрямую в onBlockPlaced, а при новом действии в этом
        // чанке скан встанет в очередь заново.
        if (!world.isChunkLoaded(job.cx(), job.cz())) return;

        Chunk chunk = world.getChunkAt(job.cx(), job.cz());
        // ChunkSnapshot — immutable и thread-safe: собираем на main thread (O(чанк)),
        // читаем блоки на async thread.
        ChunkSnapshot snap = chunk.getChunkSnapshot(true, false, false);
        int cx = job.cx();
        int cz = job.cz();
        int baseY = job.baseY();

        scanExecutor.submit(() -> scanChunkAsync(world.getName(), cx, cz, baseY, snap));
    }

    /**
     * Async-скан: читает {@link ChunkSnapshot} в отдельном потоке, находит твёрдые
     * блоки в кубе 16×16×16 вокруг действия и добавляет их в {@code tracked}.
     */
    private void scanChunkAsync(String worldName, int cx, int cz, int baseY, ChunkSnapshot snap) {
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;

            int minY = Math.max(world.getMinHeight(), baseY - scanArea / 2);
            int maxY = Math.min(world.getMaxHeight() - 1, baseY + scanArea / 2 - 1);

            int x0 = cx << 4;
            int z0 = cz << 4;
            List<long[]> found = new ArrayList<>(256);

            for (int dy = minY; dy <= maxY; dy++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Material type = snap.getBlockType(lx, dy, lz);
                        if (!isStructural(type)) continue;
                        // Пропускаем функциональные блоки (сундуки и т.п.) по типу
                        if (isFunctionalType(type)) continue;
                        // Оптимизация: отслеживаем ТОЛЬКО блоки без опоры снизу —
                        // именно они могут тратить липкость и обрушиться. Блоки с
                        // опорой (натуральный грунт) в tick всё равно пропускаются
                        // (below.isSolid()), поэтому в память их не берём.
                        if (dy > world.getMinHeight()) {
                            Material below = snap.getBlockType(lx, dy - 1, lz);
                            if (below.isSolid()) continue;
                        }
                        found.add(new long[]{x0 + lx, dy, z0 + lz});
                    }
                }
            }

            if (found.isEmpty()) return;

            // Возвращаем результат на main thread
            Bukkit.getScheduler().runTask(plugin, () -> applyScanResults(worldName, found));
        } catch (Throwable t) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Async scan error: " + t.getMessage());
        }
    }

    /** Применяет результаты скана на main thread: добавляет новые блоки в tracked. */
    private void applyScanResults(String worldName, List<long[]> found) {
        if (!enabled) return;
        Map<Long, Double> map = tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        for (long[] pos : found) {
            long key = LocationUtil.toKey((int) pos[0], (int) pos[1], (int) pos[2]);
            if (map.containsKey(key)) continue; // уже отслеживается
            map.put(key, 100.0);
            dirtyKeys.add(new DirtyKey(worldName, key));
        }
    }

    /** Функциональные материалы, которые нельзя обрушивать (инвентарь/тил-эннити). */
    private static boolean isFunctionalType(Material mat) {
        String n = mat.name();
        return n.contains("CHEST") || n.contains("SHULKER_BOX") || n.contains("BARREL")
                || n.contains("FURNACE") || n.contains("HOPPER") || n.contains("DISPENSER")
                || n.contains("DROPPER") || n.contains("SIGN") || n.contains("BED")
                || n.contains("LECTERN") || n.contains("BEACON") || n.contains("SPAWNER")
                || n.contains("JUKEBOX") || n.contains("BREWING_STAND") || n.contains("ANVIL")
                || n.contains("BELL") || n.contains("CAMPFIRE") || n.contains("SCULK")
                || n.contains("CONDUIT") || n.contains("ENCHANTING_TABLE")
                || n.contains("DECORATED_POT") || n.contains("CRAFTER")
                || n.contains("BEEHIVE") || n.contains("BEE_NEST") || n.contains("STRUCTURE_BLOCK")
                || n.contains("JIGSAW") || n.contains("COMMAND_BLOCK") || n.contains("MOVING_PISTON")
                || n.contains("END_PORTAL") || n.contains("END_GATEWAY");
    }

    // =========================
    // TICK — трата липкости + обрушения
    // =========================

    @Override
    public void run() {
        if (!enabled) return;

        List<String> collapseWorlds = new ArrayList<>();
        List<long[]> collapsePos = new ArrayList<>();

        for (Map.Entry<String, Map<Long, Double>> we : tracked.entrySet()) {
            String worldName = we.getKey();
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            for (Map.Entry<Long, Double> e : we.getValue().entrySet()) {
                long key = e.getKey();
                int x = LocationUtil.getX(key);
                int y = LocationUtil.getY(key);
                int z = LocationUtil.getZ(key);

                // Не загружаем чанки ради механики — пропускаем выгруженные
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type == Material.AIR || !type.isSolid()) {
                    // блок исчез без BlockBreakEvent (взрыв и т.п.) — самоочистка
                    we.getValue().remove(key);
                    deleteFromDb(worldName, key);
                    dirtyKeys.remove(new DirtyKey(worldName, key));
                    continue;
                }

                // Опора: твёрдый блок прямо снизу (вертикальная, как у подмостков)
                Block below = world.getBlockAt(x, y - 1, z);
                if (below.getType().isSolid()) {
                    continue; // есть опора — липкость не тратится
                }

                double heavy = heaviness(type);
                double drain = heavy * rate * (intervalTicks / 20.0);
                double next = e.getValue() - drain;
                if (next <= 0) {
                    // Фиксируем 0 в карте, чтобы collapseColumn корректно определял
                    // границу каскада (лом только блоки с липкостью == 0).
                    e.setValue(0.0);
                    dirtyKeys.add(new DirtyKey(worldName, key));
                    collapseWorlds.add(worldName);
                    collapsePos.add(new long[]{x, y, z});
                } else {
                    e.setValue(next);
                    dirtyKeys.add(new DirtyKey(worldName, key));
                }
            }
        }

        // Обрабатываем обрушения ПОСЛЕ итерации (избегаем ConcurrentModification)
        for (int i = 0; i < collapsePos.size(); i++) {
            World world = Bukkit.getWorld(collapseWorlds.get(i));
            if (world == null) continue;
            long[] c = collapsePos.get(i);
            collapseColumn(world, (int) c[0], (int) c[1], (int) c[2]);
        }

        // Периодический лор-скан инвентарей (контент-осознанный: только когда надо)
        tickCounter++;
        int loreEvery = Math.max(1, loreIntervalTicks / Math.max(1, intervalTicks));
        if (loreEnabled && tickCounter % loreEvery == 0) {
            scanLore();
        }

        // Периодическое сохранение в БД (по умолчанию — раз в 10 минут)
        int saveEvery = Math.max(1, (saveIntervalSeconds * 20) / Math.max(1, intervalTicks));
        if (tickCounter % saveEvery == 0) {
            saveDirty();
        }
    }

    /**
     * Обрушение вертикальной колонны вверх: блок и все блоки с липкостью 0%
     * над ним падают предметами (как будто добыты незеритовым инструментом).
     * <p>Каскад останавливается на первом блоке с липкостью &gt; 0 (он ещё
     * держится и не ломается) или на неотслеживаемом блоке (натуральный камень).
     */
    private void collapseColumn(World world, int x, int y, int z) {
        String worldName = world.getName();
        Map<Long, Double> map = tracked.get(worldName);
        int cy = y;
        int guard = 0;

        while (map != null && guard++ < 512) {
            long key = LocationUtil.toKey(x, cy, z);
            Double stick = map.get(key);
            // Стоп-условия каскада:
            //  • блок не отслеживается (натуральный камень/опора) — не трогаем;
            //  • липкость > 0 — блок ещё держится, ломаем только блоки с 0%.
            if (stick == null || stick > 0) {
                break;
            }

            Block block = world.getBlockAt(x, cy, z);
            if (block.getType() == Material.AIR || !block.getType().isSolid()) {
                map.remove(key);
                deleteFromDb(worldName, key);
                break;
            }

            for (ItemStack drop : collapseDrops(block)) {
                world.dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            world.playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    block.getSoundGroup().getBreakSound(), 1.0f, 0.8f);

            block.setType(Material.AIR);
            map.remove(key);
            deleteFromDb(worldName, key);
            dirtyKeys.remove(new DirtyKey(worldName, key));
            cy++;
        }
    }

    /** Дропы при обрушении: как будто блок добыли незеритовым инструментом. */
    private List<ItemStack> collapseDrops(Block block) {
        Material type = block.getType();
        try {
            java.util.Collection<ItemStack> drops = block.getState().getDrops(netheriteTool(type), null);
            if (drops != null && !drops.isEmpty()) {
                return new ArrayList<>(drops);
            }
        } catch (Throwable ignored) {
            // API недоступен — fallback ниже
        }
        // Если блок ничего не дропает без шёлкового касания (стекло и т.п.) —
        // отдаём сам блок.
        return List.of(new ItemStack(type, 1));
    }

    private ItemStack netheriteTool(Material mat) {
        String n = mat.name();
        if (n.contains("WOOD") || n.contains("PLANK") || n.contains("LOG") || n.contains("FENCE")
                || n.contains("GATE") || n.contains("DOOR") || n.contains("TRAPDOOR")
                || n.contains("SIGN") || n.contains("BAMBOO") || n.contains("STEM")
                || n.contains("PUMPKIN") || n.contains("MELON")) {
            return new ItemStack(Material.NETHERITE_AXE);
        }
        if (n.contains("DIRT") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("CLAY")
                || n.contains("MUD") || n.contains("SNOW") || n.contains("SOUL")
                || n.contains("GRASS") || n.contains("PODZOL") || n.contains("MYCELIUM")
                || n.contains("FARMLAND") || n.contains("PATH")) {
            return new ItemStack(Material.NETHERITE_SHOVEL);
        }
        return new ItemStack(Material.NETHERITE_PICKAXE);
    }

    // =========================
    // LORE — «Stickiness/Heaviness» в описании блочного предмета
    // =========================

    private void scanLore() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i <= 40; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR) continue;
                Material type = item.getType();
                if (!isStructural(type)) continue;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) continue;

                var pdc = meta.getPersistentDataContainer();
                // Миграция старых предметов: убираем устаревшую строку «Stickiness»
                // из лора даже если PDC-тег уже проставлен (раньше показывались обе).
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                boolean cleaned = lore.removeIf(c -> plainText(c).contains("Stickiness:"));

                if (pdc.has(Keys.BLOCK_COLLAPSE_TAG, PersistentDataType.DOUBLE)) {
                    if (cleaned) {
                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }
                    continue;
                }
                // Не трогаем кастомные предметы плагина (у них есть PDC-теги)
                if (!pdc.isEmpty()) continue;

                double heavy = heaviness(type);
                // В лоре показываем только тяжесть — липкость у всех блоков всегда 100
                // (тратится по формуле heaviness% в секунду при отсутствии опоры).
                lore.add(MessageUtil.parse("<gray>Heaviness: <yellow>" + formatHeaviness(heavy) + "</yellow></gray>"));
                meta.lore(lore);
                pdc.set(Keys.BLOCK_COLLAPSE_TAG, PersistentDataType.DOUBLE, heavy);
                item.setItemMeta(meta);
            }
        }
    }

    private static String formatHeaviness(double h) {
        if (h == Math.floor(h)) {
            return String.valueOf((long) h);
        }
        return String.format(Locale.ROOT, "%.1f", h);
    }

    /** Простой текст Adventure-компонента (для поиска устаревших строк лора). */
    private static String plainText(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    // =========================
    // 💾 БД (периодический сброс + при шатдауне)
    // =========================

    private void loadFromDb() {
        if (!enabled || !DatabaseManager.isConnected()) return;
        String sql = "SELECT world, x, y, z, stickiness FROM " + DB_TABLE;
        int count = 0;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {

            while (rs.next()) {
                String worldName = rs.getString("world");
                long key = LocationUtil.toKey(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                        .put(key, rs.getDouble("stickiness"));
                count++;
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to load state from DB: " + e.getMessage());
        }
        if (count > 0) {
            ConsoleLogger.info("[BLOCK_COLLAPSE] Loaded " + count + " tracked block(s) from DB.");
        }
    }

    private void saveDirty() {
        if (!DatabaseManager.isConnected() || dirtyKeys.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO " + DB_TABLE
                + " (world, x, y, z, stickiness) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {

            for (DirtyKey dk : dirtyKeys) {
                Map<Long, Double> map = tracked.get(dk.world());
                if (map == null) continue;
                Double stick = map.get(dk.key());
                if (stick == null) continue; // блок удалён за это время

                st.setString(1, dk.world());
                st.setInt(2, LocationUtil.getX(dk.key()));
                st.setInt(3, LocationUtil.getY(dk.key()));
                st.setInt(4, LocationUtil.getZ(dk.key()));
                st.setDouble(5, stick);
                st.addBatch();
            }
            st.executeBatch();
            dirtyKeys.clear();

        } catch (Exception e) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to save state to DB: " + e.getMessage());
        }
    }

    private void deleteFromDb(String worldName, long key) {
        if (!DatabaseManager.isConnected()) return;
        String sql = "DELETE FROM " + DB_TABLE + " WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, worldName);
            st.setInt(2, LocationUtil.getX(key));
            st.setInt(3, LocationUtil.getY(key));
            st.setInt(4, LocationUtil.getZ(key));
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to delete from DB: " + e.getMessage());
        }
    }

    /** Ключ «изменился с прошлого сохранения»: мир + координаты блока. */
    private record DirtyKey(String world, long key) {}

    /** Задача сканирования чанка: мир + координаты чанка + Y действия игрока. */
    private record ScanJob(String world, int cx, int cz, int baseY) {}
}
