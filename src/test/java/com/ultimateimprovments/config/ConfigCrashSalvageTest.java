package com.ultimateimprovments.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ConfigCrashSalvage} — «синтаксический краш → игнор секции».
 * <p>
 * Чистая логика без Bukkit-сервера: на временных файлах проверяем, что при
 * сломанном YAML удаляется ТОЛЬКО битая секция (с бэкапом), а остальные
 * настройки пользователя сохраняются.
 */
class ConfigCrashSalvageTest {

    @TempDir
    Path tempDir;

    private static final List<String> NO_LOGS = new ArrayList<>();

    private File writeConfig(String content) throws Exception {
        File file = tempDir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    private File backupDir() {
        return tempDir.resolve("config-broken").toFile();
    }

    private static boolean parses(File file) throws Exception {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration.loadConfiguration(new StringReader(content));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // Основной сценарий: одна сломанная секция
    // ============================================================

    @Test
    @DisplayName("Broken section is removed, healthy sections survive")
    void brokenSectionRemoved() throws Exception {
        File file = writeConfig(
                "healthy:\n" +
                "  enabled: true\n" +
                "  name: \"keep me\"\n" +
                "broken:\n" +
                "  value: \"unclosed string\n" +
                "another:\n" +
                "  count: 7\n");

        assertFalse(parses(file), "precondition: file must be broken");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, backupDir(), NO_LOGS::add);

        assertTrue(result.success, result.message);
        assertEquals(List.of("broken"), result.removedSections);
        assertTrue(parses(file), "after salvage the file must parse");

        YamlConfiguration cfg = load(file);
        assertTrue(cfg.getBoolean("healthy.enabled"), "healthy section value must survive");
        assertEquals("keep me", cfg.getString("healthy.name"));
        assertEquals(7, cfg.getInt("another.count"));
        assertFalse(cfg.isSet("broken"), "broken section must be gone");
    }

    @Test
    @DisplayName("Removed section is backed up with its content")
    void backupCreated() throws Exception {
        File file = writeConfig(
                "ok:\n" +
                "  enabled: true\n" +
                "bad:\n" +
                "  value: [1, 2\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, backupDir(), NO_LOGS::add);
        assertTrue(result.success, result.message);

        File[] backups = backupDir().listFiles((dir, name) -> name.startsWith("bad-") && name.endsWith(".yml"));
        assertNotNull(backups);
        assertEquals(1, backups.length, "one backup file for the broken section");
        String backup = Files.readString(backups[0].toPath(), StandardCharsets.UTF_8);
        assertTrue(backup.contains("bad:"), "backup must contain the broken section");
        assertTrue(backup.contains("[1, 2"), "backup must contain the broken content");
    }

    // ============================================================
    // Несколько сломанных секций
    // ============================================================

    @Test
    @DisplayName("Multiple broken sections are removed one by one")
    void multipleBrokenSections() throws Exception {
        File file = writeConfig(
                "good:\n" +
                "  enabled: true\n" +
                "broken_a:\n" +
                "  value: \"unclosed\n" +
                "broken_b:\n" +
                "  list: [1, 2\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, backupDir(), NO_LOGS::add);

        assertTrue(result.success, result.message);
        assertTrue(result.removedSections.containsAll(List.of("broken_a", "broken_b")),
                "both broken sections must be removed, got: " + result.removedSections);
        assertTrue(parses(file));
        assertTrue(load(file).getBoolean("good.enabled"), "healthy section must survive");
    }

    // ============================================================
    // Здоровый файл — не трогаем
    // ============================================================

    @Test
    @DisplayName("Healthy config is left untouched")
    void healthyFileUntouched() throws Exception {
        String content =
                "a:\n" +
                "  enabled: true\n" +
                "b:\n" +
                "  count: 3\n";
        File file = writeConfig(content);

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, backupDir(), NO_LOGS::add);

        assertTrue(result.success);
        assertTrue(result.removedSections.isEmpty());
        assertEquals(content, Files.readString(file.toPath(), StandardCharsets.UTF_8),
                "healthy file must not be rewritten");
        assertFalse(backupDir().exists(), "no backups for a healthy file");
    }

    // ============================================================
    // Неустранимые случаи — запасной вариант (пересоздание из JAR)
    // ============================================================

    @Test
    @DisplayName("Tab-indented content (no root sections) cannot be salvaged")
    void tabIndentedNotSalvageable() throws Exception {
        // Таб в начале строки — гарантированная ошибка SnakeYAML, корневых секций нет
        File file = writeConfig("\tbroken: 1\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, backupDir(), NO_LOGS::add);

        assertFalse(result.success, "must report failure so caller falls back to recreate");
        assertFalse(parses(file));
    }

    @Test
    @DisplayName("Broken content outside sections cannot be salvaged")
    void brokenOutsideSectionsNotSalvageable() throws Exception {
        // Таб-мусор до первого корневого ключа — вне всех секций, удаление секций не поможет
        File file = writeConfig(
                "\tbroken: 1\n" +
                "ok:\n" +
                "  enabled: true\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, backupDir(), NO_LOGS::add);

        assertFalse(result.success);
    }

    // ============================================================
    // Вспомогательные
    // ============================================================

    private static YamlConfiguration load(File file) throws Exception {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return YamlConfiguration.loadConfiguration(new StringReader(content));
    }
}
