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
 * RedstoneGuard — защита от перегрузки редстоуном.
 * <p>
 * Если тик редстоуна нагружает сервер больше чем {@code mspt_threshold} (50ms) —
 * плагин сканирует все чанки, находит чанк с наибольшим числом итераций и
 * блокирует его итерации ПОЛНОСТЬЮ И НАВСЕГДА (до ручной разблокировки
 * {@code /ui redstone unlock <номер>}). Затем нагрузка пересчитывается: если она
 * всё ещё выше порога — блокируется следующий самый нагруженный чанк, и так
 * повторяется, пока нагрузка не упадёт ниже порога.
 * <p>
 * 🔎 Блокируются ТОЛЬКО чанки с реальной редстоун-активностью (итерации выше
 * {@code chunk_iterations_limit}). Чанк без редстоун-итераций не замораживается —
 * чтобы не тратить ресурсы сервера впустую.
 * <p>
 * 📣 При блокировке уведомляются все игроки, находящиеся внутри этого чанка.
 * <p>
 * 💾 Заблокированные чанки сохраняются в БД плагина (таблица {@code redstone_blocks})
 * и переживают рестарт сервера.
 * <p>
 * Заблокированные чанки нумеруются (#1, #2, ...) и видны через
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
    /** Номер → заблокированный чанк (вечная блокировка). */
    private final Map<Integer, BlockedChunk> blockedChunks = new LinkedHashMap<>();
    /** O(1) проверка блокировки чанка (зеркало blockedChunks). */
    private final Set<ChunkKey> blockedKeys = new HashSet<>();
    private int nextBlockNumber = 1;
    /** Флаг: блокировки уже загружены из БД. */
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
     * 🔎 Проверка «редстоуновый ли чанк вообще»: замораживаем только чанки с
     * реальной редстоун-активностью (итераций больше лимита). Чанки без
     * редстоун-итераций не блокируются — не тратим ресурсы сервера впустую.
     */
    private boolean isRedstoneChunk(int iterations) {
        return iterations > chunkIterationsLimit;
    }

    /**
     * Вызывается раз в тик: если MSPT выше порога — блокирует чанк с наибольшим
     * числом итераций навсегда. На следующих тиках цикл повторяется, пока
     * нагрузка не упадёт ниже порога.
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

        // Самый нагруженный незаблокированный чанк (только с реальным редстоуном)
        ChunkKey hottest = null;
        int hottestIterations = 0;
        for (Map.Entry<ChunkKey, Integer> e : snapshot.entrySet()) {
            // 🔎 Без редстоун-итераций чанк не замораживается
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
        persistBlock(blocked); // 💾 в БД
        notifyChunkBlocked(mspt, hottest, number, hottestIterations);
        notifyPlayersInChunk(hottest, number); // 📣 игрокам внутри чанка
    }

    // =========================
    // УПРАВЛЕНИЕ БЛОКИРОВКАМИ
    // =========================

    /** Разблокирует чанк по номеру. Возвращает true если номер найден. */
    public boolean unlock(int number) {
        BlockedChunk removed = blockedChunks.remove(number);
        if (removed == null) return false;
        blockedKeys.remove(removed.key);
        deleteBlock(number); // 💾 из БД
        ConsoleLogger.info("[REDSTONE_GUARD] Chunk #" + number + " unblocked: " + removed.key);
        ServerOverloadNotify.broadcast(
                "<white>sᴇʀᴠᴇʀ <dark_gray>» <reset><white>Чанк </white><yellow>#" + number
                        + " </yellow><gray>(" + removed.key + ") </gray><green>разблокирован</green>"
        );
        return true;
    }

    /** Список всех заблокированных чанков (в порядке нумерации). */
    public List<BlockedChunk> getBlockedChunks() {
        ensureDbLoaded();
        return new ArrayList<>(blockedChunks.values());
    }

    public int getBlockedCount() {
        ensureDbLoaded();
        return blockedChunks.size();
    }

    // =========================
    // АЛЕРТЫ
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
     * 📣 Уведомляет всех игроков, которые СЕЙЧАС стоят в заблокированном чанке.
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
    // 💾 PERSISTENCE (БД плагина)
    // =========================

    /**
     * Загружает заблокированные чанки из БД (переживают рестарт). Безопасен при
     * вызове до инициализации БД — повторно попробует при следующем тике.
     */
    private void ensureDbLoaded() {
        if (dbLoaded) return;
        if (!DatabaseManager.isConnected()) return; // БД ещё не готова — повторим позже

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

    /** Сохраняет блокировку в БД. */
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

    /** Удаляет блокировку из БД при разблокировке. */
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
    // ДАННЫЕ
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

        /** Координаты центра чанка (для телепортации). */
        public int centerX() { return x * 16 + 8; }
        public int centerZ() { return z * 16 + 8; }

        @Override
        public String toString() {
            return world + ":" + x + "," + z;
        }
    }
}
