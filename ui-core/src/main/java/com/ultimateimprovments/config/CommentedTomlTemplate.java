package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CommentedTomlTemplate — a TOML file (bundled template or on-disk config) that
 * keeps its comments.
 * <p>
 * Used by {@link AddonConfigManager} so that {@code configs/UI-<Addon>.toml} stays
 * fully commented (every section and key documented, in English):
 * <ul>
 *   <li><b>First run</b> — the bundled {@code config/UI-<Addon>.toml} template is
 *       copied to disk verbatim (comments included);</li>
 *   <li><b>Every write</b> — values are re-serialized from the in-memory view, and
 *       comments are re-attached: per-table header comments and per-key comments
 *       come from the existing disk file, falling back to the bundled template
 *       (so freshly repaired keys are documented too);</li>
 *   <li><b>Repair lookup</b> — the template doubles as the defaults reference
 *       (parsed back as a Bukkit view).</li>
 * </ul>
 */
public final class CommentedTomlTemplate {

    /** Comments of one table: header lines + per-leaf-key comment lines. */
    private static final class Table {
        final List<String> headerComments = new ArrayList<>();
        final Map<String, List<String>> keyComments = new LinkedHashMap<>();
    }

    /** Bare-key segments allowed unquoted in a table header (TOML spec). */
    private static final Pattern BARE = Pattern.compile("^[A-Za-z0-9_-]+$");
    /** Segments that must be quoted even if bare-looking (digits / bool literals). */
    private static final Pattern DIGIT = Pattern.compile("^\\d");

    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    /** Raw source lines (template copy mode); null for parsed on-disk files. */
    private final List<String> rawLines;

    private CommentedTomlTemplate(List<String> rawLines) {
        this.rawLines = rawLines;
    }

    // ========================================================================
    // LOADING
    // ========================================================================

    /** Parses a commented TOML file from disk. Returns null when missing/unreadable. */
    public static CommentedTomlTemplate parse(File file) {
        if (!file.exists()) return null;
        List<String> lines = readLines(file);
        if (lines == null) return null;
        CommentedTomlTemplate t = new CommentedTomlTemplate(null);
        t.consume(lines);
        try (InputStream in = new FileInputStream(file)) {
            Map<String, Object> parsed = new Toml().read(new InputStreamReader(in, StandardCharsets.UTF_8)).toMap();
            if (parsed != null) flatten(parsed, "", t.values);
        } catch (Exception e) {
            return null;
        }
        return t;
    }

