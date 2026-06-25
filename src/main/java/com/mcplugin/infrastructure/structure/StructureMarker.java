package com.mcplugin.infrastructure.structure;

import com.mcplugin.infrastructure.core.Main;
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
 *   - "structure_type" → String ("battery", "light", "cable", etc.)
 *   - "structure_id"   → String (UUID)
 * <p>
 * In-memory кэш строится из Marker'ов при загрузке чанка.
 * При разрушении блока — Marker удаляется, вся структура разбирается.
 * <p>
 * Преимущества:
 *   - Не зависит от БД (мир можно переименовать/перенести)
 *   - Marker сохраняется в world-файлах
 *   - Не требует загрузки на старте (ленивая инициализация через ChunkLoadEvent)
 */
public class StructureMarker {

    private static final NamespacedKey TYPE_KEY = new NamespacedKey("mcplugin", "structure_type");
    private static final NamespacedKey ID_KEY = new NamespacedKey("mcplugin", "structure_id");

    // ════════════════════════════════════════
    // CACHE: позиция блока → {type, uuid}
    // ════════════════════════════════════════
    private static final Map<Long, StructureData> byPosition = new HashMap<>();
    // UUID → все позиции блоков этой структуры
    private static final Map<UUID, Set<Long>> byUuid = new HashMap<>();

    public record StructureData(String type, UUID uuid) {}

    // ════════════════════════════════════════
    // КООРДИНАТНЫЙ КЛЮЧ
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

        // Проверяем, нет ли уже Marker на этом месте
        if (getAt(blockLoc) != null) return;

        long key = toKey(blockLoc);
        StructureData data = new StructureData(type, uuid);
        byPosition.put(key, data);
        byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(key);

        // Спавним Marker entity в центре блока
        Location center = blockLoc.toCenterLocation();
        Marker marker = blockLoc.getWorld().spawn(center, Marker.class);
        marker.setPersistent(true);

        PersistentDataContainer pdc = marker.getPersistentDataContainer();
        pdc.set(TYPE_KEY, PersistentDataType.STRING, type);
        pdc.set(ID_KEY, PersistentDataType.STRING, uuid.toString());
    }

    // ════════════════════════════════════════
    // GET — получить данные структуры по позиции
    // ════════════════════════════════════════
    public static StructureData getAt(Location blockLoc) {
        if (blockLoc == null) return null;
        return byPosition.get(toKey(blockLoc));
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

        long key = toKey(blockLoc);
        StructureData data = byPosition.remove(key);
        if (data != null) {
            Set<Long> positions = byUuid.get(data.uuid());
            if (positions != null) {
                positions.remove(key);
                if (positions.isEmpty()) byUuid.remove(data.uuid());
            }
        }

        // Удаляем Marker entity в мире
        removeMarkerEntityAt(blockLoc);
    }

    // ════════════════════════════════════════
    // REMOVE ALL — удалить ВСЕ Marker'ы структуры по UUID
    // ════════════════════════════════════════
    public static void removeAllByUuid(World world, UUID uuid) {
        if (world == null || uuid == null) return;

        Set<Long> positions = byUuid.remove(uuid);
        if (positions == null) return;

        for (long key : positions) {
            byPosition.remove(key);
            removeMarkerEntityAt(world, key);
        }
    }

    // ════════════════════════════════════════
    // GET ALL POSITIONS — получить все позиции структуры по UUID
    // ════════════════════════════════════════
    public static Set<Long> getPositionsByUuid(UUID uuid) {
        Set<Long> positions = byUuid.get(uuid);
        return positions != null ? new HashSet<>(positions) : Collections.emptySet();
    }

    // ════════════════════════════════════════
    // GET ALL UUIDs — получить все UUID в мире/кэше
    // ════════════════════════════════════════
    public static Set<UUID> getAllUuids() {
        return new HashSet<>(byUuid.keySet());
    }

    // ════════════════════════════════════════
    // SCAN CHUNK — просканировать чанк на Marker'ы и восстановить кэш
    // Используем getEntities() т.к. Chunk.getEntitiesByClass() не существует в Paper API
    // ════════════════════════════════════════
    public static void scanChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return;

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
            long key = toKey(loc);

            // Не перезаписываем существующую запись
            if (!byPosition.containsKey(key)) {
                byPosition.put(key, new StructureData(type, uuid));
                byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(key);
            }
        }
    }

    // ════════════════════════════════════════
    // SCAN ALL LOADED CHUNKS — пересканировать все загруженные чанки
    // ════════════════════════════════════════
    public static void scanAllLoadedChunks() {
        for (World world : Main.getInstance().getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    // ════════════════════════════════════════
    // GET ALL ENTRIES — получить все записи из кэша
    // ════════════════════════════════════════
    public static Set<Map.Entry<Long, StructureData>> getAllEntries() {
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
    // HELPER: удалить Marker entity на позиции
    // ════════════════════════════════════════
    private static void removeMarkerEntityAt(Location blockLoc) {
        World world = blockLoc.getWorld();
        if (world == null) return;

        Location center = blockLoc.toCenterLocation();
        world.getNearbyEntities(center, 0.5, 0.5, 0.5, e -> e instanceof Marker).forEach(e -> {
            e.remove();
        });
    }

    private static void removeMarkerEntityAt(World world, long key) {
        int x = getX(key), y = getY(key), z = getZ(key);
        Location center = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        world.getNearbyEntities(center, 0.5, 0.5, 0.5, e -> e instanceof Marker).forEach(e -> {
            e.remove();
        });
    }
}
