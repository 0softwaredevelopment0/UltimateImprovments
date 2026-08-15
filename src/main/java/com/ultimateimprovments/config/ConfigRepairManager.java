package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * 🔧 ConfigRepairManager — smart config repair.
 * <p>
 * Instead of fully replacing the file (as was done with compromised-*) it finds
 * the missing keys in the current config relative to the JAR reference and ADDS
 * them to the end of the file.
 * <p>
 * All existing values are KEPT — no need to reconfigure the config from scratch.
 */
public class ConfigRepairManager {

    private ConfigRepairManager() {}

    /**
     * Checks and repairs the config: if there are missing keys — adds them to the end of the file.
     *
     * @param plugin         plugin instance
     * @param resourcePath   path to the JAR reference (e.g. "config.yml")
     * @param config         the currently loaded config
     * @param dataFile       the config file on disk
     * @return true if new keys were added, false if everything is fine
     */
    public static boolean repair(Main plugin, String resourcePath, FileConfiguration config, File dataFile) {
        FileConfiguration defaultConfig = loadDefaultResource(plugin, resourcePath);
        if (defaultConfig == null) return false;

        // Collect all paths from the reference
        Set<String> requiredPaths = collectAllPaths(defaultConfig);

        // Find the missing ones
        List<String> missing = findMissingPaths(config, requiredPaths);

        if (missing.isEmpty()) {
            return false; // all good
        }

        ConsoleLogger.warn("[ConfigRepair] Missing " + missing.size() + " key(s) in " + dataFile.getName());
        for (String path : missing) {
            ConsoleLogger.warn("[ConfigRepair]   + " + path);
        }

        // Add the missing keys to the end of the file
        appendMissingKeys(plugin, config, dataFile, defaultConfig, missing);
        ConsoleLogger.info("[ConfigRepair] ✔ Added " + missing.size() + " missing key(s) to " + dataFile.getName());
        return true;
    }

    /**
     * Adds the missing keys to the end of the YAML file, preserving the structure.
     */
    /**
     * Adds the missing keys to the config.
     * <p>
     * Two modes:
     * <ol>
     *   <li><b>New root sections</b> (root does not exist in userConfig) — appended as a YAML block
     *       at the end of the file. This is safe because there will be no duplicates.</li>
     *   <li><b>Sub-keys in existing sections</b> (root already present) — set via
     *       {@code config.set()}, after which the whole file is rewritten with {@code config.save()}.
     *       This is required so that SnakeYAML does not overwrite existing values with a duplicate root section.</li>
     * </ol>
     */
    private static void appendMissingKeys(Main plugin, FileConfiguration userConfig, File dataFile, FileConfiguration defaultConfig, List<String> missing) {
        // Split the missing keys into two groups:
        //   A — root section already exists → config.set() + save
        //   B — new root section → YAML append (no duplicate risk)
        List<String> appendPaths = new ArrayList<>();
        boolean needsFullSave = false;

        for (String path : missing) {
            String rootKey = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;

            if (userConfig.isSet(rootKey)) {
                // Root section already exists — set via set(),
                // to avoid a duplicate root key in YAML
                userConfig.set(path, defaultConfig.get(path));
                needsFullSave = true;
            } else {
                appendPaths.add(path);
            }
        }

        // ── Group A: sub-keys in existing sections → config.set() + save ──
        // Done FIRST so that save() does not overwrite the YAML-append that comes after.
        if (needsFullSave) {
            try {
                userConfig.save(dataFile);
                ConsoleLogger.info("[ConfigRepair] Saved config with merged missing sub-keys.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[ConfigRepair] Failed to save config after merging missing keys", e);
            }
        }

        // ── Group B: new root sections → YAML append at the END of the already-saved file ──
        if (!appendPaths.isEmpty()) {
            appendPaths.sort(Comparator.comparingInt(String::length));

            StringBuilder appendix = new StringBuilder();
            appendix.append("\n");
            appendix.append("# === Missing keys added by UltimateImprovments (auto-repair) ===\n");

            // Group by root section
            Map<String, Set<String>> sectionMap = new LinkedHashMap<>();
            for (String path : appendPaths) {
                String rootKey = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
                String relativePath = path.contains(".") ? path.substring(path.indexOf('.') + 1) : "";
                sectionMap.computeIfAbsent(rootKey, k -> new LinkedHashSet<>()).add(relativePath);
            }

            for (Map.Entry<String, Set<String>> entry : sectionMap.entrySet()) {
                String rootKey = entry.getKey();
                Set<String> subPaths = entry.getValue();

                if (defaultConfig.isConfigurationSection(rootKey)) {
                    ConfigurationSection section = defaultConfig.getConfigurationSection(rootKey);
                    appendix.append(rootKey).append(":\n");
                    for (String subPath : subPaths) {
                        if (subPath.isEmpty()) {
                            Object val = defaultConfig.get(rootKey);
                            appendix.append("  ").append(formatYamlValue(rootKey, val)).append("\n");
                        } else {
                            appendYamlPath(appendix, section, subPath, 1);
                        }
                    }
                } else {
                    Object val = defaultConfig.get(rootKey);
                    appendix.append(formatYamlValue(rootKey, val)).append("\n");
                }
            }

            try (FileWriter fw = new FileWriter(dataFile, true)) {
                fw.write(appendix.toString());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[ConfigRepair] Failed to append keys to " + dataFile.getName(), e);
            }
        }
    }

