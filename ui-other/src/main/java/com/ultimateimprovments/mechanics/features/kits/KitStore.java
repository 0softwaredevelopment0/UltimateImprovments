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
import java.util.UUID;

/**
 * KitStore — SQLite persistence + in-memory cache for the kit system.
 * <p>
 * Own tables (independent of the core schema, same convention as
 * {@code SudoDatabase}/{@code NotesDatabase}):
 * <ul>
 *   <li>{@code kits} — one row per kit, including its per-recipient cooldown
 *       ({@code cooldown_seconds}, 0 = none);</li>
 *   <li>{@code kit_cooldowns} — last time a player received a kit
 *       ({@code player_uuid}, {@code kit_key}, {@code last_used} epoch ms),
 *       so cooldowns survive restarts and {@code /ui reload}.</li>
 * </ul>
 * The caches are small by nature (a server has tens of kits, each 27 item
 * slots), so every lookup is a map hit and writes go straight to SQLite.
 * <p>
 * Identity: {@link Kit#keyOf(String)} — plain name without MiniMessage tags,
 * lowercase. The raw MiniMessage name/description are stored for display only.
 */
public final class KitStore {

    private static final Map<String, Kit> CACHE = new LinkedHashMap<>();
    /** "uuid:kit_key" → epoch millis when the cooldown was last started. */
    private static final Map<String, Long> COOLDOWNS = new LinkedHashMap<>();
    /** kit_key → epoch millis when the server-wide cooldown was last started. */
    private static final Map<String, Long> GLOBAL_COOLDOWNS = new LinkedHashMap<>();
    private static volatile boolean ready = false;

    private KitStore() {}

