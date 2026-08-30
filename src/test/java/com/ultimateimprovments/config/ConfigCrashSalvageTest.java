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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ConfigCrashSalvage} — "syntax crash → comment out only the broken lines".
 * <p>
 * Pure logic without a Bukkit server: on temp files we check that a broken YAML comments out
 * ONLY the problem line(s), the healthy settings survive, the {@code config-broken} folder is
 * never created and the file is never deleted.
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

    @Test
    @DisplayName("Broken line is commented out, healthy settings survive")
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
                ConfigCrashSalvage.salvageFile(file, NO_LOGS::add);

        assertTrue(result.success, result.message);
        assertTrue(parses(file), "after salvage the file must parse");
        assertFalse(result.commentedLines.isEmpty(), "some line must have been commented");

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"keep me\""), "healthy value must survive");
        assertTrue(content.contains("enabled: true"), "healthy settings must survive");
        assertTrue(content.contains("count: 7"), "healthy settings must survive");
    }

    @Test
    @DisplayName("No config-broken folder is created and the file is never deleted")
    void noBackupFolderNoDeletion() throws Exception {
        File file = writeConfig(
                "ok:\n" +
                "  enabled: true\n" +
                "bad:\n" +
                "  value: [1, 2\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, NO_LOGS::add);
        assertTrue(result.success, result.message);

        assertFalse(backupDir().exists(), "config-broken folder must not be created");
        assertTrue(file.exists(), "config.yml must never be deleted");
        assertTrue(parses(file), "file must remain valid");
    }

    @Test
    @DisplayName("Multiple broken lines are commented one by one")
    void multipleBrokenSections() throws Exception {
        File file = writeConfig(
                "good:\n" +
                "  enabled: true\n" +
                "broken_a:\n" +
                "  value: \"unclosed\n" +
                "broken_b:\n" +
                "  list: [1, 2\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, NO_LOGS::add);

        assertTrue(result.success, result.message);
        assertTrue(result.commentedLines.size() >= 2,
                "both broken lines must be commented, got: " + result.commentedLines);
        assertTrue(parses(file));
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("enabled: true"), "healthy section value must survive");
    }

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
                ConfigCrashSalvage.salvageFile(file, NO_LOGS::add);

        assertTrue(result.success);
        assertTrue(result.commentedLines.isEmpty());
        assertFalse(backupDir().exists(), "no backups for a healthy file");
    }

    @Test
    @DisplayName("Tab-indented content is commented out, file preserved")
    void tabIndentedCommentedOut() throws Exception {
        File file = writeConfig(
                "\tbroken: 1\n" +
                "ok:\n" +
                "  enabled: true\n");

        ConfigCrashSalvage.Result result =
                ConfigCrashSalvage.salvageFile(file, NO_LOGS::add);

        assertTrue(result.success, result.message);
        assertTrue(parses(file), "after commenting the tab line the file must parse");
        assertTrue(file.exists(), "file must be preserved");
    }
}
