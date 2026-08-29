package com.ultimateimprovments.broadcast;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BroadcastCondition} and {@link BroadcastSection} parsing.
 * <p>
 * Проверяет только чистую логику парсинга/дедупликации условий (без Bukkit server):
 * распознавание имён и операторов, валидацию значений, предупреждения, группировку
 * «одинаковые условия = ИЛИ» и отсев дубликатов «имя=значение».
 */
class AutoBroadcastConditionTest {

    // ============================================================
    // BroadcastCondition.parse — распознавание условий
    // ============================================================

    @Test
    @DisplayName("parse: boolean condition is-op=true")
    void parseIsOp() {
        BroadcastCondition c = BroadcastCondition.parse("s", "is-op=true", new ArrayList<>());
        assertNotNull(c);
        assertEquals("is-op", c.getName());
        assertEquals("true", c.getRawValue());
        assertFalse(c.isGlobal());
    }

    @Test
    @DisplayName("parse: gamemode condition")
    void parseGameMode() {
        BroadcastCondition c = BroadcastCondition.parse("s", "is-gamemode=survival", new ArrayList<>());
        assertNotNull(c);
        assertEquals("is-gamemode", c.getName());
    }

    @Test
    @DisplayName("parse: height-above=25 (operator + int value)")
    void parseHeightAbove() {
        BroadcastCondition c = BroadcastCondition.parse("s", "height-above=25", new ArrayList<>());
        assertNotNull(c);
        assertEquals("height-above", c.getName());
        assertFalse(c.isGlobal());
    }

    @Test
    @DisplayName("parse: online-is=5 is global (server condition)")
    void parseOnlineIsGlobal() {
        BroadcastCondition c = BroadcastCondition.parse("s", "online-is=5", new ArrayList<>());
        assertNotNull(c);
        assertEquals("online-is", c.getName());
        assertTrue(c.isGlobal());
    }

    @Test
    @DisplayName("parse: xp-lvl-below=30")
    void parseXpLvl() {
        BroadcastCondition c = BroadcastCondition.parse("s", "xp-lvl-below=30", new ArrayList<>());
        assertNotNull(c);
        assertEquals("xp-lvl-below", c.getName());
    }

    @Test
    @DisplayName("parse: is-group=admin")
    void parseGroup() {
        BroadcastCondition c = BroadcastCondition.parse("s", "is-group=admin", new ArrayList<>());
        assertNotNull(c);
        assertEquals("is-group", c.getName());
        assertEquals("admin", c.getRawValue());
    }

    // ============================================================
    // BroadcastCondition.parse — невалидные значения
    // ============================================================

