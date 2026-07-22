package com.mcplugin.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigGuideManager}'s pure-logic helpers.
 * <p>
 * Использует reflection, чтобы не ослаблять инкапсуляцию (методы остаются {@code private}).
 * Тесты НЕ требуют Bukkit plugin instance: проверяется только pure Java логика
 * (sha256, hash canonicalization, dedupe, CRLF marker matching).
 */
class ConfigGuideManagerTest {

    private File tempDir;
    private File configFile;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("mcplugin-config-guide-test").toFile();
        configFile = new File(tempDir, "config.yml");
    }

    @AfterEach
    void tearDown() {
        deleteRecursive(tempDir);
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    // ============================================================
    // sha256 tests
    // ============================================================

    @Test
    @DisplayName("sha256: same input -> same hash (consistency)")
    void testSha256Consistency() throws Exception {
        Method m = ConfigGuideManager.class.getDeclaredMethod("sha256", String.class);
        m.setAccessible(true);
        String h1 = (String) m.invoke(null, "hello world");
        String h2 = (String) m.invoke(null, "hello world");
        assertEquals(h1, h2, "Same input should produce the same hash");
        assertEquals(64, h1.length(), "SHA-256 hex should be 64 chars");
    }

    @Test
    @DisplayName("sha256: different inputs -> different hashes")
    void testSha256DifferentInputsProduceDifferentHashes() throws Exception {
        Method m = ConfigGuideManager.class.getDeclaredMethod("sha256", String.class);
        m.setAccessible(true);
        String h1 = (String) m.invoke(null, "hello world");
        String h2 = (String) m.invoke(null, "hello WORLD");
        assertNotEquals(h1, h2, "Different inputs should produce different hashes");
    }

    // ============================================================
    // Hash canonicalization round-trip (raw vs #-prefixed file form)
    // ============================================================

    @Test
    @DisplayName("Hash canonicalization: raw text hash == file-prefixed text hash (no false 'needs update')")
    void testHashCanonicalizationRoundTrip() throws Exception {
        // КЛЮЧЕВОЕ СВОЙСТВО: если хеш отличается, гайд будет переэмбежиться на КАЖДОМ рестарте.
        // Тест вызывает РЕАЛЬНЫЙ production-helper reconstructRawGuideText (не симулирует его в тесте)
        // — если production сломается, тест сломается тоже.
        // Raw text from JAR guide (no '# ' prefix). String.split("\\R", -1) preserves trailing empty.
        String rawText = "Line 1\nLine 2\n\nLine 4 after blank\n";

        // BEGIN/END markers окружают содержимое (exclusive — мы тестируем внутренности).
        // Симулируем то, что Files.write + Files.readAllLines сделали бы с префиксным контентом.
        List<String> fileLines = new ArrayList<>();
        fileLines.add(ConfigGuideManager.GUIDE_BEGIN_MARKER);
        for (String line : rawText.split("\\R", -1)) {
            fileLines.add(line.isEmpty() ? "#" : "# " + line);
        }
        fileLines.add(ConfigGuideManager.GUIDE_END_MARKER);

        int beginIdx = 0;
        int endIdx = fileLines.size() - 1;

        Method sha256 = ConfigGuideManager.class.getDeclaredMethod("sha256", String.class);
        sha256.setAccessible(true);
        String rawHash = (String) sha256.invoke(null, rawText);

        String reconstructed = ConfigGuideManager.reconstructRawGuideText(fileLines, beginIdx, endIdx);
        String reconstructedHash = (String) sha256.invoke(null, reconstructed);

        assertEquals(rawHash, reconstructedHash,
                "Hash of raw jar text MUST equal sha256 of reconstructRawGuideText(fileLines). " +
                "Otherwise the guide re-embeds on every restart.");
    }

    @Test
    @DisplayName("Hash canonicalization: round-trip works with single-line, empty, '#' edge cases")
    void testHashCanonicalizationWithEdgeCases() throws Exception {
        // Несколько edge cases — каждый проверяет что String.join построен правильно.
        Method sha256 = ConfigGuideManager.class.getDeclaredMethod("sha256", String.class);
        sha256.setAccessible(true);

        assertRoundTrip(sha256, "#JustHashTag\n\nnormal line\n", "hash-tag + blank + line");
        assertRoundTrip(sha256, "Hello", "single line, no trailing nl");
        assertRoundTrip(sha256, "Hello\n", "single line with trailing nl");
        assertRoundTrip(sha256, "", "empty");
        assertRoundTrip(sha256, "\n", "just newline");
        assertRoundTrip(sha256, "Line 1\nLine 2", "two lines, no trailing");
        assertRoundTrip(sha256, "Line 1\nLine 2\n", "two lines, trailing nl");
        assertRoundTrip(sha256, "a\n\nb\n", "blank in middle + trailing nl");
    }

    private static void assertRoundTrip(Method sha256, String rawText, String description) throws Exception {
        // Строим префиксный файл-список по той же логике, что и production-code при ЗАПИСИ.
        List<String> fileLines = new ArrayList<>();
        fileLines.add(ConfigGuideManager.GUIDE_BEGIN_MARKER);
        for (String line : rawText.split("\\R", -1)) {
            fileLines.add(line.isEmpty() ? "#" : "# " + line);
        }
        fileLines.add(ConfigGuideManager.GUIDE_END_MARKER);

        String rawHash = (String) sha256.invoke(null, rawText);
        String reconstructed = ConfigGuideManager.reconstructRawGuideText(fileLines, 0, fileLines.size() - 1);
        String reconstructedHash = (String) sha256.invoke(null, reconstructed);

        assertEquals(rawHash, reconstructedHash,
                "Round-trip failed for case: " + description + "\nraw=" + rawText + "\nreconstructed=" + reconstructed);
    }

    // ============================================================
    // dedupeMetaBlocks tests
    // ============================================================

    @Test
    @DisplayName("dedupeMetaBlocks: 3 _meta: blocks -> only the last one remains with its sub-keys")
    void testDedupeMetaBlocksRemovesDuplicates() throws Exception {
        // Симулируем ситуацию: snakeyaml save был вызван, и _meta оказался в середине файла,
        // плюс наш upsertMetaBlockAtEnd добавил блок в самый низ.
        List<String> initialContent = Arrays.asList(
                "some_other_key: foo",
                "_meta:",
                "  guide_hash: \"oldmidhash\"",
                "another_key: bar",
                "# === INTEGRITY META — НЕ РЕДАКТИРОВАТЬ (auto-managed by ConfigGuideManager) ===",
                "",
                "_meta:",
                "  guide_hash: \"latestbottomhash\""
        );
        Files.write(configFile.toPath(), initialContent, StandardCharsets.UTF_8);

        Method m = ConfigGuideManager.class.getDeclaredMethod("dedupeMetaBlocks", File.class);
        m.setAccessible(true);
        m.invoke(null, configFile);

        List<String> result = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        long metaCount = result.stream().filter(line -> line.trim().equals("_meta:")).count();
        assertEquals(1, metaCount,
                "After dedupe there must be exactly one _meta: line");
        // Latest (kept) hash is "latestbottomhash"
        assertTrue(result.stream().anyMatch(l -> l.contains("latestbottomhash")),
                "The kept _meta block should be the LAST one inserted");
        assertFalse(result.stream().anyMatch(l -> l.contains("oldmidhash")),
                "Old intermediate _meta must be removed");

        // The line ordering: "_meta:" should be the LAST non-empty line area
        int metaIdx = -1;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).trim().equals("_meta:")) metaIdx = i;
        }
        assertTrue(metaIdx > result.size() - 4,
                "The surviving _meta: should be near the end of the file (we kept the last one)");
    }

    @Test
    @DisplayName("dedupeMetaBlocks: single _meta: block is unchanged (idempotent)")
    void testDedupeMetaBlocksIdempotent() throws Exception {
        List<String> initial = Arrays.asList(
                "key1: value1",
                "_meta:",
                "  guide_hash: \"abc\""
        );
        Files.write(configFile.toPath(), initial, StandardCharsets.UTF_8);

        Method m = ConfigGuideManager.class.getDeclaredMethod("dedupeMetaBlocks", File.class);
        m.setAccessible(true);
        m.invoke(null, configFile);

        List<String> result = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(3, result.size(), "Single _meta block must remain unchanged");
        assertEquals("_meta:", result.get(1));
        assertEquals("  guide_hash: \"abc\"", result.get(2));
    }

    @Test
    @DisplayName("dedupeMetaBlocks: file with NO _meta: is left untouched")
    void testDedupeMetaBlocksNoMetaFile() throws Exception {
        List<String> initial = Arrays.asList(
                "auth:",
                "  enabled: true",
                "energy:",
                "  amount: 100"
        );
        Files.write(configFile.toPath(), initial, StandardCharsets.UTF_8);

        Method m = ConfigGuideManager.class.getDeclaredMethod("dedupeMetaBlocks", File.class);
        m.setAccessible(true);
        m.invoke(null, configFile);

        List<String> result = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(initial, result, "File without _meta: must remain unchanged");
    }

    // ============================================================
    // indexOfLine CRLF tolerance
    // ============================================================

    @Test
    @DisplayName("indexOfLine: matches both LF and CRLF versions of the marker")
    void testIndexOfLineCrlfTolerant() throws Exception {
        Method m = ConfigGuideManager.class.getDeclaredMethod("indexOfLine", List.class, String.class);
        m.setAccessible(true);

        List<String> lfLines = Arrays.asList(
                ConfigGuideManager.GUIDE_BEGIN_MARKER,  // exact LF match
                "  guide content",
                ConfigGuideManager.GUIDE_END_MARKER
        );
        int idxLF = (int) m.invoke(null, lfLines, ConfigGuideManager.GUIDE_BEGIN_MARKER);
        assertEquals(0, idxLF, "LF marker should match exactly");

        // CRLF: append \r to each line
        List<String> crlfLines = Arrays.asList(
                ConfigGuideManager.GUIDE_BEGIN_MARKER + "\r",
                "  guide content\r",
                ConfigGuideManager.GUIDE_END_MARKER + "\r"
        );
        int idxCRLF_Begin = (int) m.invoke(null, crlfLines, ConfigGuideManager.GUIDE_BEGIN_MARKER);
        int idxCRLF_End = (int) m.invoke(null, crlfLines, ConfigGuideManager.GUIDE_END_MARKER);
        assertEquals(0, idxCRLF_Begin, "BEGIN marker should match even with CRLF line ending");
        assertEquals(2, idxCRLF_End, "END marker should match even with CRLF line ending");
    }

    @Test
    @DisplayName("indexOfLine: returns -1 when marker is absent")
    void testIndexOfLineAbsentReturnsMinus1() throws Exception {
        Method m = ConfigGuideManager.class.getDeclaredMethod("indexOfLine", List.class, String.class);
        m.setAccessible(true);

        List<String> lines = Arrays.asList(
                "  just some content",
                "  nothing useful",
                "  no markers here"
        );
        int idx = (int) m.invoke(null, lines, ConfigGuideManager.GUIDE_BEGIN_MARKER);
        assertEquals(-1, idx, "Missing marker must return -1, not 0");

        // Пустой список
        assertEquals(-1, (int) m.invoke(null, List.of(), ConfigGuideManager.GUIDE_BEGIN_MARKER),
                "Empty list must return -1");
    }

    @Test
    @DisplayName("reconstructRawGuideText: empty range returns empty string")
    void testReconstructRawGuideTextEmptyRange() {
        // beginIdx..endIdx содержит только маркеры рядом (beginIdx+1 == endIdx)
        List<String> onlyBothMarkers = Arrays.asList(
                ConfigGuideManager.GUIDE_BEGIN_MARKER,
                ConfigGuideManager.GUIDE_END_MARKER
        );
        String result = ConfigGuideManager.reconstructRawGuideText(onlyBothMarkers, 0, 1);
        assertEquals("", result, "Direct adjacency between BEGIN and END must yield empty raw text");

        // Один только BEGIN без END (но beginIdx+1 < endIdx < size still)
        // В этом случае итерация бежит 0 раз, join → "".
        List<String> onlyBegin = Arrays.asList(ConfigGuideManager.GUIDE_BEGIN_MARKER);
        String s = ConfigGuideManager.reconstructRawGuideText(onlyBegin, 0, 0);
        assertEquals("", s, "Empty begin-end range must yield empty raw text");
    }
}