    /** True once the tables exist and the caches have been loaded. */
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
                    updated_at INTEGER DEFAULT 0,
                    cooldown_seconds INTEGER DEFAULT 0,
                    global_cooldown_seconds INTEGER DEFAULT 0
                );
            """);

            // Migration for databases created before kit cooldowns existed.
            if (!hasColumn(con, "kits", "cooldown_seconds")) {
                st.execute("ALTER TABLE kits ADD COLUMN cooldown_seconds INTEGER DEFAULT 0");
                ConsoleLogger.info("[Kits] Added the cooldown_seconds column to the kits table.");
            }
            if (!hasColumn(con, "kits", "global_cooldown_seconds")) {
                st.execute("ALTER TABLE kits ADD COLUMN global_cooldown_seconds INTEGER DEFAULT 0");
                ConsoleLogger.info("[Kits] Added the global_cooldown_seconds column to the kits table.");
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS kit_cooldowns (
                    player_uuid TEXT NOT NULL,
                    kit_key TEXT NOT NULL,
                    last_used INTEGER DEFAULT 0,
                    PRIMARY KEY (player_uuid, kit_key)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS kit_global_cooldowns (
                    kit_key TEXT PRIMARY KEY,
                    last_used INTEGER DEFAULT 0
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

    /** True when {@code table} already has a column named {@code column}. */
    private static boolean hasColumn(Connection con, String table, String column) throws Exception {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    /** Re-reads every kit and cooldown timestamp from the database into the caches. */
    public static synchronized void reload() {
        CACHE.clear();
        COOLDOWNS.clear();
        GLOBAL_COOLDOWNS.clear();
        if (!ready) return;
        try (Connection con = DatabaseManager.getConnection()) {

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT kit_key, name, description, items, created_by, created_at, updated_at, "
                            + "cooldown_seconds, global_cooldown_seconds FROM kits "
                            + "ORDER BY name COLLATE NOCASE ASC");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    Kit kit = new Kit(
                            rs.getString("kit_key"),
                            rs.getString("name"),
                            rs.getString("description"),
                            Kit.decodeItems(rs.getString("items")),
                            rs.getString("created_by"),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at"),
                            rs.getInt("cooldown_seconds"),
                            rs.getInt("global_cooldown_seconds"));
                    CACHE.put(kit.key(), kit);
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT player_uuid, kit_key, last_used FROM kit_cooldowns");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    COOLDOWNS.put(cooldownId(rs.getString("player_uuid"), rs.getString("kit_key")),
                            rs.getLong("last_used"));
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT kit_key, last_used FROM kit_global_cooldowns");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    GLOBAL_COOLDOWNS.put(rs.getString("kit_key"), rs.getLong("last_used"));
                }
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
                     "INSERT INTO kits (kit_key, name, description, items, created_by, created_at, updated_at, "
                             + "cooldown_seconds, global_cooldown_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON CONFLICT(kit_key) DO UPDATE SET "
                             + "name = excluded.name, description = excluded.description, "
                             + "items = excluded.items, updated_at = excluded.updated_at, "
                             + "cooldown_seconds = excluded.cooldown_seconds, "
                             + "global_cooldown_seconds = excluded.global_cooldown_seconds")) {

            ps.setString(1, kit.key());
            ps.setString(2, kit.name());
            ps.setString(3, kit.description());
            ps.setString(4, Kit.encodeItems(kit.items()));
            ps.setString(5, kit.createdBy());
            ps.setLong(6, kit.createdAt());
            ps.setLong(7, kit.updatedAt());
            ps.setInt(8, kit.cooldownSeconds());
            ps.setInt(9, kit.globalCooldownSeconds());
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
     * key; the old one is removed. Pending cooldowns follow the kit.
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
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE kit_cooldowns SET kit_key = ? WHERE kit_key = ?")) {
                    up.setString(1, newKey);
                    up.setString(2, kit.key());
                    up.executeUpdate();
                }
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE kit_global_cooldowns SET kit_key = ? WHERE kit_key = ?")) {
                    up.setString(1, newKey);
                    up.setString(2, kit.key());
                    up.executeUpdate();
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Rename (delete old) failed: " + e.getMessage());
            return false;
        }
        Kit renamed = new Kit(newKey, newName, kit.description(), kit.items(),
                kit.createdBy(), kit.createdAt(), System.currentTimeMillis(),
                kit.cooldownSeconds(), kit.globalCooldownSeconds());
        if (!save(renamed)) return false;
        if (keyChanged) {
            CACHE.remove(kit.key());
            moveCooldownKeys(kit.key(), newKey);
            Long global = GLOBAL_COOLDOWNS.remove(kit.key());
            if (global != null) GLOBAL_COOLDOWNS.put(newKey, global);
        }
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

            try (PreparedStatement cd = con.prepareStatement(
                    "DELETE FROM kit_cooldowns WHERE kit_key = ?")) {
                cd.setString(1, key);
                cd.executeUpdate();
            }
            try (PreparedStatement gd = con.prepareStatement(
                    "DELETE FROM kit_global_cooldowns WHERE kit_key = ?")) {
                gd.setString(1, key);
                gd.executeUpdate();
            }
            CACHE.remove(key);
            COOLDOWNS.keySet().removeIf(id -> id.endsWith(":" + key));
            GLOBAL_COOLDOWNS.remove(key);
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

    // =========================================================================
    // COOLDOWNS (per player + per kit)
    // =========================================================================

    /**
     * Whole seconds left before {@code player} may receive {@code kitKey} again.
     *
     * @return remaining seconds, {@code 0} when no cooldown is running
     */
    public static long remainingCooldownSeconds(UUID player, String kitKey) {
        if (!ready || player == null || kitKey == null) return 0;
        Kit kit = CACHE.get(kitKey);
        if (kit == null || !kit.hasCooldown()) return 0;
        Long lastUsed = COOLDOWNS.get(cooldownId(player.toString(), kitKey));
        if (lastUsed == null) return 0;
        long expiresAt = lastUsed + kit.cooldownSeconds() * 1000L;
        long leftMillis = expiresAt - System.currentTimeMillis();
        if (leftMillis <= 0) return 0;
        return (leftMillis + 999L) / 1000L; // round up so "0" is never shown early
    }

    /** Starts (or restarts) the cooldown of {@code kitKey} for {@code player}. */
    public static synchronized void markCooldown(UUID player, String kitKey) {
        if (!ready || player == null || kitKey == null) return;
        long now = System.currentTimeMillis();
        String id = cooldownId(player.toString(), kitKey);
        COOLDOWNS.put(id, now);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO kit_cooldowns (player_uuid, kit_key, last_used) VALUES (?, ?, ?) "
                             + "ON CONFLICT(player_uuid, kit_key) DO UPDATE SET last_used = excluded.last_used")) {
            ps.setString(1, player.toString());
            ps.setString(2, kitKey);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Cooldown save failed for '" + kitKey + "': " + e.getMessage());
        }
    }

    /** Clears a running cooldown (kept for admin tooling / future use). */
    public static synchronized void clearCooldown(UUID player, String kitKey) {
        if (!ready || player == null || kitKey == null) return;
        COOLDOWNS.remove(cooldownId(player.toString(), kitKey));
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM kit_cooldowns WHERE player_uuid = ? AND kit_key = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, kitKey);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Cooldown clear failed for '" + kitKey + "': " + e.getMessage());
        }
    }

    /**
     * Clears every per-player kit cooldown of one player ({@code /ui kit resetcd}).
     * Global (server-wide) cooldowns are not touched — they belong to the kit,
     * not to a player.
     *
     * @return number of in-memory cooldown entries that were running
     */
    public static synchronized int clearPlayerCooldowns(UUID player) {
        if (!ready || player == null) return 0;
        String prefix = player.toString() + ":";
        int removed = 0;
        java.util.Iterator<String> it = COOLDOWNS.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
                removed++;
            }
        }
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM kit_cooldowns WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Cooldown reset failed for '" + player + "': " + e.getMessage());
        }
        return removed;
    }

    /** Clears every running per-player cooldown of one kit (called when the kit cooldown is disabled). */
    public static synchronized void clearKitPlayerCooldowns(String kitKey) {
        if (!ready || kitKey == null) return;
        COOLDOWNS.keySet().removeIf(id -> id.endsWith(":" + kitKey));
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM kit_cooldowns WHERE kit_key = ?")) {
            ps.setString(1, kitKey);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Cooldown clear failed for kit '" + kitKey + "': " + e.getMessage());
        }
    }

    // =========================================================================
    // GLOBAL COOLDOWNS (one per kit, shared by every player)
    // =========================================================================

    /**
     * Whole seconds left before anyone may receive {@code kitKey} again.
     *
     * @return remaining seconds, {@code 0} when no global cooldown is running
     */
    public static long remainingGlobalCooldownSeconds(String kitKey) {
        if (!ready || kitKey == null) return 0;
        Kit kit = CACHE.get(kitKey);
        if (kit == null || !kit.hasGlobalCooldown()) return 0;
        Long lastUsed = GLOBAL_COOLDOWNS.get(kitKey);
        if (lastUsed == null) return 0;
        long expiresAt = lastUsed + kit.globalCooldownSeconds() * 1000L;
        long leftMillis = expiresAt - System.currentTimeMillis();
        if (leftMillis <= 0) return 0;
        return (leftMillis + 999L) / 1000L;
    }

    /** Starts (or restarts) the server-wide cooldown of {@code kitKey}. */
    public static synchronized void markGlobalCooldown(String kitKey) {
        if (!ready || kitKey == null) return;
        long now = System.currentTimeMillis();
        GLOBAL_COOLDOWNS.put(kitKey, now);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO kit_global_cooldowns (kit_key, last_used) VALUES (?, ?) "
                             + "ON CONFLICT(kit_key) DO UPDATE SET last_used = excluded.last_used")) {
            ps.setString(1, kitKey);
            ps.setLong(2, now);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Global cooldown save failed for '" + kitKey + "': " + e.getMessage());
        }
    }

    /** Stops a running global cooldown (used when the kit cooldown is disabled). */
    public static synchronized void clearGlobalCooldown(String kitKey) {
        if (!ready || kitKey == null) return;
        GLOBAL_COOLDOWNS.remove(kitKey);
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM kit_global_cooldowns WHERE kit_key = ?")) {
            ps.setString(1, kitKey);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Kits] Global cooldown clear failed for '" + kitKey + "': " + e.getMessage());
        }
    }

    /** Re-keys the in-memory cooldown entries after a kit rename. */
    private static void moveCooldownKeys(String oldKey, String newKey) {
        String suffix = ":" + oldKey;
        Map<String, Long> moved = new LinkedHashMap<>();
        COOLDOWNS.forEach((id, time) -> {
            if (id.endsWith(suffix)) {
                moved.put(id.substring(0, id.length() - suffix.length()) + ":" + newKey, time);
            }
        });
        COOLDOWNS.keySet().removeIf(id -> id.endsWith(suffix));
        COOLDOWNS.putAll(moved);
    }

    private static String cooldownId(String uuid, String kitKey) {
        return uuid + ":" + kitKey;
    }
}
