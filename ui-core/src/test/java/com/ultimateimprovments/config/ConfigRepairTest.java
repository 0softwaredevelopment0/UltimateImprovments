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
 * Integrity checks for the bundled {@code config.yml}:
 * <ul>
 *   <li>Every rule key from {@link ConfigRules} exists in the config (no missing values)</li>
 *   <li>The raw file has no duplicate keys at the same nesting level (SnakeYAML would silently
 *       keep only the last one — user edits in the first copy would be lost)</li>
 * </ul>
 */
class ConfigRepairTest {

    /** Loads the bundled config.yml resource exactly as Bukkit does. */
    private static YamlConfiguration loadBundledConfig() {
        InputStream in = ConfigRepairTest.class.getResourceAsStream("/config.yml");
        assertTrue(in != null, "config.yml resource must exist");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    // ============================================================
    // Completeness vs ConfigRules
    // ============================================================

    @Test
    @DisplayName("Every ConfigRules key is present in config.yml")
    void allRuleKeysPresent() {
        YamlConfiguration config = loadBundledConfig();

        List<String> missing = new ArrayList<>();
        for (ConfigRules.Rule rule : ConfigRules.ALL) {
            if (!config.isSet(rule.key)) {
                missing.add(rule.key);
            }
        }
        assertTrue(missing.isEmpty(), "Missing config keys (would be auto-repaired, but should exist in the bundle): " + missing);
    }

    // ============================================================
    // Duplicate key detection (raw line scan — before YAML parsing)
    // ============================================================

    /**
     * Scans the raw file line-by-line: two identical full paths at the same depth
     * are duplicates. List items and block scalars (|, >) are handled.
     */
    @Test
    @DisplayName("config.yml has no duplicate keys at the same level")
    void noDuplicateKeys() throws Exception {
        List<String> lines;
        try (InputStream in = ConfigRepairTest.class.getResourceAsStream("/config.yml")) {
            assertTrue(in != null, "config.yml resource must exist");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }

        List<String> dupes = findRawDuplicates(lines);
        assertTrue(dupes.isEmpty(), "Duplicate keys in config.yml (SnakeYAML keeps only the last — first copy is dead): " + dupes);
    }

    /** Raw duplicate scan: returns human-readable duplicate paths. */
    static List<String> findRawDuplicates(List<String> lines) {
        List<String> dupes = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        List<int[]> stack = new ArrayList<>(); // {indent, keyIndex into keys}
        List<String> keys = new ArrayList<>();
        boolean inBlockScalar = false;
        int blockScalarIndent = 0;
        int listCounter = 0;
        // list item scope: {indent, fullPathOfItemKey}
        int[] listItemScope = null;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            int indent = leadingSpaces(raw);

            // inside block scalar — skip until dedent
            if (inBlockScalar) {
                if (indent <= blockScalarIndent) inBlockScalar = false;
                else continue;
            }

            // list item
            if (trimmed.startsWith("- ")) {
                if (trimmed.endsWith("|") || trimmed.endsWith(">")) {
                    inBlockScalar = true;
                    blockScalarIndent = indent;
                    listItemScope = null;
                } else if (trimmed.matches("^-\\s+\\S[^:]*:\\s.*")) {
                    // map item in a list: "- key: value" — each item gets unique #N path
                    listCounter++;
                    String itemKey = trimmed.replaceFirst("^-\\s+", "").replaceFirst(":.*", "").trim()
                            .replaceAll("^[\"']|[\"']$", "");
                    while (!stack.isEmpty() && stack.get(stack.size() - 1)[0] >= indent) stack.remove(stack.size() - 1);
                    stack.add(new int[]{indent, keys.size()});
                    keys.add(itemKey);
                    listItemScope = new int[]{indent, keys.size() - 1};
                } else {
                    listItemScope = null;
                }
                continue;
            }

            // "key:" line
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([^:#]+?)\\s*:").matcher(trimmed);
            if (!m.find()) continue;
            String key = m.group(1).trim().replaceAll("^[\"']|[\"']$", "");

            // If inside a list-item map scope, sub-keys are scoped under the item
            // key with a #N marker so items don't collide with each other.
            if (listItemScope != null && indent > listItemScope[0]) {
                String itemPath = buildPath(stack, keys);
                String full = itemPath + "#" + listCounter + "." + key;
                if (seen.containsKey(full)) {
                    dupes.add(full + " (lines " + seen.get(full) + " and " + (i + 1) + ")");
                } else {
                    seen.put(full, i + 1);
                }
                continue;
            }
            listItemScope = null;

            while (!stack.isEmpty() && stack.get(stack.size() - 1)[0] >= indent) stack.remove(stack.size() - 1);
            stack.add(new int[]{indent, keys.size()});
            keys.add(key);

            if (trimmed.endsWith("|") || trimmed.endsWith(">")) {
                inBlockScalar = true;
                blockScalarIndent = indent;
            }

            String full = buildPath(stack, keys);
            if (seen.containsKey(full)) {
                dupes.add(full + " (lines " + seen.get(full) + " and " + (i + 1) + ")");
            } else {
                seen.put(full, i + 1);
            }
        }
        return dupes;
    }

    /** Builds a dotted path from the current key stack. */
    private static String buildPath(List<int[]> stack, List<String> keys) {
        StringBuilder path = new StringBuilder();
        for (int[] entry : stack) {
            if (path.length() > 0) path.append('.');
            path.append(keys.get(entry[1]));
        }
        return path.toString();
    }

    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && (s.charAt(n) == ' ' || s.charAt(n) == '\t')) n++;
        return n;
    }
}
