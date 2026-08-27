package com.ultimateimprovments.structure;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * 🏷 Structure marker registry with FULL SQLite persistence.
 * <p>
 * <b>History:</b> previously every structure block spawned a Marker entity in the
 * world with PDC data, and the in-memory cache was rebuilt by scanning loaded chunks.
 * That was fragile: after a restart the cache was empty until chunks loaded, and
 * markers could be lost.
 * <p>
 * <b>Now:</b> structure data (world + coordinates → type + UUID) is stored in the
 * {@code structure_markers} DB table. The RAM cache is only a working copy; the
 * source of truth is the DB:
 * <ul>
 *   <li>{@link #place}/{@link #removeAt}/{@link #removeAllByUuid} write to the DB immediately;</li>
 *   <li>{@link #saveAll()} — full re-save (every 10 minutes and on shutdown);</li>
 *   <li>{@link #loadFromDatabase()} restores the cache at startup.</li>
 * </ul>
 * Marker entities are no longer spawned; on the first run after an update old
 * markers are imported into the DB and removed from the world ({@link #migrateLegacyMarkers}).
 * <p>
 * ⚠️ The cache key includes the world UUID — critical for multi-world setups!
 * Two different worlds with the same x,y,z will NOT collide.
 */
public class StructureMarker {

    private static final NamespacedKey TYPE_KEY = new NamespacedKey("ui", "structure_type");
    private static final NamespacedKey ID_KEY = new NamespacedKey("ui", "structure_id");

    // ════════════════════════════════════════
    // CACHE: world_uid:x:y:z → {type, uuid, worldUid}
    // (working copy of the DB data)
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

    /** Parses x from fullKey "worldUid:x,y,z" */
    public static int parseX(String fullKey) {
        String[] parts = fullKey.split(":");
        String[] coords = parts[1].split(",");
        return Integer.parseInt(coords[0]);
    }

    /** Parses y from fullKey */
    public static int parseY(String fullKey) {
        String[] parts = fullKey.split(":");
        String[] coords = parts[1].split(",");
        return Integer.parseInt(coords[1]);
    }

    /** Parses z from fullKey */
    public static int parseZ(String fullKey) {
        String[] parts = fullKey.split(":");
        String[] coords = parts[1].split(",");
        return Integer.parseInt(coords[2]);
    }

    /** Parses worldUid from fullKey */
    public static String parseWorldUid(String fullKey) {
        return fullKey.split(":")[0];
    }

    // ════════════════════════════════════════
    // COORD KEY (for backward compatibility — BatteryManager/LightManager use toKey)
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
    // LOAD FROM DATABASE — restore the cache from the DB (source of truth)
    // ════════════════════════════════════════
    public static void loadFromDatabase() {
        byPosition.clear();
        byUuid.clear();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT world, x, y, z, type, structure_uuid FROM structure_markers");
             ResultSet rs = st.executeQuery()) {

            int loaded = 0;
            while (rs.next()) {
                String worldUid = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String type = rs.getString("type");
                String uuidStr = rs.getString("structure_uuid");

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    continue; // corrupt row — skip
                }

                String fk = fullKey(worldUid, x, y, z);
                if (byPosition.containsKey(fk)) continue; // duplicate protection

                byPosition.put(fk, new StructureData(type, uuid, worldUid));
                byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(fk);
                loaded++;
            }

            if (loaded > 0) {
                ConsoleLogger.info("[StructureMarker] Loaded " + loaded + " structure markers from DB.");
            } else {
                ConsoleLogger.info("[StructureMarker] No structure markers in DB.");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[StructureMarker] Failed to load from DB: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // SAVE ALL — full rewrite of the table from the cache
    // (periodic re-save + on server shutdown)
    // ════════════════════════════════════════
    public static void saveAll() {
        try (Connection con = DatabaseManager.getConnection()) {
            boolean oldAutoCommit = con.getAutoCommit();
            try {
                // Transaction: DELETE + INSERT atomically — if a crash happens in the
                // middle, the table won't be left half-empty (otherwise data would be
                // lost because the source of truth is the DB).
                con.setAutoCommit(false);

                try (PreparedStatement del = con.prepareStatement("DELETE FROM structure_markers")) {
                    del.executeUpdate();
                }

                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT OR IGNORE INTO structure_markers (world, x, y, z, type, structure_uuid) VALUES (?, ?, ?, ?, ?, ?)")) {
                    int saved = 0;
                    for (Map.Entry<String, StructureData> entry : byPosition.entrySet()) {
                        StructureData data = entry.getValue();
                        String[] parts = entry.getKey().split(":");
                        if (parts.length < 2) continue;
                        String[] coords = parts[1].split(",");
                        if (coords.length < 3) continue;
                        try {
                            ins.setString(1, parts[0]);
                            ins.setInt(2, Integer.parseInt(coords[0]));
                            ins.setInt(3, Integer.parseInt(coords[1]));
                            ins.setInt(4, Integer.parseInt(coords[2]));
                            ins.setString(5, data.type());
                            ins.setString(6, data.uuid().toString());
                            ins.addBatch();
                            saved++;
                        } catch (NumberFormatException ignored) {
                            // Invalid coordinates — skip
                        }
                    }
                    ins.executeBatch();
                }

                con.commit();
            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (Exception ignored) {}
                throw e;
            } finally {
                try {
                    con.setAutoCommit(oldAutoCommit);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[StructureMarker] Failed to save to DB: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // MIGRATE LEGACY MARKERS — one-time import of old Marker entities into the DB
    // (first run after update: the DB is still empty, data lives in the Markers)
    // Imports only if the table is empty; in any case removes found Markers.
    // ════════════════════════════════════════
    public static void migrateLegacyMarkers() {
        boolean dbHasData = hasDataInDb();
        int imported = 0;
        int removed = 0;

        for (World world : Main.getInstance().getServer().getWorlds()) {
            String worldUid = world.getUID().toString();
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof Marker marker)) continue;
                    if (!marker.isValid() || marker.isDead()) continue;

                    PersistentDataContainer pdc = marker.getPersistentDataContainer();
                    String type = pdc.get(TYPE_KEY, PersistentDataType.STRING);
                    String uuidStr = pdc.get(ID_KEY, PersistentDataType.STRING);
                    if (type == null || uuidStr == null) {
                        // Marker without our data — leave it alone
                        continue;
                    }

                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException e) {
                        marker.remove();
                        removed++;
                        continue;
                    }

                    Location loc = marker.getLocation();
                    String fk = fullKey(worldUid, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

                    if (!dbHasData && !byPosition.containsKey(fk)) {
                        StructureData data = new StructureData(type, uuid, worldUid);
                        byPosition.put(fk, data);
                        byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(fk);
                        persistOne(data, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                        imported++;
                    }

                    // Remove the old Marker entity — the system is fully DB-based now
                    marker.remove();
                    removed++;
                }
            }
        }

        if (imported > 0 || removed > 0) {
            ConsoleLogger.info("[StructureMarker] Legacy marker migration: imported "
                    + imported + " to DB, removed " + removed + " entity(ies).");
        }
    }

    // ════════════════════════════════════════
    // PLACE — register a structure block (writes to DB immediately)
    // ════════════════════════════════════════
    public static void place(Location blockLoc, String type, UUID uuid) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        String fk = fullKey(blockLoc);
        if (byPosition.containsKey(fk)) return;  // structure already exists in this world+coords

        StructureData data = new StructureData(type, uuid, blockLoc.getWorld().getUID().toString());
        byPosition.put(fk, data);
        byUuid.computeIfAbsent(uuid, k -> new HashSet<>()).add(fk);

        // Immediate DB write — don't rely on RAM
        persistOne(data, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());

        // Add a plugin chunk ticket — the chunk stays loaded
        StructureChunkTracker.addTicket(blockLoc.getWorld(), blockLoc.getBlockX(), blockLoc.getBlockZ());

        // Update the chunk tracker
        StructureChunkTracker.rebuildFromCache();
    }

    // ════════════════════════════════════════
    // GET — get structure data at a position
    // ════════════════════════════════════════
    public static StructureData getAt(Location blockLoc) {
        if (blockLoc == null) return null;
        return byPosition.get(fullKey(blockLoc));
    }

    // ════════════════════════════════════════
    // GET TYPE — get the structure type
    // ════════════════════════════════════════
    public static String getType(Location blockLoc) {
        StructureData data = getAt(blockLoc);
        return data != null ? data.type() : null;
    }

    // ════════════════════════════════════════
    // GET UUID — get the structure UUID
    // ════════════════════════════════════════
    public static UUID getUUID(Location blockLoc) {
        StructureData data = getAt(blockLoc);
        return data != null ? data.uuid() : null;
    }

    // ════════════════════════════════════════
    // EXISTS — is there a structure at the position?
    // ════════════════════════════════════════
    public static boolean existsAt(Location blockLoc) {
        return getAt(blockLoc) != null;
    }

    // ════════════════════════════════════════
    // REMOVE — delete the structure at one position (deletes from DB immediately)
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

        deleteFromDb(fk);

        // Update the chunk tracker
        StructureChunkTracker.rebuildFromCache();
    }

    // ════════════════════════════════════════
    // REMOVE ALL — delete ALL blocks of a structure by UUID in the given world
    // ════════════════════════════════════════
    public static void removeAllByUuid(World world, UUID uuid) {
        if (world == null || uuid == null) return;

        Set<String> keys = byUuid.get(uuid);
        if (keys == null) return;

        String worldUid = world.getUID().toString();
        List<String> toRemove = new ArrayList<>();

        // First collect the keys of this world (don't delete while iterating)
        for (String fk : keys) {
            if (fk.startsWith(worldUid + ":")) {
                toRemove.add(fk);
            }
        }

        if (toRemove.isEmpty()) return;

        for (String fk : toRemove) {
            byPosition.remove(fk);
            keys.remove(fk);
        }

        // If no keys remain for this UUID — clean up byUuid
        if (keys.isEmpty()) {
            byUuid.remove(uuid);
        }

        // Delete from DB
        deleteFromDbByWorldUuid(worldUid, uuid);

        // Update the chunk tracker
        StructureChunkTracker.rebuildFromCache();
    }

    // ════════════════════════════════════════
    // GET ALL POSITIONS — get all fullKeys of a structure by UUID
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
    // GET ALL ENTRIES — all cache entries (fullKey → StructureData)
    // ════════════════════════════════════════
    public static Set<Map.Entry<String, StructureData>> getAllEntries() {
        return byPosition.entrySet();
    }

    // ════════════════════════════════════════
    // CLEAR — clear cache AND DB (full structure wipe)
    // ════════════════════════════════════════
    public static void clearCache() {
        byPosition.clear();
        byUuid.clear();

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("DELETE FROM structure_markers")) {
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[StructureMarker] Failed to clear DB: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // PURGE ORPHANED — remove from cache AND DB the markers with unknown UUIDs
    // Called after rebuildFromMarkers() to clean up orphaned entries.
    // ════════════════════════════════════════
    // ⚠️ IMPORTANT: entries in UNLOADED worlds are NOT removed — managers build
    // usedUuids only from loaded worlds, and deleting entries of an unloaded world
    // would permanently lose structure data (previously the loss was only temporary
    // — from the RAM cache; now the source of truth is the DB).
    public static void purgeOrphaned(Set<UUID> usedUuids) {
        Iterator<Map.Entry<String, StructureData>> it = byPosition.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StructureData> entry = it.next();
            StructureData data = entry.getValue();
            if (!usedUuids.contains(data.uuid())) {
                // Skip entries of unloaded worlds — structures are kept, and when the
                // world loads the managers rebuild and decide their fate.
                if (data.worldUid() != null && !isWorldLoaded(data.worldUid())) {
                    continue;
                }
                String fk = entry.getKey();
                it.remove();
                // Also remove from byUuid
                Set<String> uuidKeys = byUuid.get(data.uuid());
                if (uuidKeys != null) {
                    uuidKeys.remove(fk);
                    if (uuidKeys.isEmpty()) byUuid.remove(data.uuid());
                }
                // And from the DB, so the orphan doesn't return on the next load
                deleteFromDb(fk);
            }
        }
    }

    /** Whether the world with the given UID is loaded (safety for purgeOrphaned). */
    private static boolean isWorldLoaded(String worldUid) {
        try {
            return org.bukkit.Bukkit.getWorld(UUID.fromString(worldUid)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ════════════════════════════════════════
    // DATABASE HELPERS
    // ════════════════════════════════════════

    private static String fullKey(String worldUid, int x, int y, int z) {
        return worldUid + ":" + x + "," + y + "," + z;
    }

    /** Whether the structure_markers table has any rows. */
    private static boolean hasDataInDb() {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT COUNT(*) FROM structure_markers");
             ResultSet rs = st.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            return true; // on error assume data exists — do not run migration
        }
    }

    /** Immediate write of a single entry (INSERT OR REPLACE). */
    private static void persistOne(StructureData data, int x, int y, int z) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO structure_markers (world, x, y, z, type, structure_uuid) VALUES (?, ?, ?, ?, ?, ?)")) {
            st.setString(1, data.worldUid());
            st.setInt(2, x);
            st.setInt(3, y);
            st.setInt(4, z);
            st.setString(5, data.type());
            st.setString(6, data.uuid().toString());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[StructureMarker] Failed to persist marker at "
                    + x + "," + y + "," + z + ": " + e.getMessage());
        }
    }

    /** Deletes one entry by fullKey. */
    private static void deleteFromDb(String fk) {
        if (fk == null) return;
        String[] parts = fk.split(":");
        if (parts.length < 2) return;
        String[] coords = parts[1].split(",");
        if (coords.length < 3) return;
        try {
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement st = con.prepareStatement(
                         "DELETE FROM structure_markers WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                st.setString(1, parts[0]);
                st.setInt(2, x);
                st.setInt(3, y);
                st.setInt(4, z);
                st.executeUpdate();
            }
        } catch (NumberFormatException ignored) {
            // Invalid coordinates — skip
        } catch (Exception e) {
            ConsoleLogger.warn("[StructureMarker] Failed to delete marker: " + e.getMessage());
        }
    }

    /** Deletes all entries of a structure in a world by UUID. */
    private static void deleteFromDbByWorldUuid(String worldUid, UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM structure_markers WHERE world = ? AND structure_uuid = ?")) {
            st.setString(1, worldUid);
            st.setString(2, uuid.toString());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[StructureMarker] Failed to delete structure by UUID: " + e.getMessage());
        }
    }
}
