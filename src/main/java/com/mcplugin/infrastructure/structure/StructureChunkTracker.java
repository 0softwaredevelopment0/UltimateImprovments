package com.mcplugin.infrastructure.structure;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * 📂 Отслеживает координаты чанков, в которых есть Marker'ы структур.
 * <p>
 * Сохраняет в {@code structure-chunks.json} — чтобы при старте сервера можно было
 * принудительно загрузить эти чанки, просканировать Marker'ы и перестроить
 * менеджеры (CableNetwork, BatteryManager, LightManager и т.д.).
 * <p>
 * Принудительная загрузка чанков + plugin chunk ticket гарантируют, что структуры
 * не пропадают после рестарта сервера.
 */
public class StructureChunkTracker {

    private static final String FILE_NAME = "structure-chunks.json";
    private static final Map<String, Set<ChunkPos>> chunksByWorld = new HashMap<>();

    private StructureChunkTracker() {}

    // ════════════════════════════════════════
    // CHUNK POS
    // ════════════════════════════════════════
    public record ChunkPos(int x, int z) {}

    // ════════════════════════════════════════
    // LOAD — прочитать JSON из файла
    // ════════════════════════════════════════
    public static void load() {
        chunksByWorld.clear();

        File file = new File(Main.getInstance().getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            Main.getInstance().getLogger().info("[StructureChunkTracker] No saved chunks found.");
            return;
        }

        try {
            String json = Files.readString(file.toPath()).trim();
            if (json.isEmpty() || json.equals("{}")) {
                Main.getInstance().getLogger().info("[StructureChunkTracker] Empty tracker.");
                return;
            }

            // Формат: {"world-uuid":[[cx,cz],[cx,cz],...], "world-uuid2":[[cx,cz],...]}
            // Парсим без Gson — свой ручной парсер

            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            // Разбиваем по world: "world-uuid":[[...]],"world-uuid2":[[...]]
            String[] worldEntries = json.split(",\\s*(?=\")");
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

                Set<ChunkPos> chunks = new HashSet<>();

                if (!coordsPart.isEmpty()) {
                    String[] coordPairs = coordsPart.split("\\],\\[");
                    for (String pair : coordPairs) {
                        pair = pair.replaceAll("[\\[\\]]", "").trim();
                        if (pair.isEmpty()) continue;
                        String[] xy = pair.split(",");
                        if (xy.length >= 2) {
                            try {
                                int cx = Integer.parseInt(xy[0].trim());
                                int cz = Integer.parseInt(xy[1].trim());
                                chunks.add(new ChunkPos(cx, cz));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                if (!chunks.isEmpty()) {
                    chunksByWorld.put(worldUid, chunks);
                }
            }

            Main.getInstance().getLogger().info("[StructureChunkTracker] Loaded " + countTotal() + " chunk positions.");
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[StructureChunkTracker] Failed to load: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════
    // SAVE — записать JSON на диск
    // ════════════════════════════════════════
    public static void save() {
        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            List<String> worldUids = new ArrayList<>(chunksByWorld.keySet());
            Collections.sort(worldUids);

            for (String worldUid : worldUids) {
                Set<ChunkPos> chunks = chunksByWorld.get(worldUid);
                if (chunks == null || chunks.isEmpty()) continue;

                if (!first) json.append(",");
                first = false;

                json.append("\"").append(worldUid).append("\":[");

                List<ChunkPos> sorted = new ArrayList<>(chunks);
                sorted.sort(Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z));

                boolean firstCp = true;
                for (ChunkPos cp : sorted) {
                    if (!firstCp) json.append(",");
                    firstCp = false;
                    json.append("[").append(cp.x()).append(",").append(cp.z()).append("]");
                }

                json.append("]");
            }

            json.append("}");

            File file = new File(Main.getInstance().getDataFolder(), FILE_NAME);
            Files.writeString(file.toPath(), json);

            Main.getInstance().getLogger().fine("[StructureChunkTracker] Saved " + countTotal() + " chunk positions.");

        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[StructureChunkTracker] Failed to save: " + e.getMessage(), e);
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
        Main.getInstance().getLogger().info("[StructureChunkTracker] Loaded " + loaded + " structure chunks.");
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
        save();
    }
}
