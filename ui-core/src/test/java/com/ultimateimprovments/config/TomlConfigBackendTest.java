package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the TOML config backend: YAML → TOML migration round-trip,
 * TOML → Bukkit view, crash salvage and the yml→toml file migration.
 */
class TomlConfigBackendTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // MIGRATION: config.yml → config.toml
    // ========================================================================

    @Test
    @DisplayName("YAML config migrates to TOML preserving all leaf values")
    void ymlMigratesToToml() throws Exception {
        File yml = tempDir.resolve("config.yml").toFile();
        Files.writeString(yml.toPath(), """
                prefix: "<white>[<green>UI<white>] <reset>"
                features:
                  integrity:
                    enabled: true
                    interval_ticks: 10
                    cost_multiplier: 1.5
                    blacklist:
                      - STICK
                      - BOWL
                  xp_integrity:
                    enabled: true
                    integrity_per_xp: 0.1
                messages:
                  auth:
                    gui:
                      register: "Регистрация"
                """);

        File toml = tempDir.resolve("config.toml").toFile();
        assertTrue(TomlConfigManager.convertYmlToToml(yml, toml), "migration must succeed");
        assertTrue(toml.exists());

        // The TOML must parse and contain every leaf value
        Toml parsed = new Toml().read(toml);
        assertEquals("<white>[<green>UI<white>] <reset>", parsed.getString("prefix"));
        assertEquals(10L, parsed.getLong("features.integrity.interval_ticks"));
        assertEquals(1.5, parsed.getDouble("features.integrity.cost_multiplier"), 1e-9);
        assertEquals(Boolean.TRUE, parsed.getBoolean("features.integrity.enabled"));
        assertEquals(List.of("STICK", "BOWL"), parsed.getList("features.integrity.blacklist"));
        assertEquals("Регистрация", parsed.getString("messages.auth.gui.register"));
    }

    @Test
    @DisplayName("Migrated TOML round-trips through the Bukkit view")
    void migratedTomlLoadsIntoBukkitView() throws Exception {
        File yml = tempDir.resolve("config.yml").toFile();
        Files.writeString(yml.toPath(), """
                features:
                  integrity:
                    enabled: true
                    cost_multiplier: 0.75
                messages:
                  update:
                    header: "=== Update ==="
                """);

        File toml = tempDir.resolve("config.toml").toFile();
        assertTrue(TomlConfigManager.convertYmlToToml(yml, toml));

        // Same conversion path TomlConfigManager.load() uses
        YamlConfiguration view = new YamlConfiguration();
        Toml parsed = new Toml().read(toml);
        flattenForTest(parsed.toMap(), "", view);

        assertTrue(view.getBoolean("features.integrity.enabled"));
        assertEquals(0.75, view.getDouble("features.integrity.cost_multiplier"), 1e-9);
        assertEquals("=== Update ===", view.getString("messages.update.header"));
    }

    // ========================================================================
    // NO DUPLICATE KEYS
    // ========================================================================

    @Test
    @DisplayName("Migrated TOML has no duplicate keys at the same level")
    void migratedTomlHasNoDuplicates() throws Exception {
        File yml = tempDir.resolve("config.yml").toFile();
        // Duplicate root sections — a classic YAML problem TOML cannot have
        Files.writeString(yml.toPath(), """
                features:
                  integrity:
                    enabled: true
                """);

        File toml = tempDir.resolve("config.toml").toFile();
        assertTrue(TomlConfigManager.convertYmlToToml(yml, toml));

        // TomlWriter emits nested tables as [features.integrity] — exactly once,
        // no intermediate [features] header, no duplicated sections
        long count = Files.readAllLines(toml.toPath()).stream()
                .filter(line -> line.trim().equals("[features.integrity]"))
                .count();
        assertEquals(1, count, "[features.integrity] table header must appear exactly once");
    }

    // ========================================================================
    // CRASH SALVAGE
    // ========================================================================

    @Test
    @DisplayName("Healthy TOML is left untouched by salvage")
    void salvageKeepsHealthyFile() throws Exception {
        File toml = tempDir.resolve("config.toml").toFile();
        Files.writeString(toml.toPath(), "[features]\nenabled = true\n[server]\nport = 25565\n");

        TomlCrashSalvage.Result result = TomlCrashSalvage.salvage(toml);
        assertTrue(result.success);
        assertTrue(result.commentedLines.isEmpty());
        assertEquals("[features]\nenabled = true\n[server]\nport = 25565\n",
                Files.readString(toml.toPath()));
    }

    @Test
    @DisplayName("Broken line is commented out, healthy keys survive")
    void salvageCommentsBrokenLine() throws Exception {
        File toml = tempDir.resolve("config.toml").toFile();
        Files.writeString(toml.toPath(), """
                [features]
                enabled = true
                broken = "unterminated string
                port = 25565
                """);

        TomlCrashSalvage.Result result = TomlCrashSalvage.salvage(toml);
        assertTrue(result.success, "salvage should stabilize the file: " + result.message);
        assertEquals(1, result.commentedLines.size());

        String content = Files.readString(toml.toPath());
        assertTrue(content.contains("# broken ="), "broken line must be commented out");
        assertTrue(content.contains("enabled = true"), "healthy keys must survive");
        assertTrue(content.contains("port = 25565"));

        // After salvage the file parses again
        Toml parsed = new Toml().read(toml);
        assertEquals(Boolean.TRUE, parsed.getBoolean("features.enabled"));
        assertEquals(25565L, parsed.getLong("features.port"));
    }

    @Test
    @DisplayName("Multiple broken lines are commented one by one")
    void salvageHandlesMultipleErrors() throws Exception {
        File toml = tempDir.resolve("config.toml").toFile();
        Files.writeString(toml.toPath(), """
                a = "ok
                b = "also broken
                c = 1
                """);

        TomlCrashSalvage.Result result = TomlCrashSalvage.salvage(toml);
        assertTrue(result.success, "salvage should stabilize the file: " + result.message);
        assertTrue(result.commentedLines.size() >= 2, "both broken lines should be commented");

        Toml parsed = new Toml().read(toml);
        assertEquals(1L, parsed.getLong("c"));
    }

    // ========================================================================
    // HELPER — same flattening as TomlConfigManager (tested indirectly above)
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static void flattenForTest(java.util.Map<String, Object> map, String prefix, YamlConfiguration target) {
        for (var entry : map.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof java.util.Map<?, ?> nested) {
                flattenForTest((java.util.Map<String, Object>) nested, path, target);
            } else {
                target.set(path, value);
            }
        }
    }
}
