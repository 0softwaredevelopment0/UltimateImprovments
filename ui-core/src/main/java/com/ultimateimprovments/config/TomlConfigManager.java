package com.ultimateimprovments.config;

import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ⚙ TomlConfigManager — TOML conversion utilities for the per-addon config backend.
 * <p>
 * <b>History:</b> this class once owned the whole config lifecycle for the
 * monolithic {@code config.toml} (bootstrap from a bundled {@code config.yml},
 * crash salvage, repair). Since the per-addon split every addon owns its own
 * {@code configs/UI-<Addon>.toml} (see {@link AddonConfigManager}), the
 * monolithic backend is dead and was removed together with the bundled
 * {@code config.yml} resource. What remains is the shared serialization
 * surface used by {@link AddonConfigManager}:
 * <ul>
 *   <li>{@link #sectionToMap(FileConfiguration)} — Bukkit view → plain nested
 *       map (toml4j-compatible, ordered);</li>
 *   <li>the TOML → Bukkit flattening conventions (dotted paths, nested tables).</li>
 * </ul>
 * Serialization note: Bukkit nested maps are serialized as
 * {@code [section] key = value} tables (never dotted keys), so the on-disk
 * structure mirrors the YAML sections 1:1.
 */
public final class TomlConfigManager {

    private TomlConfigManager() {}

    // ========================================================================
    // CONVERSION HELPERS
    // ========================================================================

    /** Bukkit view → plain nested map (toml4j-compatible, ordered). */
    public static Map<String, Object> sectionToMap(FileConfiguration config) {
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
}
