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

    // ===== CONFIG =====
    private boolean enabled = false;
    /** Formula multiplier: stickiness -= heaviness × rate (per second). rate=1 → heaviness% per sec. */
    private double rate = 1.0;
    private int intervalTicks = 20;
    private int saveIntervalSeconds = 600; // 10 min
    private boolean loreEnabled = true;
    private int loreIntervalTicks = 100;
    private double defaultHeaviness = 5.0;
    private final Map<String, Double> overrides = new HashMap<>();

    // ===== SCAN CONFIG =====
    private boolean scanEnabled = true;
    private int scanDelaySeconds = 1;        // delay before the first scan
    private int scanIntervalSeconds = 1;     // one operation every N seconds (lazy)
    private int scanArea = 16;               // cube side 16x16x16
    private double scanMaxMspt = 50.0;       // wait until MSPT drops below this
    private boolean logBlockActions = false; // log every player block action

    // ===== STATE (RAM) =====
    /** world name → (block key → current stickiness 0..100). All tracked solid blocks. */
    private final Map<String, Map<Long, Double>> tracked = new ConcurrentHashMap<>();
    /** keys that changed since the last DB save. */
    private final Set<DirtyKey> dirtyKeys = ConcurrentHashMap.newKeySet();

    /** Chunk scan queue (lazy — one operation per second). */
    private final ConcurrentLinkedQueue<ScanJob> scanQueue = new ConcurrentLinkedQueue<>();
    /** Deduplication: chunk key already queued (world|cx|cz). */
    private final Set<String> queuedChunks = ConcurrentHashMap.newKeySet();
    /** Thread executing the async scanning. */
    private ExecutorService scanExecutor;
    /** Task processing the queue once per second (main thread). */
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
                    // invalid value — skip
                }
            }
        }

        // ── Scan settings ──
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
    // BLOCK PROPERTIES
    // =========================

    /** Whether a block participates: must be solid (flowers etc. — no). */
    public static boolean isStructural(Material mat) {
        return mat != null && mat != Material.AIR && mat.isBlock() && mat.isSolid();
    }

    /** Block heaviness: config override or a table keyed by material name. */
    public double heaviness(Material mat) {
        Double ov = overrides.get(mat.name());
        if (ov != null) {
            return ov;
        }
        return defaultHeaviness(mat);
    }

    /**
     * Heaviness table by material name (the «heavier» the block — the faster
     * stickiness is spent). Most specific entries come first.
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
        // Important: SANDSTONE / END_STONE / REDSTONE contain the substring «STONE»,
        // so group 7 is checked BEFORE the STONE rule (8).
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
    // EVENTS (called from BlockCollapseListener)
    // =========================

    /** A block was placed by a player. */
    public void onBlockPlaced(Block block) {
        if (!enabled || block == null) return;
        if (!isStructural(block.getType())) return;
        // Functional blocks (chests, furnaces, beacons...) don't participate —
        // collapsing them would lose inventory/tile-entity data.
        if (block.getState() instanceof TileState) return;

        String worldName = block.getWorld().getName();
        long key = LocationUtil.toKey(block.getLocation());
        tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>()).put(key, 100.0);
        dirtyKeys.add(new DirtyKey(worldName, key));

        // Log the player's block operation (if enabled in config)
        if (logBlockActions) {
            ConsoleLogger.info("[BLOCK_COLLAPSE] PLACE " + worldName
                    + " " + block.getX() + " " + block.getY() + " " + block.getZ()
                    + " " + block.getType().name() + " (queued)");
        }

        // Scan the area around the action (lazily, in the background)
        scheduleScan(block.getWorld(), block.getX(), block.getY(), block.getZ());

        // Rule: placing on the edge of a block with 0% stickiness → collapse.
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

    /** A block was broken (by player, explosion, piston, etc.). */
    public void onBlockBroken(Block block) {
        if (!enabled || block == null) return;
        removeTracked(block.getWorld().getName(), LocationUtil.toKey(block.getLocation()));

        // Log the player's block operation (if enabled in config)
        if (logBlockActions) {
            ConsoleLogger.info("[BLOCK_COLLAPSE] BREAK " + block.getWorld().getName()
                    + " " + block.getX() + " " + block.getY() + " " + block.getZ()
                    + " " + block.getType().name() + " (queued)");
        }

        // Scan the area around the action (lazily, in the background)
        scheduleScan(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /** A batch of blocks was destroyed by an explosion. */
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
    // 🔭 ASYNC CHUNK SCANNING (16x16x16 around the action)
    // =========================

    /**
     * Queues a chunk for scanning (deduplicated by world|cx|cz).
     * The scan itself runs on a separate thread, lazily — one operation per second.
     */
    private void scheduleScan(World world, int x, int y, int z) {
        if (!enabled || !scanEnabled || world == null) return;
        int cx = x >> 4;
        int cz = z >> 4;
        String key = world.getName() + "|" + cx + "|" + cz;
        if (!queuedChunks.add(key)) return; // already queued
        scanQueue.add(new ScanJob(world.getName(), cx, cz, y));
    }

    /** Starts (or restarts) the lazy queue-processing task. */
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

    /** Takes ONE chunk from the queue and starts its async scan (main thread, cheap). */
    private void processOneScanJob() {
        if (!enabled || !scanEnabled) return;
        if (Bukkit.getAverageTickTime() > scanMaxMspt) {
            return; // server is loaded — wait until MSPT drops below the threshold
        }
        ScanJob job = scanQueue.poll();
        if (job == null) return;
        queuedChunks.remove(job.world() + "|" + job.cx() + "|" + job.cz());

        World world = Bukkit.getWorld(job.world());
        if (world == null) return;

        // Don't load chunks for the mechanic: if the chunk unloaded (the player
        // teleported within 1 sec), drop the scan — the placed block is already
        // tracked directly in onBlockPlaced, and a new action in this chunk
        // will re-queue the scan.
        if (!world.isChunkLoaded(job.cx(), job.cz())) return;

        Chunk chunk = world.getChunkAt(job.cx(), job.cz());
        // ChunkSnapshot is immutable and thread-safe: build it on the main thread (O(chunk)),
        // read blocks on the async thread.
        ChunkSnapshot snap = chunk.getChunkSnapshot(true, false, false);
        int cx = job.cx();
        int cz = job.cz();
        int baseY = job.baseY();

        scanExecutor.submit(() -> scanChunkAsync(world.getName(), cx, cz, baseY, snap));
    }

    /**
     * Async scan: reads a {@link ChunkSnapshot} on a separate thread, finds solid
     * blocks in the 16×16×16 cube around the action and adds them to {@code tracked}.
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
                        // Skip functional blocks (chests etc.) by type
                        if (isFunctionalType(type)) continue;
                        // Optimization: track ONLY blocks without support below —
                        // only they can spend stickiness and collapse. Blocks with
                        // support (natural ground) are skipped in tick anyway
                        // (below.isSolid()), so we don't keep them in memory.
                        if (dy > world.getMinHeight()) {
                            Material below = snap.getBlockType(lx, dy - 1, lz);
                            if (below.isSolid()) continue;
                        }
                        found.add(new long[]{x0 + lx, dy, z0 + lz});
                    }
                }
            }

            if (found.isEmpty()) return;

            // Return the result to the main thread
            Bukkit.getScheduler().runTask(plugin, () -> applyScanResults(worldName, found));
        } catch (Throwable t) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Async scan error: " + t.getMessage());
        }
    }

    /** Applies scan results on the main thread: adds new blocks to tracked. */
    private void applyScanResults(String worldName, List<long[]> found) {
        if (!enabled) return;
        Map<Long, Double> map = tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        for (long[] pos : found) {
            long key = LocationUtil.toKey((int) pos[0], (int) pos[1], (int) pos[2]);
            if (map.containsKey(key)) continue; // already tracked
            map.put(key, 100.0);
            dirtyKeys.add(new DirtyKey(worldName, key));
        }
    }

    /** Functional materials that must not collapse (inventory/tile-entities). */
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
    // TICK — stickiness drain + collapses
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

                // Don't load chunks for the mechanic — skip unloaded ones
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type == Material.AIR || !type.isSolid()) {
                    // block disappeared without BlockBreakEvent (explosion etc.) — self-cleanup
                    we.getValue().remove(key);
                    deleteFromDb(worldName, key);
                    dirtyKeys.remove(new DirtyKey(worldName, key));
                    continue;
                }

                // Support: a solid block directly below (vertical, like scaffolding)
                Block below = world.getBlockAt(x, y - 1, z);
                if (below.getType().isSolid()) {
                    continue; // has support — stickiness is not spent
                }

                double heavy = heaviness(type);
                double drain = heavy * rate * (intervalTicks / 20.0);
                double next = e.getValue() - drain;
                if (next <= 0) {
                    // Pin 0 in the map so collapseColumn correctly determines
                    // the cascade boundary (only breaks blocks with stickiness == 0).
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

        // Process collapses AFTER the iteration (avoids ConcurrentModification)
        for (int i = 0; i < collapsePos.size(); i++) {
            World world = Bukkit.getWorld(collapseWorlds.get(i));
            if (world == null) continue;
            long[] c = collapsePos.get(i);
            collapseColumn(world, (int) c[0], (int) c[1], (int) c[2]);
        }

        // Periodic lore scan of inventories (content-aware: only when needed)
        tickCounter++;
        int loreEvery = Math.max(1, loreIntervalTicks / Math.max(1, intervalTicks));
        if (loreEnabled && tickCounter % loreEvery == 0) {
            scanLore();
        }

        // Periodic DB save (default: every 10 minutes)
        int saveEvery = Math.max(1, (saveIntervalSeconds * 20) / Math.max(1, intervalTicks));
        if (tickCounter % saveEvery == 0) {
            saveDirty();
        }
    }

    /**
     * Collapses a vertical column upward: the block and all blocks with 0%
     * stickiness above it drop as items (as if mined with a netherite tool).
     * <p>The cascade stops at the first block with stickiness &gt; 0 (it still
     * holds and doesn't break) or at an untracked block (natural stone).
     */
    private void collapseColumn(World world, int x, int y, int z) {
        String worldName = world.getName();
        Map<Long, Double> map = tracked.get(worldName);
        int cy = y;
        int guard = 0;

        while (map != null && guard++ < 512) {
            long key = LocationUtil.toKey(x, cy, z);
            Double stick = map.get(key);
            // Cascade stop conditions:
            //  • block not tracked (natural stone/support) — don't touch it;
            //  • stickiness > 0 — block still holds, only break blocks at 0%.
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

    /** Drops on collapse: as if the block was mined with a netherite tool. */
    private List<ItemStack> collapseDrops(Block block) {
        Material type = block.getType();
        try {
            java.util.Collection<ItemStack> drops = block.getState().getDrops(netheriteTool(type), null);
            if (drops != null && !drops.isEmpty()) {
                return new ArrayList<>(drops);
            }
        } catch (Throwable ignored) {
            // API unavailable — fallback below
        }
        // If the block drops nothing without silk touch (glass etc.) —
        // give the block itself.
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
    // LORE — «Stickiness/Heaviness» in the block item's description
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
                // Migration of old items: remove the outdated «Stickiness» line
                // from the lore even if the PDC tag is already set (both used to show).
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                boolean cleaned = lore.removeIf(c -> plainText(c).contains("Stickiness:"));

                if (pdc.has(Keys.BLOCK_COLLAPSE_TAG, PersistentDataType.DOUBLE)) {
                    if (cleaned) {
                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }
                    continue;
                }
                // Don't touch the plugin's custom items (they have PDC tags)
                if (!pdc.isEmpty()) continue;

                double heavy = heaviness(type);
                // Only show heaviness in the lore — all blocks always have stickiness 100
                // (spent as heaviness% per second when unsupported).
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

    /** Plain text of an Adventure component (for finding outdated lore lines). */
    private static String plainText(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    // =========================
    // 💾 DB (periodic flush + on shutdown)
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
                if (stick == null) continue; // block removed in the meantime

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

    /** «Changed since last save» key: world + block coordinates. */
    private record DirtyKey(String world, long key) {}

    /** Chunk scan task: world + chunk coordinates + Y of the player's action. */
    private record ScanJob(String world, int cx, int cz, int baseY) {}
}