    /** Loads the bundled template {@code config/<name>.toml} from the plugin JAR. */
    public static CommentedTomlTemplate loadResource(com.ultimateimprovments.core.Main plugin, String resource) {
        if (plugin == null) return null;
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return null;
            List<String> lines = readAll(in);
            CommentedTomlTemplate t = new CommentedTomlTemplate(lines);
            t.consume(lines);
            try (InputStream in2 = plugin.getResource(resource)) {
                Map<String, Object> parsed = new Toml().read(new InputStreamReader(in2, StandardCharsets.UTF_8)).toMap();
                if (parsed != null) flatten(parsed, "", t.values);
            }
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> readLines(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> readAll(InputStream in) {
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8);
             java.io.BufferedReader br = new java.io.BufferedReader(r)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
            return lines;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Consumes raw lines: comments are attached to the current table header or to
     * the following key; every {@code [a.b]} line starts a new table bucket.
     */
    private void consume(List<String> lines) {
        Table current = rootTable();
        List<String> pending = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#")) {
                pending.add(raw.trim());
                continue;
            }
            if (line.isEmpty()) {
                pending.clear(); // blank lines break comment attachment
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]") && !line.startsWith("[[")) {
                String path = unquoteHeader(line.substring(1, line.length() - 1));
                current = tables.computeIfAbsent(path, p -> new Table());
                current.headerComments.addAll(pending);
                pending.clear();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                String key = line.substring(0, eq).trim();
                key = unquoteKey(key);
                current.keyComments.put(key, new ArrayList<>(pending));
                pending.clear();
            }
        }
    }

    private Table rootTable() {
        return tables.computeIfAbsent("", p -> new Table());
    }

    // ========================================================================
    // LOOKUPS
    // ========================================================================

    /** Header comments of a table path ("" = root). */
    public List<String> headerComments(String tablePath) {
        Table t = tables.get(tablePath);
        return t == null ? List.of() : t.headerComments;
    }

    /** Comment lines attached to one key of a table. */
    public List<String> keyComments(String tablePath, String key) {
        Table t = tables.get(tablePath);
        return t == null ? List.of() : t.keyComments.getOrDefault(key, List.of());
    }

    /** The parsed values as a Bukkit view (defaults reference for repair). */
    public FileConfiguration toBukkit() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            cfg.set(e.getKey(), e.getValue());
        }
        return cfg;
    }

    /** Raw template lines (null when this instance was parsed from disk). */
    public List<String> rawLines() {
        return rawLines;
    }

    /** Copies the verbatim template to the target file (first-run generation). */
    public boolean writeTo(File target) {
        if (rawLines == null) return false;
        try {
            target.getParentFile().mkdirs();
            java.nio.file.Files.write(target.toPath(),
                    (String.join("\n", rawLines) + "\n").getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ========================================================================
    // WRITER (values + preserved comments)
    // ========================================================================

    /**
     * Writes {@code root} (plain nested maps from {@code sectionToMap}) as TOML,
     * re-attaching comments: prefer comments of {@code onDisk}, fall back to
     * {@code bundled} (covers freshly repaired keys).
     */
    public static void write(File target, Map<String, Object> root,
                             CommentedTomlTemplate onDisk, CommentedTomlTemplate bundled) {
        StringBuilder out = new StringBuilder();
        CommentedTomlTemplate c = onDisk != null ? onDisk : bundled;
        CommentedTomlTemplate fallback = onDisk != null ? bundled : null;

        // root scalars first (TOML: they must precede the first table header)
        List<Map.Entry<String, Object>> rootScalars = new ArrayList<>();
        List<Map.Entry<String, Object>> rootTables = new ArrayList<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            if (e.getValue() instanceof Map<?, ?>) rootTables.add(e);
            else rootScalars.add(e);
        }
        for (Map.Entry<String, Object> e : rootScalars) {
            appendKeyComments(out, c, fallback, "", e.getKey());
            out.append(e.getKey()).append(" = ").append(serialize(e.getValue())).append('\n');
        }
        for (Map.Entry<String, Object> e : rootTables) {
            out.append('\n');
            writeTable(out, e.getKey(), asMap(e.getValue()), c, fallback);
        }

        try {
            target.getParentFile().mkdirs();
            java.nio.file.Files.write(target.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // caller logs
        }
    }

    private static void writeTable(StringBuilder out, String path, Map<String, Object> node,
                                   CommentedTomlTemplate primary, CommentedTomlTemplate fallback) {
        List<String> header = primary != null ? primary.headerComments(path) : List.of();
        if (header.isEmpty() && fallback != null) header = fallback.headerComments(path);
        for (String hc : header) out.append(hc).append('\n');
        out.append('[').append(headerOf(path)).append("]\n");

        List<Map.Entry<String, Object>> scalars = new ArrayList<>();
        List<Map.Entry<String, Object>> subs = new ArrayList<>();
        for (Map.Entry<String, Object> e : node.entrySet()) {
            if (e.getValue() instanceof Map<?, ?>) subs.add(e);
            else scalars.add(e);
        }
        for (Map.Entry<String, Object> e : scalars) {
            appendKeyComments(out, primary, fallback, path, e.getKey());
            out.append(e.getKey()).append(" = ").append(serialize(e.getValue())).append('\n');
        }
        for (Map.Entry<String, Object> e : subs) {
            out.append('\n');
            writeTable(out, path + "." + e.getKey(), asMap(e.getValue()), primary, fallback);
        }
    }

    private static void appendKeyComments(StringBuilder out, CommentedTomlTemplate primary,
                                          CommentedTomlTemplate fallback, String table, String key) {
        List<String> lines = primary != null ? primary.keyComments(table, key) : List.of();
        if (lines.isEmpty() && fallback != null) lines = fallback.keyComments(table, key);
        for (String l : lines) out.append(l).append('\n');
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    private static String headerOf(String path) {
        String[] segs = path.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) sb.append('.');
            String seg = segs[i];
            if (BARE.matcher(seg).matches() && !DIGIT.matcher(seg).matches()
                    && !seg.equals("true") && !seg.equals("false")) {
                sb.append(seg);
            } else {
                sb.append('"').append(seg.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return sb.toString();
    }

    private static String unquoteHeader(String header) {
        // header is dotted; segments may be quoted
        StringBuilder path = new StringBuilder();
        for (String seg : header.split("\\.", -1)) {
            seg = seg.trim();
            if (seg.length() >= 2 && seg.startsWith("\"") && seg.endsWith("\"")) {
                seg = seg.substring(1, seg.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
            if (path.length() > 0) path.append('.');
            path.append(seg);
        }
        return path.toString();
    }

    private static String unquoteKey(String key) {
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            return key.substring(1, key.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return key;
    }

    /** Serializes any Bukkit-view value as inline TOML (toml4j-compatible). */
    static String serialize(Object v) {
        if (v == null) return "\"\"";
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Number n) {
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) return "0.0";
                String s = String.valueOf(d);
                return s.endsWith(".0") ? s.substring(0, s.length() - 2) + ".0" : s;
            }
            return String.valueOf(n);
        }
        if (v instanceof String s) return quote(s);
        if (v instanceof Character ch) return quote(String.valueOf(ch));
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{ ");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(String.valueOf(e.getKey())).append(" = ").append(serialize(e.getValue()));
            }
            return sb.append(first ? "}" : " }").toString();
        }
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(serialize(o));
            }
            return sb.append(']').toString();
        }
        return quote(String.valueOf(v));
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04X", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    /** Flattens nested maps into dotted-path values (order preserved). */
    private static void flatten(Map<String, Object> map, String prefix, Map<String, Object> target) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested && !nested.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                flatten(nestedMap, path, target);
            } else {
                target.put(path, v == null ? new ArrayList<>() : v);
            }
        }
    }
}
