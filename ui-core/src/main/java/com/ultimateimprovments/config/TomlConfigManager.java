package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ⚙ TomlConfigManager — TOML backend for the single plugin config.
 * <p>
 * Owns the full config lifecycle and presents the data as a Bukkit
 * {@link YamlConfiguration} (in-memory only) so the whole existing
 * {@code getConfig()} call-site surface keeps working unchanged:
 * <ol>
 *   <li>First run: if {@code config.toml} is absent, the bundled {@code config.yml}
 *       is extracted from the JAR and converted to {@code config.toml}
 *       (comments are not preserved — TOML has no comment model in toml4j).</li>
 *   <li>No legacy migration: the config was already migrated to TOML by the
 *       previous plugin version — a stray legacy {@code config.yml} on disk is
 *       ignored.</li>
 *   <li>Crash salvage on every load: broken TOML lines are commented out
 *       (see {@link TomlCrashSalvage}) — same strategy as the old YAML salvage.</li>
 *   <li>Missing keys are filled from the bundled {@code config.yml} reference
 *       (via {@link ConfigRepairManager} on the loaded Bukkit view).</li>
 * </ol>
 * Serialization note: Bukkit nested maps are serialized as
 * {@code [section] key = value} tables (never dotted keys), so the on-disk
 * structure mirrors the YAML sections 1:1.
 */
public final class TomlConfigManager {

    /** Primary config file. */
    public static final String CONFIG_TOML = "config.toml";
    /** Bundled reference (source of defaults + bootstrap source). */
    public static final String CONFIG_YML = "config.yml";

    private TomlConfigManager() {}

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Loads the effective config as a Bukkit {@link YamlConfiguration}:
     * handles first-run bootstrap, legacy migration, salvage and repair.
     */
    public static YamlConfiguration load(Main plugin) {
        File tomlFile = new File(plugin.getDataFolder(), CONFIG_TOML);
        File ymlFile = new File(plugin.getDataFolder(), CONFIG_YML);

        // ── 1. Fresh install: no config at all → bootstrap TOML from the bundled yml ──
        if (!tomlFile.exists() && !ymlFile.exists()) {
            extractBundledYml(plugin, ymlFile);
            if (convertYmlToToml(ymlFile, tomlFile)) {
                ConsoleLogger.info("[Config] Bootstrap config.yml converted to config.toml");
            }
            // The bootstrap yml has served its purpose — remove it.
            try {
                java.nio.file.Files.deleteIfExists(ymlFile.toPath());
            } catch (Exception e) {
                ConsoleLogger.warn("[Config] Could not delete bootstrap config.yml: " + e.getMessage());
            }
        }

        // ── 2. No legacy migration: the config was already migrated to TOML by the
        //    previous plugin version — a stray legacy config.yml on disk is ignored. ──

        // ── 3. Salvage: comment out broken TOML lines until the file parses ──
        TomlCrashSalvage.salvageFile(tomlFile, msg -> ConsoleLogger.warn("[ConfigSalvage] " + msg));

        // ── 4. Parse ──
        YamlConfiguration asBukkit = new YamlConfiguration();
        try (InputStream in = new FileInputStream(tomlFile)) {
            Toml toml = new Toml().read(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, Object> map = toml.toMap();
            if (map != null) {
                flattenInto(map, "", asBukkit);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] config.toml is unreadable (" + e.getMessage()
                    + ") — defaults from the bundled reference will be used");
            return loadReferenceAsBukkit(plugin);
        }

        // ── 5. Repair: fill missing keys from the bundled reference ──
        try {
            YamlConfiguration reference = loadReferenceAsBukkit(plugin);
            List<String> missing = ConfigRepairManager.findMissing(asBukkit, reference);
            if (!missing.isEmpty()) {
                ConsoleLogger.warn("[ConfigRepair] Missing " + missing.size() + " key(s) in config.toml");
                for (String path : missing) {
                    ConsoleLogger.warn("[ConfigRepair]   + " + path);
                    asBukkit.set(path, reference.get(path));
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[ConfigRepair] Reference repair failed: " + e.getMessage());
        }

        return asBukkit;
    }

    /** Saves the given config view back to config.toml (used by saveConfig()). */
    public static boolean save(Main plugin, org.bukkit.configuration.file.FileConfiguration config) {
        File tomlFile = new File(plugin.getDataFolder(), CONFIG_TOML);
        try {
            Map<String, Object> root = sectionToMap(config);
            new TomlWriter().write(root, tomlFile);
            return true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to save config.toml: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads the bundled config.yml from the JAR as a Bukkit view —
     * the source of defaults for the repair pass (and for bootstrap).
     */
    private static YamlConfiguration loadReferenceAsBukkit(Main plugin) {
        try (InputStream in = plugin.getResource(CONFIG_YML)) {
            if (in == null) {
                ConsoleLogger.warn("[Config] Bundled reference config.yml not found in the JAR");
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to load reference config.yml: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    // ========================================================================
    // BOOTSTRAP CONVERSION
    // =========================================================================

    /** Extracts the bundled config.yml from the JAR (bootstrap for a fresh install). */
    private static void extractBundledYml(Main plugin, File target) {
        try (InputStream in = plugin.getResource(CONFIG_YML)) {
            if (in == null) {
                ConsoleLogger.warn("[Config] Bundled config.yml not found in the JAR!");
                return;
            }
            java.nio.file.Files.copy(in, target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.info("[Config] Bundled config.yml extracted");
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] Failed to extract bundled config.yml: " + e.getMessage());
        }
    }

    /**
     * Pure conversion utility: parses a YAML file and writes the same data as TOML.
     * Used by the fresh-install bootstrap (bundled reference yml → config.toml) and
     * by tests. This is NOT user-data migration — the config was already migrated
     * to TOML by the previous plugin version.
     *
     * @return true on success
     */
    public static boolean convertYmlToToml(File ymlFile, File tomlFile) {
        try {
            YamlConfiguration yml = new YamlConfiguration();
            yml.load(ymlFile);
            Map<String, Object> root = sectionToMap(yml);
            new TomlWriter().write(root, tomlFile);
            return true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Config] YAML → TOML conversion failed: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // CONVERSION HELPERS
    // ========================================================================

    /** Bukkit view → plain nested map (toml4j-compatible, ordered). */
    public static Map<String, Object> sectionToMap(org.bukkit.configuration.file.FileConfiguration config) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (String key : config.getKeys(false)) {
            Object value = config.get(key);
            if (value instanceof MemorySection section) {
                root.put(key, memorySectionToMap(section));
            } else if (value != null) {
                root.put(key, value);
            }
        }
        return root;
    }

    private static Map<String, Object> memorySectionToMap(MemorySection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof MemorySection nested) {
                map.put(key, memorySectionToMap(nested));
            } else if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Recursively flattens a TOML map into the Bukkit view
     * ({@code set("a.b.c", value)}), creating nested MemorySections on demand.
     */
    private static void flattenInto(Map<String, Object> map, String prefix, YamlConfiguration target) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flattenInto(nestedMap, path, target);
            } else {
                target.set(path, toBukkitValue(value));
            }
        }
    }

    /** Converts a toml4j value into a Bukkit-friendly value. */
    private static Object toBukkitValue(Object value) {
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) {
                out.add(toBukkitValue(element));
            }
            return out;
        }
        return value;
    }
}
