package com.mcplugin.infrastructure.structure;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * 🗺 Отслеживает координаты чанков, в которых есть Marker'ы структур.
 * <p>
 * Хранит данные в SQLite (таблица {@code structure_chunks}) — при старте сервера
 * эти чанки принудительно загружаются, а Marker'ы в них сканируются.
 * <p>
 * После загрузки чанка Marker entity сам поддерживает его загруженным
 * через plugin chunk ticket ({@link #addTicket}).
 */
public class StructureChunkTracker {

    private static final Map<String, Set<ChunkPos>> chunksByWorld = new HashMap<>();

    private StructureChunkTracker() {}

    // ════════════════════════════════════════
    // CHUNK POS
    // ════════════════════════════════════════
    public record ChunkPos(int x, int z) {}

    // ════════════════════════════════════════
    // LOAD — прочитать из SQLite + миграция из JSON
    // ════════════════════════════════════════
    public static void load() {
        chunksByWorld.clear();

        try (Connection con = DatabaseManager.getConnection()) {
            // Миграция из старого JSON-файла
            migrateFromJson(con);

            // Загружаем данные из SQLite
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT world, cx, cz FROM structure_chunks ORDER BY world, cx, cz");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String worldUid = rs.getString("world");
                    int cx = rs.getInt("cx");
                    int cz = rs.getInt("cz");
                    chunksByWorld.computeIfAbsent(worldUid, k -> new HashSet<>())
                            .add(new ChunkPos(cx, cz));
                }
            }

            int total = countTotal();
            if (total > 0) {
                Main.getInstance().getLogger().info("[StructureChunkTracker] Loaded " + total + " chunk positions from SQLite.");
            } else {
                Main.getInstance().getLogger().info("[StructureChunkTracker] No saved chunks in DB.");
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[StructureChunkTracker] Failed to load from DB", e);
        }
    }

    // ════════════════════════════════════════
    // SAVE — записать в SQLite (полная перезапись)
    // ════════════════════════════════════════
    public static void save() {
        try (Connection con = DatabaseManager.getConnection()) {
            // Очищаем таблицу
            try (PreparedStatement st = con.prepareStatement("DELETE FROM structure_chunks")) {
                st.executeUpdate();
            }

            // Вставляем все текущие записи
            try (PreparedStatement st = con.prepareStatement(
                    "INSERT OR IGNORE INTO structure_chunks (world, cx, cz) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, Set<ChunkPos>> entry : chunksByWorld.entrySet()) {
                    String worldUid = entry.getKey();
                    for (ChunkPos cp : entry.getValue()) {
                        st.setString(1, worldUid);
                        st.setInt(2, cp.x());
                        st.setInt(3, cp.z());
                        st.addBatch();
                    }
                }
                st.executeBatch();
            }

            Main.getInstance().getLogger().fine("[StructureChunkTracker] Saved " + countTotal() + " chunk positions to SQLite.");
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[StructureChunkTracker] Failed to save to DB", e);
        }
    }

    // ════════════════════════════════════════
    // REBUILD FROM CACHE — перестроить набор чанков из StructureMarker.getAllEntries()
    // ════════════════════════════════════════
    public static void rebuildFromCache() {
        chunksByWorld.clear();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            String fk = entry.getKey();  // "worldUid:x,y,z"
            String[] parts = fk.split(":");
            String worldUid = parts[0];
            String[] coords = parts[1].split(",");
            int x = Integer.parseInt(coords[0]);
            int z = Integer.parseInt(coords[2]);

            int cx = x >> 4;
            int cz = z >> 4;

            chunksByWorld.computeIfAbsent(worldUid, k -> new HashSet<>()).add(new ChunkPos(cx, cz));
        }

        save();
    }

    // ════════════════════════════════════════
    // LOAD TRACKED CHUNKS — при старте загрузить все чанки со структурами
    // и добавить plugin chunk ticket (чтобы не выгружались)
    // ════════════════════════════════════════
    public static void loadTrackedChunks() {
        int loaded = 0;
        for (Map.Entry<String, Set<ChunkPos>> entry : chunksByWorld.entrySet()) {
            String worldUid = entry.getKey();
            World world = Bukkit.getWorld(UUID.fromString(worldUid));
            if (world == null) {
                Main.getInstance().getLogger().warning("[StructureChunkTracker] World not found: " + worldUid);
                continue;
            }

            for (ChunkPos cp : entry.getValue()) {
                world.loadChunk(cp.x(), cp.z());  // загружает чанк (триггерит ChunkLoadEvent)
                world.addPluginChunkTicket(cp.x(), cp.z(), Main.getInstance());
                loaded++;
            }
        }
        if (loaded > 0) {
            Main.getInstance().getLogger().info("[StructureChunkTracker] Loaded " + loaded + " structure chunks.");
        }
    }

    // ════════════════════════════════════════
    // ADD TICKET — добавить plugin chunk ticket при установке Marker'а
    // ════════════════════════════════════════
    public static void addTicket(World world, int blockX, int blockZ) {
        if (world == null) return;
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        world.addPluginChunkTicket(cx, cz, Main.getInstance());
    }

    // ════════════════════════════════════════
    // COUNT TOTAL
    // ════════════════════════════════════════
    private static int countTotal() {
        int total = 0;
        for (Set<ChunkPos> chunks : chunksByWorld.values()) {
            total += chunks.size();
        }
        return total;
    }

    // ════════════════════════════════════════
    // CLEAR — очистить трекер (при полной очистке кэша структур)
    // ════════════════════════════════════════
    public static void clear() {
        chunksByWorld.clear();

        // Снимаем все chunk tickets плагина с загруженных чанков
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                world.removePluginChunkTicket(chunk.getX(), chunk.getZ(), Main.getInstance());
            }
        }

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("DELETE FROM structure_chunks")) {
            st.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[StructureChunkTracker] Failed to clear DB", e);
        }
    }

    // ════════════════════════════════════════
    // MIGRATION — импорт из старого JSON
    // ════════════════════════════════════════
    private static void migrateFromJson(Connection con) {
        java.io.File jsonFile = new java.io.File(Main.getInstance().getDataFolder(), "structure-chunks.json");
        if (!jsonFile.exists()) return;

        // Проверяем, есть ли уже данные в БД
        try (PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM structure_chunks");
             ResultSet rs = st.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                // БД уже заполнена — удаляем JSON и выходим
                jsonFile.delete();
                return;
            }
        } catch (Exception ignored) {}

        try {
            String json = java.nio.file.Files.readString(jsonFile.toPath()).trim();
            if (json.isEmpty() || json.equals("{}")) {
                jsonFile.delete();
                return;
            }

            // Парсим JSON: {"world-uuid":[[cx,cz],[cx,cz],...], "world-uuid2":[...]}
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            String[] worldEntries = json.split(",\\s*(?=\")");
            int imported = 0;

            try (PreparedStatement st = con.prepareStatement(
                    "INSERT OR IGNORE INTO structure_chunks (world, cx, cz) VALUES (?, ?, ?)")) {
                for (String entry : worldEntries) {
                    entry = entry.trim();
                    if (entry.isEmpty()) continue;

                    String[] parts = entry.split("\":\\[", 2);
                    if (parts.length < 2) continue;

                    String worldUid = parts[0].replaceAll("^\"|\"$", "").trim();
                    String coordsPart = parts[1];
                    if (coordsPart.startsWith("[")) coordsPart = coordsPart.substring(1);
                    while (coordsPart.endsWith("]")) {
                        coordsPart = coordsPart.substring(0, coordsPart.length() - 1);
                    }

                    if (coordsPart.isEmpty()) continue;

                    String[] coordPairs = coordsPart.split("\\],\\[");
                    for (String pair : coordPairs) {
                        pair = pair.replaceAll("[\\[\\]]", "").trim();
                        if (pair.isEmpty()) continue;
                        String[] xy = pair.split(",");
                        if (xy.length >= 2) {
                            try {
                                int cx = Integer.parseInt(xy[0].trim());
                                int cz = Integer.parseInt(xy[1].trim());
                                st.setString(1, worldUid);
                                st.setInt(2, cx);
                                st.setInt(3, cz);
                                st.addBatch();
                                imported++;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                st.executeBatch();
            }

            Main.getInstance().getLogger().info("[StructureChunkTracker] Migrated " + imported + " chunk positions from JSON to SQLite.");

            // Удаляем JSON после успешной миграции
            jsonFile.delete();
            Main.getInstance().getLogger().info("[StructureChunkTracker] Deleted old structure-chunks.json");
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[StructureChunkTracker] Failed to migrate from JSON", e);
        }
    }
}
