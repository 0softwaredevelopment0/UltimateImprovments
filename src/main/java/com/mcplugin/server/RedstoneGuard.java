package com.mcplugin.server;

import com.mcplugin.core.Main;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Считает итерации редстоуна и блокирует самые нагруженные чанки при перегрузке.
 */
public final class RedstoneGuard {

    private static RedstoneGuard instance;

    private final Main plugin;

    private boolean enabled = true;
    private double msptThreshold = 50.0;
    private int globalIterationsLimit = 800;
    private int chunkIterationsLimit = 100;
    private int blockDurationSeconds = 30;
    private int chunksPerTick = 1;

    private final Map<ChunkKey, Integer> currentTickCounts = new HashMap<>();
    private final Map<ChunkKey, Long> blockedUntilMs = new ConcurrentHashMap<>();

    private boolean overloadActive = false;

    private RedstoneGuard(Main plugin) {
        this.plugin = plugin;
    }

    public static void init(Main plugin) {
        instance = new RedstoneGuard(plugin);
        instance.reload();
    }

    public static RedstoneGuard getInstance() {
        return instance;
    }

    public static void reload() {
        if (instance != null) {
            instance.loadConfig();
            ConsoleLogger.info("[REDSTONE_GUARD] Config reloaded (enabled=" + instance.enabled + ")");
        }
    }

    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("redstone_guard.enabled", true);
        msptThreshold = cfg.getDouble("redstone_guard.mspt_threshold", 50.0);
        globalIterationsLimit = cfg.getInt("redstone_guard.global_iterations_limit", 800);
        chunkIterationsLimit = cfg.getInt("redstone_guard.chunk_iterations_limit", 100);
        blockDurationSeconds = cfg.getInt("redstone_guard.block_duration_seconds", 30);
        chunksPerTick = Math.max(1, cfg.getInt("redstone_guard.chunks_per_tick", 1));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void recordIteration(Chunk chunk) {
        if (!enabled) {
            return;
        }
        ChunkKey key = ChunkKey.from(chunk);
        synchronized (currentTickCounts) {
            currentTickCounts.merge(key, 1, Integer::sum);
        }
    }

    public boolean isChunkBlocked(Chunk chunk) {
        if (!enabled) {
            return false;
        }
        // pruneExpired() already called once per tick in tick() — no need to call here
        return isKeyBlocked(ChunkKey.from(chunk));
    }

