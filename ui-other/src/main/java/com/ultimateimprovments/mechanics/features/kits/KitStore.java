package com.ultimateimprovments.mechanics.features.kits;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KitStore — SQLite persistence + in-memory cache for the kit system.
 * <p>
 * Own table {@code kits} (independent of the core schema, same convention as
 * {@code SudoDatabase}/{@code NotesDatabase}). The cache is small by nature
 * (a server has tens of kits, each 27 item slots), so every lookup is a map hit
 * and writes go straight to SQLite.
 * <p>
 * Identity: {@link Kit#keyOf(String)} — plain name without MiniMessage tags,
 * lowercase. The raw MiniMessage name/description are stored for display only.
 */
public final class KitStore {

    private static final Map<String, Kit> CACHE = new LinkedHashMap<>();
    private static volatile boolean ready = false;

    private KitStore() {}

    /** True once the table exists and the cache has been loaded. */
    public static boolean isReady() {
        return ready;
    }

    // =========================================================================
    // INIT / RELOAD
    // =========================================================================

    public static synchronized void init() {
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS kits (
                    kit_key TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    items TEXT DEFAULT '',
                    created_by TEXT DEFAULT '',
                    created_at INTEGER DEFAULT 0,
                    updated_at INTEGER DEFAULT 0
                );
            """);

            ready = true;
            reload();
            ConsoleLogger.info("[Kits] Store ready, " + CACHE.size() + " kit(s) loaded.");

        } catch (Exception e) {
            ready = false;
            ConsoleLogger.error("[Kits] Store init failed: " + e.getMessage());
        }
    }

    /** Re-reads every kit from the database into the cache. */
    public static synchronized void reload() {
        CACHE.clear();
        if (!ready) return;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT kit_key, name, description, items, created_by, created_at, updated_at "
                             + "FROM kits ORDER BY name COLLATE NOCASE ASC");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Kit kit = new Kit(
                        rs.getString("kit_key"),
                        rs.getString("name"),
                        rs.getString("description"),
                        Kit.decodeItems(rs.getString("items")),
                        rs.getString("created_by"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at"));
                CACHE.put(kit.key(), kit);
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Reload failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // LOOKUPS
    // =========================================================================

    /** All kits in display-name order. */
    public static List<Kit> all() {
        return new ArrayList<>(CACHE.values());
    }

    public static int size() {
        return CACHE.size();
    }

    /** @return the kit addressing by the given (raw, possibly tagged) name, or null. */
    public static Kit get(String rawName) {
        String key = Kit.keyOf(rawName);
        return key.isEmpty() ? null : CACHE.get(key);
    }

    public static Kit getByKey(String key) {
        return key == null ? null : CACHE.get(key);
    }

    public static boolean exists(String rawName) {
        String key = Kit.keyOf(rawName);
        return !key.isEmpty() && CACHE.containsKey(key);
    }

    // =========================================================================
    // WRITES
    // =========================================================================

    /**
     * Inserts or updates a kit.
     *
     * @return true on success (cache + DB in sync)
     */
    public static synchronized boolean save(Kit kit) {
        if (!ready || kit == null || kit.key().isEmpty()) return false;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO kits (kit_key, name, description, items, created_by, created_at, updated_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                             + "ON CONFLICT(kit_key) DO UPDATE SET "
                             + "name = excluded.name, description = excluded.description, "
                             + "items = excluded.items, updated_at = excluded.updated_at")) {

            ps.setString(1, kit.key());
            ps.setString(2, kit.name());
            ps.setString(3, kit.description());
            ps.setString(4, Kit.encodeItems(kit.items()));
            ps.setString(5, kit.createdBy());
            ps.setLong(6, kit.createdAt());
            ps.setLong(7, kit.updatedAt());
            ps.executeUpdate();

            CACHE.put(kit.key(), kit);
            return true;

        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Save failed for '" + kit.key() + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Moves a kit to a new identity (rename). The row is rewritten under the new
     * key; the old one is removed.
     */
    public static synchronized boolean rename(Kit kit, String newKey, String newName) {
        if (!ready || kit == null || newKey == null || newKey.isEmpty()) return false;
        boolean keyChanged = !newKey.equals(kit.key());
        try (Connection con = DatabaseManager.getConnection()) {
            if (keyChanged) {
                try (PreparedStatement del = con.prepareStatement("DELETE FROM kits WHERE kit_key = ?")) {
                    del.setString(1, kit.key());
                    del.executeUpdate();
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Rename (delete old) failed: " + e.getMessage());
            return false;
        }
        Kit renamed = new Kit(newKey, newName, kit.description(), kit.items(),
                kit.createdBy(), kit.createdAt(), System.currentTimeMillis());
        if (!save(renamed)) return false;
        if (keyChanged) CACHE.remove(kit.key());
        return true;
    }

    public static synchronized boolean delete(String rawName) {
        if (!ready) return false;
        String key = Kit.keyOf(rawName);
        if (key.isEmpty() || !CACHE.containsKey(key)) return false;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM kits WHERE kit_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
            CACHE.remove(key);
            return true;
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Delete failed for '" + key + "': " + e.getMessage());
            return false;
        }
    }

    /** Read-only view for diagnostics. */
    public static Collection<Kit> snapshot() {
        return List.copyOf(CACHE.values());
    }
}
