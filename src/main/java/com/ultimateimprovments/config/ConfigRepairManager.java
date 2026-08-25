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
     * Adds the missing keys to the config.
     * <p>
     * Two modes:
     * <ol>
     *   <li><b>Sub-keys in existing sections</b> (root already present) — inserted as text
     *       at the end of their root section, preserving the user's comments.</li>
     *   <li><b>New root sections</b> (root does not exist in userConfig) — appended as a YAML block
     *       at the end of the file. This is safe because there will be no duplicates.</li>
     * </ol>
     */
    private static void appendMissingKeys(Main plugin, FileConfiguration userConfig, File dataFile, FileConfiguration defaultConfig, List<String> missing) {
        // Split the missing keys into two groups:
        //   A — root section already exists → textual insert into that section
        //   B — new root section → YAML append (no duplicate risk)
        List<String> appendPaths = new ArrayList<>();
        Map<String, List<String>> existingRoots = new LinkedHashMap<>();

        for (String path : missing) {
            String rootKey = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;

            if (userConfig.isSet(rootKey)) {
                // Root section already exists — insert the missing sub-keys as text
                // into that section (keeps the user's comments intact).
                existingRoots.computeIfAbsent(rootKey, k -> new ArrayList<>()).add(path);
            } else {
                appendPaths.add(path);
            }
        }

        // ── Group A: sub-keys in existing sections → textual insert (comments preserved) ──
        if (!existingRoots.isEmpty()) {
            insertIntoExistingSections(dataFile, defaultConfig, existingRoots);
        }

        // ── Group B: new root sections → YAML append at the END of the file ──
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
     * Inserts missing sub-keys into existing root sections WITHOUT rewriting the
     * whole file, so the user's comments are preserved. Each root's missing keys
     * are inserted at the end of that section (right before the next root key or EOF).
     */
    private static void insertIntoExistingSections(File dataFile, FileConfiguration defaultConfig, Map<String, List<String>> existingRoots) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(dataFile.toPath()));

            // Build the insertion block for each root
            Map<String, String> blocks = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : existingRoots.entrySet()) {
                String rootKey = entry.getKey();
                StringBuilder sb = new StringBuilder();
                ConfigurationSection section = defaultConfig.getConfigurationSection(rootKey);
                for (String path : entry.getValue()) {
                    if (!path.contains(".")) continue;
                    String relativePath = path.substring(path.indexOf('.') + 1);
                    if (section != null) {
                        appendYamlPath(sb, section, relativePath, 1);
                    } else {
                        Object val = defaultConfig.get(path);
                        if (val != null) sb.append("  ").append(formatYamlValue(relativePath, val)).append("\n");
                    }
                }
                blocks.put(rootKey, sb.toString());
            }

            // Locate all root-level key lines
            List<Integer> rootLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (isRootKeyLine(lines.get(i))) rootLines.add(i);
            }

            // Insert bottom-up so line indices stay valid
            for (int r = rootLines.size() - 1; r >= 0; r--) {
                int rootIdx = rootLines.get(r);
                String rootKey = extractKey(lines.get(rootIdx));
                String block = blocks.remove(rootKey);
                if (block == null || block.isEmpty()) continue;

                // Insertion point: end of this section = next root line (or EOF),
                // skipping trailing blank lines so we insert before them.
                int insertAt = (r + 1 < rootLines.size()) ? rootLines.get(r + 1) : lines.size();
                int target = insertAt;
                while (target > rootIdx + 1 && lines.get(target - 1).trim().isEmpty()) target--;

                List<String> toInsert = new ArrayList<>(java.util.Arrays.asList(block.split("\n", -1)));
                lines.addAll(target, toInsert);
            }

            Files.write(dataFile.toPath(), lines);
            ConsoleLogger.info("[ConfigRepair] Inserted missing sub-keys into existing sections (comments preserved).");
        } catch (IOException e) {
            ConsoleLogger.warn("[ConfigRepair] Failed to insert missing sub-keys: " + e.getMessage());
        }
    }

    /** Whether a line is a root-level YAML key (no indent, not a comment, not a list item). */
    private static boolean isRootKeyLine(String line) {
        if (line == null || line.isEmpty()) return false;
        char first = line.charAt(0);
        if (first == ' ' || first == '\t') return false;
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) return false;
        if (trimmed.startsWith("- ")) return false;
        return trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_-]*:.*");
    }

    /** Extracts the key name from a {@code key: value} line. */
    private static String extractKey(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        return colon > 0 ? trimmed.substring(0, colon) : trimmed;
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
            sb.append(indent).append(formatYamlValue(key, val, depth)).append("\n");
        }
    }

    /**
     * Formats a YAML value: strings in quotes, lists, etc.
     */
    private static String formatYamlValue(String key, Object value) {
        return formatYamlValue(key, value, 0);
    }

    private static String formatYamlValue(String key, Object value, int depth) {
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
                String listIndent = "  ".repeat(depth + 1);
                sb.append(listIndent).append("- ").append(formatScalar(item)).append("\n");
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
