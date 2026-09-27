package com.ultimateimprovments.enchantment.lavawalker;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * MeltStore — persistence for the Lava Walker melt registry.
 * <p>
 * The melt registry ({@code EnchantmentListener.MELTING}) lives in memory; without
 * this store every created obsidian plate simply STAYED after a restart (melt
 * timers were gone). The store persists each melting plate:
 * <ul>
 *   <li>position — key {@code world;x;y;z},</li>
 *   <li>{@code lava} — the captured EXACT original lava BlockData string
 *       (source vs flow level),</li>
 *   <li>{@code due-ms} — the melt deadline as WALL-CLOCK epoch millis. Server
 *       ticks reset on every restart, wall-clock time does not, so timers
 *       survive restarts and even downtime: a plate saved 10 minutes before a
 *       crash melts right after boot.</li>
 * </ul>
 * File: {@code <plugin dataFolder>/lava_walker_melts.yml}, written atomically
 * (temp file + move). Triggers: load at module start (worlds are already
 * loaded by then), async autosave every {@link #AUTOSAVE_INTERVAL_TICKS},
 * synchronous save on module disable (async tasks do not survive shutdown).
 * A hard crash loses at most one autosave interval of timers — affected plates
 * stay obsidian, which is the old harmless behavior.
 */
final class MeltStore {

    /** Persisted file name inside the plugin's data folder. */
    private static final String FILE_NAME = "lava_walker_melts.yml";

    /** Autosave interval: 5 minutes. */
    private static final long AUTOSAVE_INTERVAL_TICKS = 6000L;

    /** Key separator inside the melts section: {@code world;x;y;z}. */
    private static final String KEY_SEP = ";";

    /** Minimum due delay after load (ticks): overdue plates melt on the first sweep, not instantly en masse. */
    private static final long MIN_DUE_AFTER_LOAD_TICKS = 20L;

    /** Snapshot record: due time in wall-clock millis + the original lava data string. */
    private record PendingMelt(long dueMs, String lavaData) {}

    private MeltStore() {}

    // ─────────────────────────────────────────────────────────────
    //  LOAD
    // ─────────────────────────────────────────────────────────────

    /**
     * Loads persisted plates into the melt registry. Called once at module
     * start, before the melt sweep task starts. Entries whose world is not
     * loaded are kept — the sweep postpones them (see
     * {@code EnchantmentListener#meltTick}).
     */
    static void load(Main plugin) {
        File file = file(plugin);
        if (!file.isFile()) return;

        try {
            YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = conf.getConfigurationSection("melts");
            if (root == null) return;

            long nowMs = System.currentTimeMillis();
            long nowTick = EnchantmentListener.now();
            int loaded = 0;
            int skipped = 0;

            for (String key : root.getKeys(false)) {
                ConfigurationSection entry = root.getConfigurationSection(key);
                if (entry == null) { skipped++; continue; }

                String lavaStr = entry.getString("lava");
                long dueMs = entry.getLong("due-ms", 0L);
                String[] parts = key.split(KEY_SEP);
                if (lavaStr == null || parts.length != 4) { skipped++; continue; }

                final BlockData lava;
                try {
                    lava = Bukkit.createBlockData(lavaStr);
                } catch (IllegalArgumentException e) {
                    skipped++; // unknown/unparsable block data — drop it
                    continue;
                }

                final int x, y, z;
                try {
                    x = Integer.parseInt(parts[1]);
                    y = Integer.parseInt(parts[2]);
                    z = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    skipped++;
                    continue;
                }

                // Wall-clock → ticks. Overdue entries get a tiny delay so the
                // first sweep cleans them up instead of an instant mass-melt.
                long dueTick = nowTick + Math.max(MIN_DUE_AFTER_LOAD_TICKS, (dueMs - nowMs) / 50L);

                EnchantmentListener.MELTING.put(
                        new EnchantmentListener.BlockPos(parts[0], x, y, z),
                        new EnchantmentListener.MeltEntry(dueTick, lava));
                loaded++;
            }

            if (loaded > 0 || skipped > 0) {
                ConsoleLogger.info("[LavaWalker] Melt store: restored " + loaded + " melting plate(s)"
                        + (skipped > 0 ? ", skipped " + skipped + " broken entry(ies)" : "") + ".");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[LavaWalker] Failed to read melt store " + file.getName() + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  SAVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the periodic autosave: a snapshot with wall-clock deadlines is
     * captured on the main thread, the file write runs asynchronously.
     */
    static void startAutosave(Main plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<EnchantmentListener.BlockPos, PendingMelt> snap = snapshot();
            if (snap.isEmpty() && !file(plugin).exists()) return; // nothing to persist yet
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(plugin, snap));
        }, AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);
    }

    /** Synchronous save for the shutdown path (async tasks do not survive plugin disable). */
    static void saveNow(Main plugin) {
        write(plugin, snapshot());
    }

    /** Thread-safe snapshot of the registry, due ticks converted to wall-clock millis. */
    private static Map<EnchantmentListener.BlockPos, PendingMelt> snapshot() {
        Map<EnchantmentListener.BlockPos, PendingMelt> copy = new HashMap<>();
        long nowMs = System.currentTimeMillis();
        long nowTick = EnchantmentListener.now();
        for (Map.Entry<EnchantmentListener.BlockPos, EnchantmentListener.MeltEntry> e
                : EnchantmentListener.MELTING.entrySet()) {
            copy.put(e.getKey(), new PendingMelt(
                    nowMs + (e.getValue().dueTick() - nowTick) * 50L,
                    e.getValue().lavaData().getAsString()));
        }
        return copy;
    }

    /** Builds the YAML and writes it atomically (temp + move). Safe off the main thread. */
    private static void write(Main plugin, Map<EnchantmentListener.BlockPos, PendingMelt> snapshot) {
        File file = file(plugin);

        if (snapshot.isEmpty()) {
            if (file.isFile() && !file.delete()) {
                ConsoleLogger.warn("[LavaWalker] Could not delete empty melt store " + file.getName());
            }
            return;
        }

        YamlConfiguration conf = new YamlConfiguration();
        ConfigurationSection root = conf.createSection("melts");
        for (Map.Entry<EnchantmentListener.BlockPos, PendingMelt> e : snapshot.entrySet()) {
            EnchantmentListener.BlockPos pos = e.getKey();
            ConfigurationSection entry = root.createSection(
                    pos.world() + KEY_SEP + pos.x() + KEY_SEP + pos.y() + KEY_SEP + pos.z());
            entry.set("due-ms", e.getValue().dueMs());
            entry.set("lava", e.getValue().lavaData());
        }

        File temp = new File(file.getParentFile(), FILE_NAME + ".tmp");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            conf.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ConsoleLogger.warn("[LavaWalker] Failed to write melt store: " + e.getMessage());
        }
    }

    private static File file(Main plugin) {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }
}
