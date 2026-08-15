package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * RedstoneGuard — protection against redstone overload.
 * <p>
 * If a redstone tick loads the server more than {@code mspt_threshold} (50ms) —
 * the plugin scans all chunks, finds the chunk with the most iterations and
 * blocks its iterations COMPLETELY AND FOREVER (until manual unlock via
 * {@code /ui redstone unlock <number>}). Then the load is recomputed: if it's
 * still above the threshold — the next most loaded chunk is blocked, and so
 * on until the load drops below the threshold.
 * <p>
 * 🔎 ONLY chunks with real redstone activity are blocked (iterations above
 * {@code chunk_iterations_limit}). A chunk without redstone iterations is not frozen —
 * to not waste server resources.
 * <p>
 * 📣 On blocking, all players inside that chunk are notified.
 * <p>
 * 💾 Blocked chunks are saved to the plugin DB (table {@code redstone_blocks})
 * and survive server restarts.
 * <p>
 * Blocked chunks are numbered (#1, #2, ...) and visible via
 * {@code /ui redstone list}.
 */
public final class RedstoneGuard {

    private static RedstoneGuard instance;

    private static final String DB_TABLE = "redstone_blocks";

    private final Main plugin;

    private boolean enabled = true;
    private double msptThreshold = 50.0;
    private int chunkIterationsLimit = 10;

    private final Map<ChunkKey, Integer> currentTickCounts = new HashMap<>();
    /** Number → blocked chunk (permanent block). */
    private final Map<Integer, BlockedChunk> blockedChunks = new LinkedHashMap<>();
    /** O(1) chunk-block check (mirror of blockedChunks). */
    private final Set<ChunkKey> blockedKeys = new HashSet<>();
    private int nextBlockNumber = 1;
    /** Flag: blocks already loaded from the DB. */
    private boolean dbLoaded = false;

    private RedstoneGuard(Main plugin) {
        this.plugin = plugin;
    }

    public static void init(Main plugin) {
        instance = new RedstoneGuard(plugin);
        instance.reload();
        instance.ensureDbLoaded();
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
        chunkIterationsLimit = Math.max(1, cfg.getInt("redstone_guard.chunk_iterations_limit", 10));
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
        return isKeyBlocked(ChunkKey.from(chunk));
    }

    private boolean isKeyBlocked(ChunkKey key) {
        return blockedKeys.contains(key);
    }

    /**
     * 🔎 Check «is the chunk redstone-active at all»: freeze only chunks with
     * real redstone activity (iterations above the limit). Chunks without
     * redstone iterations are not blocked — we don't waste server resources.
     */
    private boolean isRedstoneChunk(int iterations) {
        return iterations > chunkIterationsLimit;
    }

    /**
     * Called once per tick: if MSPT is above the threshold — blocks the chunk with
     * the most iterations forever. On the following ticks the cycle repeats until
     * the load drops below the threshold.
     */
    public void tick() {
        if (!enabled) {
            return;
        }
        ensureDbLoaded();

        Map<ChunkKey, Integer> snapshot;
        synchronized (currentTickCounts) {
            if (currentTickCounts.isEmpty()) return;
            snapshot = new HashMap<>(currentTickCounts);
            currentTickCounts.clear();
        }

        double mspt = plugin.getServer().getAverageTickTime();
        if (mspt <= msptThreshold) {
            return;
        }

        // The most loaded unblocked chunk (only with real redstone)
        ChunkKey hottest = null;
        int hottestIterations = 0;
        for (Map.Entry<ChunkKey, Integer> e : snapshot.entrySet()) {
            // 🔎 Without redstone iterations the chunk isn't frozen
            if (!isRedstoneChunk(e.getValue())) continue;
            if (isKeyBlocked(e.getKey())) continue;
            if (e.getValue() > hottestIterations) {
                hottestIterations = e.getValue();
                hottest = e.getKey();
            }
        }

        if (hottest == null) {
            return;
        }

        int number = nextBlockNumber++;
        BlockedChunk blocked = new BlockedChunk(number, hottest, System.currentTimeMillis(), hottestIterations);
        blockedChunks.put(number, blocked);
        blockedKeys.add(hottest);
        persistBlock(blocked); // 💾 to the DB
        notifyChunkBlocked(mspt, hottest, number, hottestIterations);
        notifyPlayersInChunk(hottest, number); // 📣 to players inside the chunk
    }

    // =========================
    // BLOCK MANAGEMENT
    // =========================

    /** Unblocks a chunk by number. Returns true if the number was found. */
    public boolean unlock(int number) {
        BlockedChunk removed = blockedChunks.remove(number);
        if (removed == null) return false;
        blockedKeys.remove(removed.key);
        deleteBlock(number); // 💾 from the DB
        ConsoleLogger.info("[REDSTONE_GUARD] Chunk #" + number + " unblocked: " + removed.key);
        ServerOverloadNotify.broadcast(
                "<white>sᴇʀᴠᴇʀ <dark_gray>» <reset><white>Чанк </white><yellow>#" + number
                        + " </yellow><gray>(" + removed.key + ") </gray><green>разблокирован</green>"
        );
        return true;
    }

    /** List of all blocked chunks (in numbering order). */
    public List<BlockedChunk> getBlockedChunks() {
        ensureDbLoaded();
        return new ArrayList<>(blockedChunks.values());
    }

    public int getBlockedCount() {
        ensureDbLoaded();
        return blockedChunks.size();
    }

    // =========================
    // ALERTS
    // =========================

    private void notifyChunkBlocked(double mspt, ChunkKey key, int number, int iterations) {
        String consoleMsg = "Server » MSPT=" + String.format("%.1f", mspt)
                + " → BLOCKED CHUNK #" + number + " " + key
                + " FOREVER (iterations=" + iterations + ", limit " + chunkIterationsLimit + ")";

        ConsoleLogger.warn(consoleMsg);

        ServerOverloadNotify.broadcast(
                "<white>sᴇʀᴠᴇʀ <dark_gray>» <reset><white>MSPT </white><red>" + String.format("%.1f", mspt)
                        + " </red><gray>→ </gray><red>Заблокирован чанк </red><yellow>#" + number
                        + " </yellow><gray>(" + key + ") </gray><red>навсегда</red>"
                        + " <dark_gray>| /ui redstone unlock " + number + "</dark_gray>"
        );
    }

    /**
     * 📣 Notifies all players currently standing in the blocked chunk.
     */
    private void notifyPlayersInChunk(ChunkKey key, int number) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            if (!player.getWorld().getName().equals(key.world())) continue;
            Chunk chunk = player.getLocation().getChunk();
            if (chunk.getX() == key.x() && chunk.getZ() == key.z()) {
                player.sendMessage(MessageUtil.parse(
                        "<red>⚠</red> <white>Ваш чанк </white><yellow>#" + number
                                + " </yellow><white>заморожен</white> <gray>(" + key
                                + ") — редстоун здесь отключён из-за перегрузки сервера.</gray>"
                ));
            }
        }
    }

    // =========================
    // 💾 PERSISTENCE (plugin DB)
    // =========================

    /**
     * Loads blocked chunks from the DB (survive restarts). Safe to call
     * before the DB is initialized — it will retry on the next tick.
     */
    private void ensureDbLoaded() {
        if (dbLoaded) return;
        if (!DatabaseManager.isConnected()) return; // DB not ready yet — retry later

        blockedChunks.clear();
        blockedKeys.clear();
        int maxNumber = 0;

        String sql = "SELECT block_number, world, chunk_x, chunk_z, blocked_at, iterations FROM " + DB_TABLE;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {

            while (rs.next()) {
                int number = rs.getInt("block_number");
                ChunkKey key = new ChunkKey(
                        rs.getString("world"),
                        rs.getInt("chunk_x"),
                        rs.getInt("chunk_z")
                );
                long blockedAt = rs.getLong("blocked_at");
                int iterations = rs.getInt("iterations");

                blockedChunks.put(number, new BlockedChunk(number, key, blockedAt, iterations));
                blockedKeys.add(key);
                if (number > maxNumber) maxNumber = number;
            }
            nextBlockNumber = maxNumber + 1;
            dbLoaded = true;

            ConsoleLogger.info("[REDSTONE_GUARD] Loaded " + blockedChunks.size()
                    + " blocked chunk(s) from DB (next #" + nextBlockNumber + ")");

        } catch (Exception e) {
            ConsoleLogger.warn("[REDSTONE_GUARD] Failed to load blocked chunks from DB: " + e.getMessage());
        }
    }

    /** Saves a block to the DB. */
    private void persistBlock(BlockedChunk bc) {
        if (!DatabaseManager.isConnected()) return;
        String sql = "INSERT OR REPLACE INTO " + DB_TABLE
                + " (block_number, world, chunk_x, chunk_z, blocked_at, iterations) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, bc.number());
            st.setString(2, bc.key().world());
            st.setInt(3, bc.key().x());
            st.setInt(4, bc.key().z());
            st.setLong(5, bc.blockedAtMs());
            st.setInt(6, bc.iterations());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[REDSTONE_GUARD] Failed to save block #" + bc.number() + " to DB: " + e.getMessage());
        }
    }

    /** Removes a block from the DB on unlock. */
    private void deleteBlock(int number) {
        if (!DatabaseManager.isConnected()) return;
        String sql = "DELETE FROM " + DB_TABLE + " WHERE block_number = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, number);
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[REDSTONE_GUARD] Failed to delete block #" + number + " from DB: " + e.getMessage());
        }
    }

    // =========================
    // DATA
    // =========================

    public record BlockedChunk(int number, ChunkKey key, long blockedAtMs, int iterations) {}

    public record ChunkKey(String world, int x, int z) {

        public static ChunkKey from(Chunk chunk) {
            return new ChunkKey(
                    chunk.getWorld().getName(),
                    chunk.getX(),
                    chunk.getZ()
            );
        }

        /** Chunk center coordinates (for teleportation). */
        public int centerX() { return x * 16 + 8; }
        public int centerZ() { return z * 16 + 8; }

        @Override
        public String toString() {
            return world + ":" + x + "," + z;
        }
    }
}
