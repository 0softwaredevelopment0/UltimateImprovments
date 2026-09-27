package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the live TOML serialization surface ({@link TomlConfigManager#sectionToMap}):
 * Bukkit view → nested map → toml4j write → parse back, preserving types and
 * nested structure. This is the exact path {@link AddonConfigManager#writeAddonToml}
 * uses to persist {@code configs/UI-<Addon>.toml}.
 * <p>
 * The monolithic config.yml → config.toml migration tests were removed together
 * with the monolithic backend (per-addon TOML templates are the only source).
 */
class TomlConfigBackendTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Bukkit view serializes to TOML preserving leaf values and types")
    void bukkicViewRoundTrips() throws Exception {
        YamlConfiguration view = new YamlConfiguration();
        view.set("prefix", "<white>[<green>UI<white>] <reset>");
        view.set("features.integrity.enabled", true);
        view.set("features.integrity.interval_ticks", 10);
        view.set("features.integrity.cost_multiplier", 1.5);
        view.set("features.integrity.blacklist", List.of("STICK", "BOWL"));
        view.set("messages.auth.gui.register", "Регистрация");

        File toml = tempDir.resolve("addon.toml").toFile();
        Map<String, Object> root = TomlConfigManager.sectionToMap(view);
        new TomlWriter().write(root, toml);
        assertTrue(toml.exists());

        Toml parsed = new Toml().read(toml);
        assertEquals("<white>[<green>UI<white>] <reset>", parsed.getString("prefix"));
        assertEquals(Boolean.TRUE, parsed.getBoolean("features.integrity.enabled"));
        assertEquals(10L, parsed.getLong("features.integrity.interval_ticks"));
        assertEquals(1.5, parsed.getDouble("features.integrity.cost_multiplier"), 1e-9);
        assertEquals(List.of("STICK", "BOWL"), parsed.getList("features.integrity.blacklist"));
        assertEquals("Регистрация", parsed.getString("messages.auth.gui.register"));
    }

    @Test
    @DisplayName("Nested sections round-trip without flattening (tables in tables)")
    void nestedSectionsRoundTrip() throws Exception {
        YamlConfiguration view = new YamlConfiguration();
        view.set("energy.generator.enabled", true);
        view.set("energy.generator.energy_per_fuel", 100);
        view.set("energy.cable.max_energy", 5000);

        File toml = tempDir.resolve("nested.toml").toFile();
        new TomlWriter().write(TomlConfigManager.sectionToMap(view), toml);

        // The on-disk structure mirrors the YAML sections 1:1 (tables, not dotted keys)
        String raw = Files.readString(toml.toPath(), StandardCharsets.UTF_8);
        assertTrue(raw.contains("[energy.generator]"), "nested section must be a TOML table");
        assertTrue(raw.contains("[energy.cable]"), "nested section must be a TOML table");
        assertFalse(raw.contains("\"energy.generator.enabled\""), "no dotted keys allowed");
    }

    @Test
    @DisplayName("Empty view serializes to an empty TOML file")
    void emptyViewSerializes() throws Exception {
        YamlConfiguration view = new YamlConfiguration();
        File toml = tempDir.resolve("empty.toml").toFile();
        new TomlWriter().write(TomlConfigManager.sectionToMap(view), toml);
        assertTrue(toml.exists());
        Toml parsed = new Toml().read(toml);
        assertTrue(parsed.isEmpty());
    }

    @Test
    @DisplayName("Per-addon TOML templates parse into Bukkit views with routed roots present")
    void bundledTemplatesParse() {
        for (String addon : AddonCatalog.catalog()) {
            InputStream in = TomlConfigBackendTest.class.getResourceAsStream("/config/" + addon + ".toml");
            assertNotNull(in, "config/" + addon + ".toml resource must exist");
            YamlConfiguration view = new YamlConfiguration();
            try {
                Toml parsed = new Toml().read(new InputStreamReader(in, StandardCharsets.UTF_8));
                Map<String, Object> map = parsed.toMap();
                assertNotNull(map);
                flatten(map, "", view);
            } catch (Exception e) {
                fail("Failed to parse config/" + addon + ".toml: " + e.getMessage());
            }
            assertFalse(view.getKeys(true).isEmpty(), addon + ".toml must not be empty");
        }
    }

    private static void flatten(Map<String, Object> map, String prefix, YamlConfiguration target) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flatten(nestedMap, path, target);
            } else {
                target.set(path, e.getValue());
            }
        }
    }
}
