package com.ultimateimprovments.enchantment.lavawalker;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MeltStore — SQLite persistence for the Lava Walker melt registry.
 * <p>
 * The melt registry ({@code EnchantmentListener.MELTING}) lives in memory; without
 * this store every created obsidian plate simply STAYED after a restart (melt
 * timers were gone). The store persists each melting plate in the SHARED plugin
 * SQLite database (see {@code DatabaseManager}, one {@code database.db} for the
 * whole UI-* family — the same store every other subsystem uses), table
 * {@code lava_walker_melts}:
 * <ul>
 *   <li>{@code world_name, x, y, z} — the plate position (primary key),</li>
 *   <li>{@code lava_data} — the captured EXACT original lava BlockData string
 *       (source vs flow level),</li>
 *   <li>{@code due_ms} — the melt deadline as WALL-CLOCK epoch millis. Server
 *       ticks reset on every restart, wall-clock time does not, so timers
 *       survive restarts and even downtime: a plate saved 10 minutes before a
 *       crash melts right after boot.</li>
 * </ul>
 * Triggers: load at module start (plus a one-time migration from the legacy
 * {@code lava_walker_melts.yml}), async autosave every
 * {@link #AUTOSAVE_INTERVAL_TICKS}, synchronous save on module disable (async
 * tasks do not survive shutdown). All DB access mirrors the
 * {@code ParticleEnergyDatabase} pattern (synchronized, lazy
 * {@code initTables}, {@code ready} flag). A hard crash loses at most one
 * autosave interval of timers — affected plates stay obsidian, which is the
 * old harmless behavior.
 */
final class MeltStore {

    /** Autosave interval: 5 minutes. */
    private static final long AUTOSAVE_INTERVAL_TICKS = 6000L;

    /** Minimum due delay after load (ticks): overdue plates melt on the first sweep, not instantly en masse. */
    private static final long MIN_DUE_AFTER_LOAD_TICKS = 20L;

    /** Legacy YAML store name (one version used it) — migrated into SQLite and deleted. */
    private static final String LEGACY_YAML = "lava_walker_melts.yml";

    /** One row read from the table. */
    private record Row(String world, int x, int y, int z, String lavaData, long dueMs) {}

    private static volatile boolean ready = false;

    private MeltStore() {}

    // ─────────────────────────────────────────────────────────────
    //  TABLES
    // ─────────────────────────────────────────────────────────────

