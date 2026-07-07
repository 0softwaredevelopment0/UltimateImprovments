package com.mcplugin.structure;

import com.mcplugin.core.Main;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Marker;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 🏷 Утилита для управления Marker-сущностями, заменяющими SQLite.
 * <p>
 * Каждый блок структуры получает Marker entity с PDC:
 *   - "structure_type" → String ("battery", "light", etc.)
 *   - "structure_id"   → String (UUID)
 * <p>
 * ⚠️ Ключ кэша включает world UUID — это критически важно для мультимиров!
 * Два разных мира с одинаковыми x,y,z НЕ пересекутся.
 * <p>
 * При разрушении блока — Marker удаляется, вся структура разбирается.
 */
public class StructureMarker {

    private static final NamespacedKey TYPE_KEY = new NamespacedKey("mcplugin", "structure_type");
    private static final NamespacedKey ID_KEY = new NamespacedKey("mcplugin", "structure_id");

    // ════════════════════════════════════════
    // CACHE: world_uid:x:y:z → {type, uuid, worldUid}
    // ════════════════════════════════════════
    private static final Map<String, StructureData> byPosition = new HashMap<>();
    // UUID → Set<fullKey> (world_uid:x:y:z)
    private static final Map<UUID, Set<String>> byUuid = new HashMap<>();

    public record StructureData(String type, UUID uuid, String worldUid) {}

    // ════════════════════════════════════════
    // WORLD-AWARE KEY
    // ════════════════════════════════════════
    public static String fullKey(World world, int x, int y, int z) {
        return world.getUID().toString() + ":" + x + "," + y + "," + z;
    }

    public static String fullKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return fullKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** Парсит x из fullKey "worldUid:x,y,z" */
    public static int parseX(String fullKey) {
        String[] parts = fullKey.split(":");
        String[] coords = parts[1].split(",");
        return Integer.parseInt(coords[0]);
    }

    /** Парсит y из fullKey */
    public static int parseY(String fullKey) {
        String[] parts = fullKey.split(":");
        String[] coords = parts[1].split(",");
        return Integer.parseInt(coords[1]);
    }

    /** Парсит z из fullKey */
    public static int parseZ(String fullKey) {
        String[] parts = fullKey.split(":");
        String[] coords = parts[1].split(",");
        return Integer.parseInt(coords[2]);
    }

    /** Парсит worldUid из fullKey */
    public static String parseWorldUid(String fullKey) {
        return fullKey.split(":")[0];
    }

    // ════════════════════════════════════════
    // COORD KEY (для обратной совместимости — BatteryManager/LightManager используют toKey)
    // ════════════════════════════════════════
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

    // ════════════════════════════════════════
    // PLACE — создать Marker на позиции блока
    // ════════════════════════════════════════
    public static void place(Location blockLoc, String type, UUID uuid) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        String fk = fullKey(blockLoc);
        if (byPosition.containsKey(fk)) return;  // уже есть Marker в этом мире+координатах

        StructureData data = new StructureData(type, uuid, blockLoc.getWorld().getUID().toString());
        byPosition.put(fk, data);
        byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(fk);

        // Спавним Marker entity в центре блока
        Location center = blockLoc.toCenterLocation();
        Marker marker = blockLoc.getWorld().spawn(center, Marker.class);
        marker.setPersistent(true);

        PersistentDataContainer pdc = marker.getPersistentDataContainer();
        pdc.set(TYPE_KEY, PersistentDataType.STRING, type);
        pdc.set(ID_KEY, PersistentDataType.STRING, uuid.toString());

        // Добавляем plugin chunk ticket — чанк останется загруженным
        StructureChunkTracker.addTicket(blockLoc.getWorld(), blockLoc.getBlockX(), blockLoc.getBlockZ());

