package com.ultimateimprovments.config;

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
 * Tests for {@link TomlCrashSalvage} — "syntax crash → comment out only the broken lines".
 * <p>
 * Pure logic without a Bukkit server: on temp files we check that a broken TOML comments out
 * ONLY the problem line(s), the healthy settings survive, no backup folder is created and
 * the file is never deleted. (The legacy YAML counterpart {@code ConfigCrashSalvage} was
 * removed together with the monolithic config.yml.)
 */
class TomlCrashSalvageTest {

    @TempDir
    Path tempDir;

    private static final List<String> NO_LOGS = new ArrayList<>();

    private File writeConfig(String content) throws Exception {
        File file = tempDir.resolve("UI-Test.toml").toFile();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    private File backupDir() {
        return tempDir.resolve("config-broken").toFile();
    }

    private static boolean parses(File file) throws Exception {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            new com.moandjiezana.toml.Toml().read(new StringReader(content));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Broken line is commented out, healthy settings survive")
    void brokenSectionRemoved() throws Exception {
        File file = writeConfig(
                "[healthy]\n" +
                "enabled = true\n" +
                "name = \"keep me\"\n" +
                "[broken]\n" +
                "value = \"unclosed string\n" +
                "[another]\n" +
                "count = 7\n");

        assertFalse(parses(file), "precondition: file must be broken");

        TomlCrashSalvage.Result result =
                TomlCrashSalvage.salvageFile(file, NO_LOGS::add) ? null : null;

        // salvageFile returns boolean; use the pure salvage() to get the Result object
        TomlCrashSalvage.Result pure = TomlCrashSalvage.salvage(writeFreshCopy(
                "[healthy]\n" +
                "enabled = true\n" +
                "name = \"keep me\"\n" +
                "[broken]\n" +
                "value = \"unclosed string\n" +
                "[another]\n" +
                "count = 7\n"));

        assertTrue(pure.success, pure.message);
        assertFalse(pure.commentedLines.isEmpty(), "some line must have been commented");

        // The salvageFile() run above must have stabilized the FIRST copy:
        assertTrue(parses(file), "after salvageFile the file must parse");
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"keep me\""), "healthy value must survive");
        assertTrue(content.contains("count = 7"), "healthy settings must survive");
        assertFalse(backupDir().exists(), "config-broken folder must not be created");
        assertTrue(file.exists(), "the file must never be deleted");
    }

    @Test
    @DisplayName("Multiple broken lines are commented one by one")
    void multipleBrokenLines() throws Exception {
        File file = writeConfig(
                "[good]\n" +
                "enabled = true\n" +
                "[broken_a]\n" +
                "value = \"unclosed\n" +
                "[broken_b]\n" +
                "list = [1, 2\n");

        TomlCrashSalvage.Result result = TomlCrashSalvage.salvage(file);

        assertTrue(result.success, result.message);
        assertTrue(result.commentedLines.size() >= 2,
                "both broken lines must be commented, got: " + result.commentedLines);
        assertTrue(parses(file));
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("enabled = true"), "healthy section value must survive");
    }

    @Test
    @DisplayName("Healthy config is left untouched")
    void healthyFileUntouched() throws Exception {
        String content =
                "[a]\n" +
                "enabled = true\n" +
                "[b]\n" +
                "count = 3\n";
        File file = writeConfig(content);

        TomlCrashSalvage.Result result = TomlCrashSalvage.salvage(file);

        assertTrue(result.success);
        assertTrue(result.commentedLines.isEmpty());
        assertFalse(backupDir().exists(), "no backups for a healthy file");
        String after = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(after.equals(content), "healthy file must not be modified");
    }

    @Test
    @DisplayName("Unquoted string value is auto-fixed by commenting the line")
    void unquotedStringSalvaged() throws Exception {
        File file = writeConfig(
                "[section]\n" +
                "key = value_without_quotes_and_spaces\n" +
                "other = 1\n");

        TomlCrashSalvage.Result result = TomlCrashSalvage.salvage(file);

        assertTrue(result.success, result.message);
        assertTrue(parses(file), "after salvage the file must parse");
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("other = 1"), "healthy lines must survive");
        assertTrue(file.exists(), "the file must never be deleted");
    }

    /** Writes a fresh temp file (second copy for the pure salvage() call). */
    private File writeFreshCopy(String content) throws Exception {
        File file = tempDir.resolve("UI-Test-pure.toml").toFile();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }
}