    @Test
    @DisplayName("parse: invalid boolean is-op=yes -> null + warning")
    void parseInvalidBoolean() {
        List<String> warnings = new ArrayList<>();
        assertNull(BroadcastCondition.parse("s", "is-op=yes", warnings));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("true/false")));
    }

    @Test
    @DisplayName("parse: invalid gamemode is-gamemode=bogus -> null + warning")
    void parseInvalidGameMode() {
        List<String> warnings = new ArrayList<>();
        assertNull(BroadcastCondition.parse("s", "is-gamemode=bogus", warnings));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("is-gamemode")));
    }

    @Test
    @DisplayName("parse: non-integer height-above=abc -> null + warning")
    void parseInvalidInt() {
        List<String> warnings = new ArrayList<>();
        assertNull(BroadcastCondition.parse("s", "height-above=abc", warnings));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("integer")));
    }

    @Test
    @DisplayName("parse: unknown condition -> null + warning")
    void parseUnknown() {
        List<String> warnings = new ArrayList<>();
        assertNull(BroadcastCondition.parse("s", "some-nonsense=1", warnings));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unknown condition")));
    }

    @Test
    @DisplayName("parse: no '=' sign -> null + warning")
    void parseNoEquals() {
        List<String> warnings = new ArrayList<>();
        assertNull(BroadcastCondition.parse("s", "justtext", warnings));
        assertFalse(warnings.isEmpty());
    }

    // ============================================================
    // BroadcastSection.parse — группировка и дедупликация
    // ============================================================

    @Test
    @DisplayName("section: same condition different values = OR (grouped under one name)")
    void sectionGroupsSameNameOr() throws Exception {
        BroadcastSection section = parseSection(
                "is-gamemode=survival, is-gamemode=creative");
        Map<String, List<BroadcastCondition>> groups = conditionGroups(section);
        assertEquals(1, groups.size(), "Both gamemode values must group under one name");
        assertEquals(2, groups.get("is-gamemode").size(),
                "Two different values of the same condition = OR (both kept)");
    }

    @Test
    @DisplayName("section: different conditions = AND (separate groups)")
    void sectionDifferentNamesAnd() throws Exception {
        BroadcastSection section = parseSection(
                "is-op=false, is-gamemode=survival, online-above=1");
        Map<String, List<BroadcastCondition>> groups = conditionGroups(section);
        assertEquals(3, groups.size());
        assertTrue(groups.containsKey("is-op"));
        assertTrue(groups.containsKey("is-gamemode"));
        assertTrue(groups.containsKey("online-above"));
    }

    @Test
    @DisplayName("section: duplicate name=value is warned and only first kept")
    void sectionDuplicateWarned() throws Exception {
        BroadcastSection section = parseSection("is-op=true, is-op=true");
        Map<String, List<BroadcastCondition>> groups = conditionGroups(section);
        assertEquals(1, groups.get("is-op").size(),
                "Duplicate condition=value must be reduced to one entry");
    }

    @Test
    @DisplayName("section: duplicate with different case value also deduped")
    void sectionDuplicateCaseInsensitive() throws Exception {
        BroadcastSection section = parseSection("is-gamemode=Survival, is-gamemode=survival");
        Map<String, List<BroadcastCondition>> groups = conditionGroups(section);
        assertEquals(1, groups.get("is-gamemode").size(),
                "Dedupe must be case-insensitive");
    }

    @Test
    @DisplayName("section: invalid entries ignored, valid ones kept")
    void sectionInvalidIgnored() throws Exception {
        BroadcastSection section = parseSection(
                "is-op=true, is-gamemode=bogus, height-above=abc, unknown=x");
        Map<String, List<BroadcastCondition>> groups = conditionGroups(section);
        assertEquals(1, groups.size());
        assertTrue(groups.containsKey("is-op"));
    }

    @Test
    @DisplayName("section: empty conditions -> no groups (broadcast to everyone)")
    void sectionEmptyConditions() throws Exception {
        BroadcastSection section = parseSection("");
        assertEquals(0, conditionGroups(section).size());
    }

    @Test
    @DisplayName("section: global online condition is detected as global")
    void sectionOnlineGlobal() throws Exception {
        BroadcastSection section = parseSection("is-op=false, online-above=5");
        Map<String, List<BroadcastCondition>> groups = conditionGroups(section);
        assertEquals(2, groups.size());
        assertTrue(groups.get("online-above").get(0).isGlobal());
        assertFalse(groups.get("is-op").get(0).isGlobal());
    }

    @Test
    @DisplayName("section: messages and cooldown parsed")
    void sectionCoreFields() {
        ConfigurationSection section = yamlSection(
                "example:\n" +
                "  enabled: true\n" +
                "  cooldown_ticks: 2400\n" +
                "  conditions: \"is-op=false\"\n" +
                "  messages:\n" +
                "    - \"<gold>One</gold>\"\n" +
                "    - \"<green>Two</green>\"\n", "example");
        assertNotNull(section);
        BroadcastSection parsed = BroadcastSection.parse(section);
        assertNotNull(parsed);
        assertEquals("example", parsed.getName());
        assertEquals(2400, parsed.getCooldownTicks());
        assertEquals(2, parsed.getMessageCount());
    }

    @Test
    @DisplayName("section: rotation nextMessage cycles through messages")
    void sectionRotation() {
        ConfigurationSection section = yamlSection(
                "example:\n" +
                "  cooldown_ticks: 100\n" +
                "  messages:\n" +
                "    - \"A\"\n" +
                "    - \"B\"\n", "example");
        assertNotNull(section);
        BroadcastSection parsed = BroadcastSection.parse(section);
        assertNotNull(parsed);
        assertEquals("A", parsed.nextMessage());
        assertEquals("B", parsed.nextMessage());
        assertEquals("A", parsed.nextMessage(), "Messages must rotate cyclically");
    }

    @Test
    @DisplayName("section: empty messages -> null (section skipped)")
    void sectionEmptyMessagesSkipped() {
        ConfigurationSection section = yamlSection(
                "empty:\n" +
                "  cooldown_ticks: 100\n" +
                "  messages: []\n", "empty");
        assertNotNull(section);
        assertNull(BroadcastSection.parse(section));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static BroadcastSection parseSection(String conditions) {
        ConfigurationSection section = yamlSection(
                "s:\n" +
                "  enabled: true\n" +
                "  cooldown_ticks: 1200\n" +
                "  conditions: \"" + conditions + "\"\n" +
                "  messages:\n" +
                "    - \"<gold>Hi</gold>\"\n", "s");
        assertNotNull(section);
        return BroadcastSection.parse(section);
    }

    private static ConfigurationSection yamlSection(String yaml, String sectionName) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception e) {
            fail("Invalid test YAML: " + e.getMessage());
        }
        return cfg.getConfigurationSection(sectionName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<BroadcastCondition>> conditionGroups(BroadcastSection section) throws Exception {
        Field field = BroadcastSection.class.getDeclaredField("conditionGroups");
        field.setAccessible(true);
        return (Map<String, List<BroadcastCondition>>) field.get(section);
    }
}