    /**
     * Recursively builds the YAML path for a nested key.
     */
    private static void appendYamlPath(StringBuilder sb, ConfigurationSection section, String path, int depth) {
        String indent = "  ".repeat(depth);
        int dot = path.indexOf('.');
        String key;
        String rest;

        if (dot >= 0) {
            key = path.substring(0, dot);
            rest = path.substring(dot + 1);
        } else {
            key = path;
            rest = null;
        }

        if (rest != null && section.isConfigurationSection(key)) {
            sb.append(indent).append(key).append(":\n");
            appendYamlPath(sb, section.getConfigurationSection(key), rest, depth + 1);
        } else {
            Object val = section.get(path);
            if (val == null) {
                // Try to get the direct key value
                val = section.get(key);
            }
            sb.append(indent).append(formatYamlValue(key, val)).append("\n");
        }
    }

    /**
     * Formats a YAML value: strings in quotes, lists, etc.
     */
    private static String formatYamlValue(String key, Object value) {
        if (value == null) {
            return key + ": null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return key + ": " + value;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return key + ": []";
            }
            StringBuilder sb = new StringBuilder(key).append(":\n");
            for (Object item : list) {
                sb.append("  - ").append(formatScalar(item)).append("\n");
            }
            return sb.toString().trim();
        }
        // String — quote it (escape backslashes so SnakeYAML doesn't stumble on \p, \n, etc.)
        return key + ": \"" + value.toString().replace("\\", "\\\\") + "\"";
    }

    private static String formatScalar(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        String str = value.toString();
        if (str.contains(" ") || str.contains("\\")) return "\"" + str.replace("\\", "\\\\") + "\"";
        return str;
    }

    // =========================
    // HELPER METHODS (from ConfigIntegrityValidator)
    // =========================

    private static FileConfiguration loadDefaultResource(Main plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in));
        } catch (Exception e) {
            ConsoleLogger.warn("[ConfigRepair] Failed to load default " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    private static Set<String> collectAllPaths(ConfigurationSection section) {
        Set<String> paths = new LinkedHashSet<>();
        if (section == null) return paths;
        collectPathsRecursive(section, "", paths);
        return paths;
    }

    private static void collectPathsRecursive(ConfigurationSection section, String prefix, Set<String> paths) {
        for (String key : section.getKeys(false)) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            paths.add(fullPath);
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    collectPathsRecursive(child, fullPath, paths);
                }
            }
        }
    }

    private static List<String> findMissingPaths(FileConfiguration config, Set<String> requiredPaths) {
        List<String> missing = new ArrayList<>();
        for (String path : requiredPaths) {
            if (!config.isSet(path)) missing.add(path);
        }
        return missing;
    }
}
