package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.FileLogger;
import com.ultimateimprovments.util.ConsoleLogger;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigGuideManager — manages the user guide EMBEDDED in config.yml.
 * <p>
 * Since v26.2, plugin-guide.txt is no longer stored as a separate file and is NOT
 * shipped as a JAR resource. Its content lives AT THE TOP of config.yml as YAML
 * comments between markers and is maintained manually (translations/edits are made
 * directly in the file):
 * <pre>
 * # === ULTIMATEIMPROVMENTS GUIDE BEGIN (auto-managed, don't edit between markers) ===
 * [several hundred lines of the plugin guide as comments prefixed with "# "]
 * # === ULTIMATEIMPROVMENTS GUIDE END ===
 * </pre>
 * <p>
 * On startup the plugin calls {@link #init(Main)}, which only removes the legacy
 * files from dataFolder ({@code plugin-guide.txt}/{@code plugin-guide.hash}) left by
 * old versions. Auto-embedding/updating the guide from the JAR resource is NO LONGER
 * performed — the guide is edited directly in config.yml.
 * <p>
 * Helper methods (sha256, reconstructRawGuideText, dedupeMetaBlocks, indexOfLine)
 * are kept in this class for the unit tests {@code ConfigGuideManagerTest}.
 * The only production entry point is {@link #init(Main)}.
 */
public class ConfigGuideManager {

    public static final String GUIDE_BEGIN_MARKER = "# === ULTIMATEIMPROVMENTS GUIDE BEGIN (auto-managed by ConfigGuideManager, do not edit between markers) ===";
    public static final String GUIDE_END_MARKER = "# === ULTIMATEIMPROVMENTS GUIDE END ===";
    /** "Don't edit" banner above the _meta block (at the very bottom of config.yml). */
    public static final String META_BANNER = "# === INTEGRITY META — DO NOT EDIT (auto-managed) ===";
    public static final String META_KEY = "_meta";
    public static final String META_HASH_KEY = "guide_hash";
    /** Name of the JAR resource that provides the current guide for embedding. */
    public static final String GUIDE_RESOURCE = "plugin-guide.txt";
    /** Name of the legacy file in dataFolder (migration from previous versions). */
    public static final String LEGACY_GUIDE_FILE = "plugin-guide.txt";
    public static final String LEGACY_HASH_FILE = "plugin-guide.hash";

    private ConfigGuideManager() {}

    /**
     * Initializes the guide: migrates old plugin-guide.txt/plugin-guide.hash out of dataFolder.
     * <p>
     * Since v26.2 the JAR resource {@code plugin-guide.txt} is NOT shipped — the guide
     * lives directly in config.yml (between markers) and is maintained manually, so
     * auto-embedding/updating from the JAR resource is no longer performed.
     */
    public static void init(Main plugin) {
        // Migrate legacy files out of dataFolder (if left by old versions)
        migrateLegacyFiles(plugin);
    }

    /**
     * Embeds/updates the embedded guide in config.yml between markers.
     * <p>
     * Logic:
     * <ul>
     *   <li>No markers → insert at the top of the file (right after the warnings/comments header).</li>
     *   <li>Markers present → copy the current range (raw text), compute SHA-256, compare it
     *       with {@code _meta.guide_hash}; if it differs — replace with the content from the
     *       JAR resource {@code plugin-guide.txt} and update the hash.</li>
     * </ul>
     */
    public static void ensureEmbeddedGuide(Main plugin, File configFile) {
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            String guideText = loadGuideTextFromJar(plugin);

            if (guideText == null) {
                ConsoleLogger.warn("[Guide] resource '" + GUIDE_RESOURCE
                        + "' not bundled — embedded guide cannot be updated.");
                return;
            }
            String guideHash = sha256(guideText);

            int beginIdx = indexOfLine(lines, GUIDE_BEGIN_MARKER);
            int endIdx = beginIdx >= 0 ? indexOfLine(lines, GUIDE_END_MARKER) : -1;

            String currentEmbeddedHash;
            if (beginIdx >= 0 && endIdx > beginIdx) {
                // The hash must be computed from the CANONICAL form (raw guide text without
                // "# " prefixes), otherwise any comment prefix would trigger a false
                // "needs update" on every startup. The conversion is done by the dedicated
                // package-private helper {@link #reconstructRawGuideText} — one shared logic
                // for the boot-time check and the tests; String.join("\n", ...) is critical
                // here: append('\n') per-line would add a trailing newline and the hash
                // would constantly differ.
                currentEmbeddedHash = sha256(reconstructRawGuideText(lines, beginIdx, endIdx));
            } else {
                currentEmbeddedHash = null;
            }

            if (currentEmbeddedHash == null) {
                // No embed → embed at the top of the file
                List<String> newLines = new ArrayList<>(lines.size() + 200);
                newLines.add(GUIDE_BEGIN_MARKER);
                for (String gl : guideText.split("\\R", -1)) {
                    if (gl.isEmpty()) {
                        newLines.add("#");
                    } else {
                        newLines.add("# " + gl);
                    }
                }
                newLines.add(GUIDE_END_MARKER);
                newLines.add(""); // blank separator line
                newLines.addAll(lines);
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Embedded plugin-guide into config.yml ("
                        + newLines.size() + " lines).");
            } else if (!guideHash.equals(currentEmbeddedHash)) {
                // Hash mismatch — replace the content between markers, keeping everything after END_MARKER
                List<String> newLines = new ArrayList<>(lines.size() + 200);
                // 1) prefix up to BEGIN_MARKER
                for (int i = 0; i < beginIdx; i++) newLines.add(lines.get(i));
                // 2) BEGIN + new guide content + END
                newLines.add(GUIDE_BEGIN_MARKER);
                for (String gl : guideText.split("\\R", -1)) {
                    if (gl.isEmpty()) {
                        newLines.add("#");
                    } else {
                        newLines.add("# " + gl);
                    }
                }
                newLines.add(GUIDE_END_MARKER);
                // 3) suffix after END_MARKER (old lines after endIdx)
                newLines.add(""); // blank line after the markers
                for (int i = endIdx + 1; i < lines.size(); i++) newLines.add(lines.get(i));
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Updated embedded plugin-guide in config.yml (was "
                        + (endIdx - beginIdx + 1) + " lines, now " + (newLines.size() - lines.size() + (endIdx - beginIdx + 1)) + ").");
            } else {
                ConsoleLogger.info("[Guide] Embedded plugin-guide is up-to-date (hash " + guideHash.substring(0, 12) + ").");
                return;
            }

            // Update/insert _meta: { guide_hash: ... } — via raw text,
            // to guarantee the block ENDS UP at the very bottom of the file
            // (snakeyaml HashMap order is not guaranteed).
            upsertMetaBlockAtEnd(configFile, guideHash);

            // After ANY write we must keep _meta last — other code may call
            // plugin.getConfig().save() (ConfigRepairManager / Migration) and snakeyaml
            // would reorder keys via HashMap. Clean DUPLICATES: keep ONE block,
            // the one at the very bottom (ours).
            dedupeMetaBlocks(configFile);

            // Reload the Bukkit config so the in-memory model (getConfig().getString,
            // getString("messages.*") etc.) reflects the fresh file.
            try {
                plugin.reloadConfig();
            } catch (Exception e) {
                ConsoleLogger.warn("[Guide] reloadConfig after file rewrite failed: " + e.getMessage());
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Guide] Failed to ensure embedded guide: " + e.getMessage());
            FileLogger.logError("Guide", "ensureEmbeddedGuide failed: " + e.getMessage(), null, e);
        }
    }

    /**
     * Migrates legacy files out of dataFolder into config.yml: if plugin-guide.txt
     * or plugin-guide.hash exist — deletes them (content will be replaced from the JAR
     * on {@link #ensureEmbeddedGuide}). Always safe to call — missing files are OK.
     */
    public static void migrateLegacyFiles(Main plugin) {
        File legacy = new File(plugin.getDataFolder(), LEGACY_GUIDE_FILE);
        File legacyHash = new File(plugin.getDataFolder(), LEGACY_HASH_FILE);
        if (legacy.exists()) {
            if (!legacy.delete()) {
                ConsoleLogger.warn("[Guide] Failed to delete legacy " + LEGACY_GUIDE_FILE);
            } else {
                ConsoleLogger.info("[Guide] Deleted legacy " + LEGACY_GUIDE_FILE);
            }
        }
        if (legacyHash.exists()) {
            if (!legacyHash.delete()) {
                ConsoleLogger.warn("[Guide] Failed to delete legacy " + LEGACY_HASH_FILE);
            } else {
                ConsoleLogger.info("[Guide] Deleted legacy " + LEGACY_HASH_FILE);
            }
        }
    }

    /**
     * Inserts/updates the {@code _meta: { guide_hash: ... }} block AT THE VERY BOTTOM
     * of config.yml with a "DO NOT EDIT" banner above it. Uses raw text manipulation,
     * not snakeyaml — key order in snakeyaml HashMap is NOT guaranteed.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Read the file line by line.</li>
     *   <li>Banner + existing _meta present — replace the old block with the new one in-place.</li>
     *   <li>Only a banner without _meta — append _meta after the banner.</li>
     *   <li>Nothing present — append banner + _meta at the very end of the file.</li>
     *   <li>Guarantee that NOTHING follows the _meta block (it is the last non-empty line).</li>
     * </ol>
     */
    private static void upsertMetaBlockAtEnd(File configFile, String hash) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
            int bannerIdx = indexOfLine(lines, META_BANNER);
            int metaIdx = indexOfLine(lines, "_meta:");

            if (bannerIdx >= 0 && metaIdx >= 0) {
                // Both present — replace the _meta content in place.
                int startMeta = metaIdx;
                int endMeta = startMeta;
                while (endMeta + 1 < lines.size()) {
                    String nxt = lines.get(endMeta + 1);
                    if (nxt.startsWith("  ") || nxt.startsWith("\t")) {
                        endMeta++;
                    } else {
                        break;
                    }
                }
                List<String> newMeta = new ArrayList<>();
                newMeta.add("_meta:");
                newMeta.add("  guide_hash: \"" + hash + "\"");
                List<String> merged = new ArrayList<>(lines.size());
                merged.addAll(lines.subList(0, bannerIdx + 1));
                // Always insert a blank line between the banner and _meta for readability
                merged.add("");
                merged.addAll(newMeta);
                if (endMeta + 1 < lines.size()) {
                    // there is a "tail" after _meta — keep a blank separator line
                    if (!lines.get(endMeta + 1).isEmpty()) merged.add("");
                    merged.addAll(lines.subList(endMeta + 1, lines.size()));
                }
                lines = merged;
                Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Updated _meta.guide_hash in-place (hash " + hash.substring(0, 12) + ").");
            } else if (bannerIdx >= 0) {
                // Banner present, no _meta — append _meta after the banner
                List<String> newLines = new ArrayList<>(lines);
                newLines.add(bannerIdx + 1, ""); // blank line
                newLines.add(bannerIdx + 2, "_meta:");
                newLines.add(bannerIdx + 3, "  guide_hash: \"" + hash + "\"");
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Inserted _meta after existing banner.");
            } else {
                // None at all — append banner + _meta at the very end of the file
                List<String> newLines = new ArrayList<>(lines);
                if (!newLines.isEmpty() && !newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.add("");
                }
                newLines.add(META_BANNER);
                newLines.add("");
                newLines.add("_meta:");
                newLines.add("  guide_hash: \"" + hash + "\"");
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Appended _meta block at end of config.yml.");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Guide] Failed to upsert _meta block: " + e.getMessage());
            FileLogger.logError("Guide", "upsertMetaBlockAtEnd failed: " + e.getMessage(), null, e);
        }
    }

    // ============================================================
    // Utilities
    // ============================================================

    /** Reads the text of the JAR resource {@code plugin-guide.txt} or null. */
    private static String loadGuideTextFromJar(Main plugin) {
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource(GUIDE_RESOURCE), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** SHA-256 hex string. */
    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b & 0xFF));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /**
     * Extracts the canonical form of the source plugin-guide.txt from
     * {@code lines[beginIdx+1 .. endIdx-1]} (the content between
     * {@link #GUIDE_BEGIN_MARKER} and {@link #GUIDE_END_MARKER}): strips the
     * {@code "# "} or {@code "#"} prefix from each line and joins the result with
     * {@code "\n"} (no trailing newline). This is the strict inverse of the WRITE
     * path in ensureEmbeddedGuide, and together they guarantee that the SHA-256 of
     * the embedded range matches the SHA-256 of the raw guide text from the JAR —
     * otherwise the guide would be re-embedded on every startup.
     * <p>
     * Known limitation: the prefix is stripped only ONCE. If the source
     * plugin-guide.txt contains a markdown-style «{@code ###SubSubHeader}» (triple
     * hash at the start of a line), after embedding and the inverse strip you get
     * «{@code ##SubSubHeader}» — one hash is lost, the hash does not match, and the
     * guide gets re-embedded. In practice plugin-guide.txt does not use {@code ###}
     * (only {@code ##} sections), but if needed — switch to the «{@code #>}»
     * prefix (a YAML comment that does not conflict with markdown).
     * <p>
     * Package-private (not private) so that tests can call it directly
     * (reflection is not used because both classes are in com.ultimateimprovments.config).
     */
    static String reconstructRawGuideText(List<String> lines, int beginIdx, int endIdx) {
        // Without a capacity hint: an empty range (beginIdx+1 >= endIdx) yields an empty String
        // and does not throw IllegalArgumentException. ArrayList grows from the default 10
        // through ~9 doublings up to ~5866 (typical guide — ~2940 lines) — cheaper than
        // maintaining a capacity formula.
        List<String> stripped = new ArrayList<>();
        for (int i = beginIdx + 1; i < endIdx; i++) {
            String line = lines.get(i);
            if (line.startsWith("# ")) {
                stripped.add(line.substring(2));
            } else if (line.equals("#")) {
                stripped.add("");
            } else if (line.startsWith("#")) {
                stripped.add(line.substring(1));
            } else {
                stripped.add(line);
            }
        }
        return String.join("\n", stripped);
    }

    /**
     * Removes ALL occurrences of the {@code _meta:} block except the last one (the
     * one {@link #upsertMetaBlockAtEnd} just wrote at the very bottom). Protection
     * against the case when someone externally called {@code plugin.getConfig().save()}
     * and the snakeyaml HashMap order reshuffled the keys — after such a reshuffle a
     * duplicate _meta: block can appear in the middle of the file.
     */
    private static void dedupeMetaBlocks(File configFile) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
            int lastMeta = -1;
            int firstMeta = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("_meta:")) {
                    if (firstMeta < 0) firstMeta = i;
                    lastMeta = i;
                }
            }
            if (firstMeta < 0 || firstMeta == lastMeta) {
                return; // none or one — OK
            }
            // Remove all _meta: occurrences except the last one (bottom-up so indices stay valid)
            // + remove the _meta sub-keys (lines with 2-space indent)
            List<Integer> toRemove = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (i == lastMeta) continue;
                if (lines.get(i).trim().equals("_meta:")) {
                    toRemove.add(i);
                    // Remove ALL sub-keys right after (indented) — while there is a 2-space indent
                    int j = i + 1;
                    while (j < lines.size() && (lines.get(j).startsWith("  ") || lines.get(j).startsWith("\t"))) {
                        toRemove.add(j);
                        j++;
                    }
                }
            }
            // Sort in descending order and remove (so indices stay valid)
            toRemove.sort((a, b) -> b - a);
            for (int idx : toRemove) {
                lines.remove(idx);
            }
            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            ConsoleLogger.info("[Guide] Removed " + toRemove.size() + " duplicate _meta line(s) — kept last block only.");
        } catch (Exception e) {
            ConsoleLogger.warn("[Guide] dedupeMetaBlocks failed: " + e.getMessage());
        }
    }

    /**
     * Finds a line in the list (normalized: trim + strip trailing CR for the CRLF case).
     * This guards against surprises when someone saved the file with CRLF and the constant uses LF.
     */
    private static int indexOfLine(List<String> lines, String marker) {
        String normMarker = marker.endsWith("\r") ? marker : marker.trim();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // strip the trailing CR if present (CRLF vs LF)
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.equals(normMarker)) return i;
        }
        return -1;
    }

    /** Utility for other places — returns the current guide hash (for logs). */
    public static String currentGuideHash(Main plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return null;
        return plugin.getConfig().getString(META_KEY + "." + META_HASH_KEY, null);
    }
}
