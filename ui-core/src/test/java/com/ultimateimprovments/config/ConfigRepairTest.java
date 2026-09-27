package com.ultimateimprovments.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrity checks for the bundled per-addon TOML templates
 * ({@code config/UI-<Addon>.toml} in ui-core resources):
 * <ul>
 *   <li>Every rule key from {@link ConfigRules} exists in the OWNING addon's
 *       template (per {@link AddonCatalog} routing) — no missing defaults.</li>
 *   <li>Every root key routed by {@link AddonCatalog} actually exists in its
 *       addon template (routing table cannot point at nowhere).</li>
 *   <li>Each template file has no duplicate keys at the same nesting level
 *       (toml4j would silently keep only the last one).</li>
 * </ul>
 * The monolithic config.yml resource was REMOVED: per-addon TOML templates are
 * the single source of defaults (they bootstrap the server-side
 * {@code configs/UI-<Addon>.toml} and repair missing keys there).
 */
class ConfigRepairTest {

    /** Cached per-addon views (template → Bukkit view). */
    private static final Map<String, YamlConfiguration> TEMPLATE_CACHE = new HashMap<>();

    /** Loads one bundled per-addon TOML template as a Bukkit view. */
    private static synchronized YamlConfiguration loadTemplate(String addon) {
        return TEMPLATE_CACHE.computeIfAbsent(addon, name -> {
            InputStream in = ConfigRepairTest.class.getResourceAsStream("/config/" + name + ".toml");
            assertTrue(in != null, "config/" + name + ".toml resource must exist");
            YamlConfiguration view = new YamlConfiguration();
            try {
                com.moandjiezana.toml.Toml parsed = new com.moandjiezana.toml.Toml()
                        .read(new InputStreamReader(in, StandardCharsets.UTF_8));
                flattenInto(parsed.toMap(), "", view);
            } catch (Exception e) {
                throw new AssertionError("Failed to parse config/" + name + ".toml: " + e.getMessage(), e);
            }
            return view;
        });
    }

    /** Recursively flattens a TOML map into dotted Bukkit paths. */
    private static void flattenInto(Map<String, Object> map, String prefix, YamlConfiguration target) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object value = e.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flattenInto(nestedMap, path, target);
            } else {
                target.set(path, value);
            }
        }
    }

    // ============================================================
    // Completeness vs ConfigRules
    // ============================================================

    @Test
    @DisplayName("Every ConfigRules key is present in its owning addon's TOML template")
    void allRuleKeysPresent() {
        List<String> missing = new ArrayList<>();
        for (ConfigRules.Rule rule : ConfigRules.ALL) {
            String addon = AddonCatalog.addonOfRootKey(rootOf(rule.key));
            YamlConfiguration template = loadTemplate(addon);
            if (!template.isSet(rule.key)) {
                missing.add(rule.key + " (expected in " + addon + ".toml)");
            }
        }
        assertTrue(missing.isEmpty(),
                "Missing config keys in per-addon templates (runtime would auto-repair empty values): " + missing);
    }

    // ============================================================
    // Routing consistency: every routed root exists in its template
    // ============================================================

    @Test
    @DisplayName("Every root key routed by AddonCatalog exists in its addon template")
    void routedRootsExistInTemplates() {
        List<String> broken = new ArrayList<>();
        // Reflection over the private routing maps is fragile — instead derive
        // the routed roots from the rules plus a smoke list of known roots.
        Set<String> knownRoots = new HashSet<>();
        for (ConfigRules.Rule rule : ConfigRules.ALL) knownRoots.add(rootOf(rule.key));
        // Roots exercised by real code paths (spot list; addonOfKey defaults to CORE
        // for anything unlisted, so a missing root key here is not fatal).
        knownRoots.addAll(List.of(
                "features", "vanish", "sunburn", "armor_effects", "armor_trim_effects",
                "auth", "economy", "enchant", "motd", "tab", "scoreboard", "bossbar",
                "chat", "chat_ping", "chat_filter", "ojm", "auto_broadcast",
                "clan", "turret", "home", "spawn", "report", "rtp", "near", "endersee", "troll",
                "anticheat", "datapack", "space", "radiation", "hazmat", "reactor",
                "energy", "energy_crafting", "access_control",
                "prefix", "messages", "messages_en"));

        for (String root : knownRoots) {
            String addon = AddonCatalog.addonOfRootKey(root);
            YamlConfiguration template = loadTemplate(addon);
            if (!template.isSet(root)) {
                broken.add(root + " routed to " + addon + ".toml but absent there");
            }
        }
        assertTrue(broken.isEmpty(), "Routed roots missing from their templates: " + broken);
    }

    // ============================================================
    // Duplicate key detection per template (raw line scan)
    // ============================================================

    @Test
    @DisplayName("Per-addon TOML templates have no duplicate keys at the same level")
    void noDuplicateKeysInTemplates() throws Exception {
        List<String> allDupes = new ArrayList<>();
        for (String addon : AddonCatalog.catalog()) {
            List<String> lines = new ArrayList<>();
            try (InputStream in = ConfigRepairTest.class.getResourceAsStream("/config/" + addon + ".toml")) {
                assertTrue(in != null, "config/" + addon + ".toml resource must exist");
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
            allDupes.addAll(findRawTomlDuplicates(addon, lines));
        }
        assertTrue(allDupes.isEmpty(),
                "Duplicate keys in per-addon TOML templates (toml4j keeps only the last): " + allDupes);
    }

    /**
     * Raw duplicate scan for TOML: tracks the current [table.header] path and
     * flags repeated {@code key = value} lines within the same table.
     */
    static List<String> findRawTomlDuplicates(String addon, List<String> lines) {
        List<String> dupes = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        String currentTable = "";
        String arrayTable = null;
        int arrayCounter = 0;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) {
                arrayTable = trimmed.substring(2, trimmed.length() - 2).trim();
                arrayCounter++;
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentTable = trimmed.substring(1, trimmed.length() - 1).trim();
                arrayTable = null;
                continue;
            }

            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim().replaceAll("^[\"']|[\"']$", "");
            String scope = arrayTable != null
                    ? arrayTable + "#" + arrayCounter
                    : currentTable;
            String full = scope + "." + key;
            if (seen.containsKey(full)) {
                dupes.add(full + " (" + addon + ".toml, lines " + seen.get(full) + " and " + (i + 1) + ")");
            } else {
                seen.put(full, i + 1);
            }
        }
        return dupes;
    }

    /** First path segment of a dotted key. */
    private static String rootOf(String dottedKey) {
        int dot = dottedKey.indexOf('.');
        return dot > 0 ? dottedKey.substring(0, dot) : dottedKey;
    }
}