    private static synchronized void initTables() {
        if (ready) return;
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS lava_walker_melts (
                    world_name TEXT    NOT NULL,
                    x          INTEGER NOT NULL,
                    y          INTEGER NOT NULL,
                    z          INTEGER NOT NULL,
                    lava_data  TEXT    NOT NULL,
                    due_ms     INTEGER NOT NULL,
                    PRIMARY KEY (world_name, x, y, z)
                );
                """);
            ready = true;
        } catch (Exception e) {
            ConsoleLogger.error("[LavaWalker] Melt store init failed: " + e.getMessage());
            ready = false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  LOAD
    // ─────────────────────────────────────────────────────────────

    /**
     * Loads persisted plates into the melt registry. Called once at module
     * start, before the melt sweep task starts. Migrates the legacy YAML
     * store if present. Entries whose world is not loaded are kept — the
     * sweep retries them until the world appears.
     */
    static void load() {
        initTables();
        if (!ready) return;

        migrateLegacyYaml();

        List<Row> rows = readAll();
        if (rows.isEmpty()) return;

        long nowMs = System.currentTimeMillis();
        long nowTick = EnchantmentListener.now();
        int skipped = 0;

        for (Row row : rows) {
            final BlockData lava;
            try {
                lava = Bukkit.createBlockData(row.lavaData());
            } catch (IllegalArgumentException e) {
                skipped++; // unknown/unparsable block data — drop it
                continue;
            }

            // Wall-clock → ticks. Overdue entries get a tiny delay so the
            // first sweep cleans them up instead of an instant mass-melt.
            long dueTick = nowTick + Math.max(MIN_DUE_AFTER_LOAD_TICKS, (row.dueMs() - nowMs) / 50L);

            EnchantmentListener.MELTING.put(
                    new EnchantmentListener.BlockPos(row.world(), row.x(), row.y(), row.z()),
                    new EnchantmentListener.MeltEntry(dueTick, lava));
        }

        ConsoleLogger.info("[LavaWalker] Melt store: restored " + (rows.size() - skipped)
                + " melting plate(s)" + (skipped > 0 ? ", skipped " + skipped + " broken" : "") + ".");
    }

    /** Reads every row from the table. */
    private static List<Row> readAll() {
        List<Row> rows = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT world_name, x, y, z, lava_data, due_ms FROM lava_walker_melts")) {
            while (rs.next()) {
                rows.add(new Row(
                        rs.getString("world_name"),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                        rs.getString("lava_data"),
                        rs.getLong("due_ms")));
            }
        } catch (Exception e) {
            ConsoleLogger.error("[LavaWalker] Melt store load failed: " + e.getMessage());
        }
        return rows;
    }

    /**
     * One-time migration: the short-lived YAML store (previous version) is
     * imported into SQLite and deleted, so no timers are lost on upgrade.
     */
    private static void migrateLegacyYaml() {
        try {
            File file = new File(com.ultimateimprovments.core.Main.getInstance().getDataFolder(), LEGACY_YAML);
            if (!file.isFile()) return;

            YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = conf.getConfigurationSection("melts");
            if (root == null) {
                file.delete();
                return;
            }

            Map<EnchantmentListener.BlockPos, String[]> imported = new HashMap<>();
            for (String key : root.getKeys(false)) {
                ConfigurationSection entry = root.getConfigurationSection(key);
                if (entry == null) continue;
                String lavaStr = entry.getString("lava");
                long dueMs = entry.getLong("due-ms", 0L);
                String[] parts = key.split(";");
                if (lavaStr == null || parts.length != 4) continue;
                try {
                    imported.put(new EnchantmentListener.BlockPos(parts[0],
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])),
                            new String[]{lavaStr, Long.toString(dueMs)});
                } catch (NumberFormatException ignored) {
                    // skip malformed key
                }
            }

            if (!imported.isEmpty()) {
                try (Connection con = DatabaseManager.getConnection();
                     PreparedStatement ps = con.prepareStatement(
                             "INSERT OR REPLACE INTO lava_walker_melts (world_name, x, y, z, lava_data, due_ms) "
                                     + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (Map.Entry<EnchantmentListener.BlockPos, String[]> e : imported.entrySet()) {
                        EnchantmentListener.BlockPos pos = e.getKey();
                        ps.setString(1, pos.world());
                        ps.setInt(2, pos.x());
                        ps.setInt(3, pos.y());
                        ps.setInt(4, pos.z());
                        ps.setString(5, e.getValue()[0]);
                        ps.setLong(6, Long.parseLong(e.getValue()[1]));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            if (file.delete()) {
                ConsoleLogger.info("[LavaWalker] Migrated legacy melt store " + LEGACY_YAML
                        + " (" + imported.size() + " plate(s)) into SQLite.");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[LavaWalker] Legacy melt store migration failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  SAVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the periodic autosave: a snapshot with wall-clock deadlines is
     * captured on the main thread, the DB write runs asynchronously.
     */
    static void startAutosave(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<EnchantmentListener.BlockPos, EnchantmentListener.MeltEntry> snap = snapshot();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveAll(snap));
        }, AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);
    }

    /** Synchronous save for the shutdown path (async tasks do not survive plugin disable). */
    static void saveNow() {
        saveAll(snapshot());
    }

    /** Thread-safe snapshot of the registry, due ticks converted to wall-clock millis. */
    private static Map<EnchantmentListener.BlockPos, EnchantmentListener.MeltEntry> snapshot() {
        return new HashMap<>(EnchantmentListener.MELTING);
    }

    /**
     * Full table overwrite from the snapshot. The snapshot entries keep their
     * tick deadlines internally; they are converted to wall-clock millis here
     * for storage.
     */
    private static synchronized void saveAll(Map<EnchantmentListener.BlockPos, EnchantmentListener.MeltEntry> snapshot) {
        if (!ready) initTables();
        if (!ready) return;

        long nowMs = System.currentTimeMillis();
        long nowTick = EnchantmentListener.now();

        try (Connection con = DatabaseManager.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.execute("DELETE FROM lava_walker_melts");
            }
            if (snapshot.isEmpty()) return;

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO lava_walker_melts (world_name, x, y, z, lava_data, due_ms) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Map.Entry<EnchantmentListener.BlockPos, EnchantmentListener.MeltEntry> e : snapshot.entrySet()) {
                    EnchantmentListener.BlockPos pos = e.getKey();
                    ps.setString(1, pos.world());
                    ps.setInt(2, pos.x());
                    ps.setInt(3, pos.y());
                    ps.setInt(4, pos.z());
                    ps.setString(5, e.getValue().lavaData().getAsString());
                    ps.setLong(6, nowMs + (e.getValue().dueTick() - nowTick) * 50L);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception e) {
            ConsoleLogger.error("[LavaWalker] Melt store save failed: " + e.getMessage());
        }
    }
}
