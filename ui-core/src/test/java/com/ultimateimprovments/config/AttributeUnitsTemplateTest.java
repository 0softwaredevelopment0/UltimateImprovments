package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@code features.attributes.units.*} block of the bundled UI-Other
 * template — the config surface of the scheduled attribute-grant manager:
 * <ul>
 *   <li>the shipped defaults exist (unit 1-4 = waypoint ranges and name-tag
 *       distances, units 5-7 = the legacy attack-damage/speed values),</li>
 *   <li>the legacy flat keys are gone (they are migrated into units by
 *       {@link AddonConfigManager}),</li>
 *   <li>ordinal unit headers survive the TOML write/read round-trip — the
 *       serializer must not mangle the numeric table names.</li>
 * </ul>
 */
class AttributeUnitsTemplateTest {

    /** Loads the bundled UI-Other template as a flattened Bukkit view. */
    private static YamlConfiguration loadOtherTemplate() throws Exception {
        try (InputStream in = AttributeUnitsTemplateTest.class.getResourceAsStream("/config/UI-Other.toml")) {
            assertNotNull(in, "bundled config/UI-Other.toml must exist");
            YamlConfiguration view = new YamlConfiguration();
            flatten(new Toml().read(new InputStreamReader(in, StandardCharsets.UTF_8)).toMap(), "", view);
            return view;
        }
    }

    private static void flatten(Map<String, Object> map, String prefix, YamlConfiguration target) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            // Same normalization the production loader applies (toml4j keeps the
            // quotes of a quoted table header such as units."1").
            String segment = CommentedTomlTemplate.stripQuotes(e.getKey());
            String path = prefix.isEmpty() ? segment : prefix + "." + segment;
            if (e.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flatten(nestedMap, path, target);
            } else {
                target.set(path, e.getValue());
            }
        }
    }

    private static String unit(String id, String key) {
        return "features.attributes.units." + id + "." + key;
    }

    @Test
    @DisplayName("Scheduled attribute units ship the documented defaults and the module is ON")
    void scheduledUnitsHaveDefaults() throws Exception {
        YamlConfiguration cfg = loadOtherTemplate();

        assertTrue(cfg.getBoolean("features.attributes.enabled"),
                "features.attributes.enabled must default to true");

        // Unit 1-4: the range attributes this feature was introduced for.
        assertEquals("minecraft:waypoint_receive_range", cfg.getString(unit("1", "attribute")));
        assertEquals(8192.0, cfg.getDouble(unit("1", "value")), 0.0001);
        assertEquals("minecraft:waypoint_transmit_range", cfg.getString(unit("2", "attribute")));
        assertEquals(8192.0, cfg.getDouble(unit("2", "value")), 0.0001);
        assertEquals("minecraft:below_name_distance", cfg.getString(unit("3", "attribute")));
        assertEquals(8.0, cfg.getDouble(unit("3", "value")), 0.0001);
        assertEquals("minecraft:name_tag_distance", cfg.getString(unit("4", "attribute")));
        assertEquals(8.0, cfg.getDouble(unit("4", "value")), 0.0001);

        // Unit 5-7: the legacy flat attributes, preserved as units.
        assertEquals("minecraft:attack_damage", cfg.getString(unit("5", "attribute")));
        assertEquals(0.1, cfg.getDouble(unit("5", "value")), 0.0001);
        assertEquals("minecraft:sneaking_speed", cfg.getString(unit("6", "attribute")));
        assertEquals(1.0, cfg.getDouble(unit("6", "value")), 0.0001);
        assertEquals("minecraft:attack_speed", cfg.getString(unit("7", "attribute")));
        assertEquals(3.5, cfg.getDouble(unit("7", "value")), 0.0001);

        // Every unit carries its own schedule and target list.
        for (String id : List.of("1", "2", "3", "4", "5", "6", "7")) {
            assertEquals(200, cfg.getInt(unit(id, "interval_ticks")),
                    "unit " + id + " must default to a 200-tick interval");
            assertEquals(List.of("all"), cfg.getStringList(unit(id, "targets")),
                    "unit " + id + " must target every online player by default");
        }
    }

    @Test
    @DisplayName("Legacy flat attribute keys are not part of the template any more")
    void legacyFlatKeysRemoved() throws Exception {
        YamlConfiguration cfg = loadOtherTemplate();
        for (String legacy : List.of("attack_damage", "sneak_speed", "attack_speed", "interval_ticks")) {
            assertFalse(cfg.isSet("features.attributes." + legacy),
                    "legacy key features.attributes." + legacy + " must be migrated into units");
        }
    }

    @Test
    @DisplayName("Ordinal unit tables survive the TOML write/read round-trip")
    void ordinalUnitTablesRoundTrip(@TempDir Path tempDir) throws Exception {
        YamlConfiguration cfg = loadOtherTemplate();
        File out = tempDir.resolve("UI-Other-roundtrip.toml").toFile();

        // Serialize exactly like AddonConfigManager does on every config write.
        CommentedTomlTemplate.write(out, TomlConfigManager.sectionToMap(cfg), null, null);
        assertTrue(out.exists(), "writer must produce the file");

        YamlConfiguration reparsed = new YamlConfiguration();
        flatten(new Toml().read(out).toMap(), "", reparsed);

        for (String id : List.of("1", "2", "3", "4")) {
            assertEquals(cfg.getString(unit(id, "attribute")), reparsed.getString(unit(id, "attribute")),
                    "unit " + id + " attribute must survive the round-trip");
            assertEquals(cfg.getDouble(unit(id, "value")), reparsed.getDouble(unit(id, "value")), 0.0001,
                    "unit " + id + " value must survive the round-trip");
            assertEquals(cfg.getStringList(unit(id, "targets")), reparsed.getStringList(unit(id, "targets")),
                    "unit " + id + " targets must survive the round-trip");
        }
    }
}