        // Обновляем трекер чанков (сохраняем координаты в JSON)
        StructureChunkTracker.rebuildFromCache();
    }

    // ════════════════════════════════════════
    // GET — получить данные структуры по позиции
    // ════════════════════════════════════════
    public static StructureData getAt(Location blockLoc) {
        if (blockLoc == null) return null;
        return byPosition.get(fullKey(blockLoc));
    }

    // ════════════════════════════════════════
    // GET TYPE — получить тип структуры
    // ════════════════════════════════════════
    public static String getType(Location blockLoc) {
        StructureData data = getAt(blockLoc);
        return data != null ? data.type() : null;
    }

    // ════════════════════════════════════════
    // GET UUID — получить UUID структуры
    // ════════════════════════════════════════
    public static UUID getUUID(Location blockLoc) {
        StructureData data = getAt(blockLoc);
        return data != null ? data.uuid() : null;
    }

    // ════════════════════════════════════════
    // EXISTS — есть ли структура на позиции?
    // ════════════════════════════════════════
    public static boolean existsAt(Location blockLoc) {
        return getAt(blockLoc) != null;
    }

    // ════════════════════════════════════════
    // REMOVE — удалить Marker с одной позиции
    // ════════════════════════════════════════
    public static void removeAt(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        String fk = fullKey(blockLoc);
        StructureData data = byPosition.remove(fk);
        if (data != null) {
            Set<String> keys = byUuid.get(data.uuid());
            if (keys != null) {
                keys.remove(fk);
                if (keys.isEmpty()) byUuid.remove(data.uuid());
            }
        }

        removeMarkerEntityAt(blockLoc);

        // Обновляем трекер чанков
        StructureChunkTracker.rebuildFromCache();
    }

    // ════════════════════════════════════════
    // REMOVE ALL — удалить ВСЕ Marker'ы структуры по UUID в указанном мире
    // ════════════════════════════════════════
    public static void removeAllByUuid(World world, UUID uuid) {
        if (world == null || uuid == null) return;

        Set<String> keys = byUuid.get(uuid);
        if (keys == null) return;

        String worldUid = world.getUID().toString();
        List<String> toRemove = new ArrayList<>();

        // Сначала собираем ключи этого мира (не удаляем во время итерации)
        for (String fk : keys) {
            if (fk.startsWith(worldUid + ":")) {
                toRemove.add(fk);
            }
        }

        if (toRemove.isEmpty()) return;

        for (String fk : toRemove) {
            byPosition.remove(fk);
            keys.remove(fk);

            // Удаляем Marker entity в мире
            int x = parseX(fk), y = parseY(fk), z = parseZ(fk);
            Location center = new Location(world, x + 0.5, y + 0.5, z + 0.5);
            world.getNearbyEntities(center, 0.5, 0.5, 0.5, e -> e instanceof Marker).forEach(e -> e.remove());
        }

        // Если больше не осталось ключей для этого UUID — чистим byUuid
        if (keys.isEmpty()) {
            byUuid.remove(uuid);
        }

        // Обновляем трекер чанков
        StructureChunkTracker.rebuildFromCache();
    }

    // ════════════════════════════════════════
    // GET ALL POSITIONS — получить все fullKey структуры по UUID
    // ════════════════════════════════════════
    public static Set<String> getKeysByUuid(UUID uuid) {
        Set<String> keys = byUuid.get(uuid);
        return keys != null ? new HashSet<>(keys) : Collections.emptySet();
    }

    // ════════════════════════════════════════
    // GET ALL UUIDs
    // ════════════════════════════════════════
    public static Set<UUID> getAllUuids() {
        return new HashSet<>(byUuid.keySet());
    }

    // ════════════════════════════════════════
    // SCAN CHUNK — просканировать чанк на Marker'ы
    // @return true если были добавлены новые Marker'ы в кэш
    // ════════════════════════════════════════
    public static boolean scanChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return false;

        String worldUid = chunk.getWorld().getUID().toString();
        boolean foundNew = false;

        for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Marker marker)) continue;
            if (!marker.isValid() || marker.isDead()) continue;

            PersistentDataContainer pdc = marker.getPersistentDataContainer();
            String type = pdc.get(TYPE_KEY, PersistentDataType.STRING);
            String uuidStr = pdc.get(ID_KEY, PersistentDataType.STRING);

            if (type == null || uuidStr == null) continue;

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            Location loc = marker.getLocation();
            String fk = fullKey(worldUid, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            // Не перезаписываем существующую запись
            if (!byPosition.containsKey(fk)) {
                byPosition.put(fk, new StructureData(type, uuid, worldUid));
                byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(fk);
                foundNew = true;
            }
        }

        return foundNew;
    }

    private static String fullKey(String worldUid, int x, int y, int z) {
        return worldUid + ":" + x + "," + y + "," + z;
    }

    // ════════════════════════════════════════
    // SCAN ALL LOADED CHUNKS
    // ════════════════════════════════════════
    public static void scanAllLoadedChunks() {
        for (World world : Main.getInstance().getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    // ════════════════════════════════════════
    // GET ALL ENTRIES — все записи кэша (fullKey → StructureData)
    // ════════════════════════════════════════
    public static Set<Map.Entry<String, StructureData>> getAllEntries() {
        return byPosition.entrySet();
    }

    // ════════════════════════════════════════
    // CLEAR — очистить кэш (не трогает Marker'ы в мире)
    // ════════════════════════════════════════
    public static void clearCache() {
        byPosition.clear();
        byUuid.clear();
    }

    // ════════════════════════════════════════
    // PURGE ORPHANED — удалить из кэша Marker'ы, мир которых неизвестен (не загружен)
    // Вызывается после rebuildFromMarkers() чтобы подчистить orphaned entries.
    // ════════════════════════════════════════
    public static void purgeOrphaned(Set<UUID> usedUuids) {
        Iterator<Map.Entry<String, StructureData>> it = byPosition.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StructureData> entry = it.next();
            StructureData data = entry.getValue();
            if (!usedUuids.contains(data.uuid())) {
                String fk = entry.getKey();
                it.remove();
                // Также убираем из byUuid
                Set<String> uuidKeys = byUuid.get(data.uuid());
                if (uuidKeys != null) {
                    uuidKeys.remove(fk);
                    if (uuidKeys.isEmpty()) byUuid.remove(data.uuid());
                }
            }
        }
    }

    // ════════════════════════════════════════
    // HELPER: удалить Marker entity на позиции
    // ════════════════════════════════════════
    private static void removeMarkerEntityAt(Location blockLoc) {
        World world = blockLoc.getWorld();
        if (world == null) return;

        Location center = blockLoc.toCenterLocation();
        world.getNearbyEntities(center, 0.5, 0.5, 0.5, e -> e instanceof Marker).forEach(e -> e.remove());
    }
}