    private boolean isKeyBlocked(ChunkKey key) {
        Long until = blockedUntilMs.get(key);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Вызывается раз в тик: анализ прошлого тика и блокировка чанков при перегрузке.
     */
    public void tick() {
        if (!enabled) {
            return;
        }

        pruneExpired();

        int globalCount;
        Map<ChunkKey, Integer> snapshot;

        synchronized (currentTickCounts) {
            globalCount = currentTickCounts.values().stream().mapToInt(Integer::intValue).sum();
            snapshot = new HashMap<>(currentTickCounts);
            currentTickCounts.clear();
        }

        double mspt = plugin.getServer().getAverageTickTime();
        boolean overloadNow = mspt > msptThreshold && globalCount > globalIterationsLimit;

        if (overloadNow) {
            if (!overloadActive) {
                notifyOverloadStarted(mspt, globalCount);
            }
            overloadActive = true;
            blockHottestChunks(snapshot, mspt);
        } else if (mspt < msptThreshold && globalCount <= globalIterationsLimit) {
            if (overloadActive) {
                notifyOverloadEnded(mspt, globalCount);
            }
            overloadActive = false;
        } else {
            // Partial recovery: one condition is still above threshold.
            // Reset overloadActive to avoid getting stuck in an ambiguous state.
            overloadActive = false;
        }
    }

    /**
     * Блокирует чанки, у которых итерации за тик {@code > chunkIterationsLimit}.
     * Чанки ниже лимита не трогает. Уже заблокированные пропускает.
     */
    private void blockHottestChunks(Map<ChunkKey, Integer> counts, double mspt) {
        if (counts.isEmpty()) {
            return;
        }

        List<Map.Entry<ChunkKey, Integer>> candidates = counts.entrySet().stream()
                .filter(e -> e.getValue() > chunkIterationsLimit)
                .filter(e -> !isKeyBlocked(e.getKey()))
                .sorted(Map.Entry.<ChunkKey, Integer>comparingByValue().reversed())
                .limit(chunksPerTick)
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        long until = System.currentTimeMillis() + blockDurationSeconds * 1000L;

        for (Map.Entry<ChunkKey, Integer> entry : candidates) {
            ChunkKey key = entry.getKey();
            blockedUntilMs.put(key, until);
            notifyChunkBlocked(mspt, key, entry.getValue());
        }
    }

    private void notifyOverloadStarted(double mspt, int globalCount) {
        String consoleMsg =
                "[Server/Warning] MSPT=" + mspt
                        + " REDSTONE=" + globalCount
                        + " (global limit " + globalIterationsLimit
                        + ", per-chunk limit " + chunkIterationsLimit + ")"
                        + " → redstone chunk blocking active";

        ConsoleLogger.warn(consoleMsg);

        String playerMsg =
                "<gray>[<white>Server</white><dark_gray>/</dark_gray><yellow>Warning</yellow>] <white>MSPT </white><red>" + mspt
                        + " </red><gray>→ </gray><red>Redstone overload </red><gray>(global </gray><yellow>" + globalCount
                        + "</yellow><gray>/</gray><yellow>" + globalIterationsLimit
                        + "</yellow><gray>, per-chunk limit </gray><yellow>" + chunkIterationsLimit + "</yellow><gray>)</gray>";

        ServerOverloadNotify.broadcast(playerMsg);
    }

    private void notifyOverloadEnded(double mspt, int globalCount) {
        String consoleMsg =
                "[Server/Info] MSPT=" + mspt
                        + " REDSTONE=" + globalCount
                        + " → redstone overload ended";

        ConsoleLogger.warn(consoleMsg);

        String playerMsg =
                "<gray>[<white>Server</white><dark_gray>/</dark_gray><white>Info</white>] <white>MSPT </white><red>" + mspt
                        + " </red><gray>→ </gray><green>Redstone overload ended </green><gray>(iterations </gray><yellow>" + globalCount + "</yellow><gray>)</gray>";

        ServerOverloadNotify.broadcast(playerMsg);
    }

    private void notifyChunkBlocked(double mspt, ChunkKey key, int iterations) {
        String consoleMsg =
                "[Server/Warning] MSPT=" + mspt
                        + " → BLOCKED CHUNK " + key
                        + " for " + blockDurationSeconds + "s"
                        + " (iterations=" + iterations
                        + ", chunk limit " + chunkIterationsLimit + ")";

        ConsoleLogger.warn(consoleMsg);

        String playerMsg =
                "<gray>[<white>Server</white><dark_gray>/</dark_gray><yellow>Warning</yellow>] <white>MSPT </white><red>" + mspt
                        + " </red><gray>→ </gray><red>Blocked chunk </red><yellow>" + key
                        + " </yellow><gray>for </gray><yellow>" + blockDurationSeconds + "s"
                        + " </yellow><gray>(iterations </gray><yellow>" + iterations + "</yellow><gray>)</gray>";

        ServerOverloadNotify.broadcast(playerMsg);
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        blockedUntilMs.entrySet().removeIf(e -> e.getValue() <= now);
    }

    public record ChunkKey(String world, int x, int z) {

        public static ChunkKey from(Chunk chunk) {
            return new ChunkKey(
                    chunk.getWorld().getName(),
                    chunk.getX(),
                    chunk.getZ()
            );
        }

        @Override
        public String toString() {
            return world + ":" + x + "," + z;
        }
    }
}
