package com.ultimateimprovments.config;

import com.ultimateimprovments.util.ConsoleLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Detects and removes duplicate root-level keys in YAML files.
 * <p>
 * SnakeYAML (used by Bukkit) when loading a file with duplicate keys
 * takes the LAST value, ignoring all previous ones. This means the user's
 * edits in the first section are lost if a duplicate exists.
 * <p>
 * Algorithm:
 * <ol>
 *   <li>Scans the file line by line</li>
 *   <li>Finds all root-level keys (lines without indentation matching {@code key:})</li>
 *   <li>If a key repeats — removes the LAST duplicate entirely
 *       (including all its sub-keys up to the next root-level key)</li>
 *   <li>Saves the cleaned file to disk</li>
 * </ol>
 */
public class YamlDuplicateCleaner {

    private YamlDuplicateCleaner() {}

    /**
     * Scans a YAML file for duplicate root-level keys and removes all
     * except the FIRST occurrence.
     *
     * @param file     the file to process
     * @param fileName the display name of the file in logs
     * @return true if duplicates were found and removed
     */
    public static boolean cleanDuplicates(File file, String fileName) {
        if (!file.exists()) return false;

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<Section> sections = findRootSections(lines);

            // Find duplicates: group by key, keep the first, remove the rest
            Map<String, Section> firstOccurrence = new LinkedHashMap<>();
            Set<Integer> linesToRemove = new HashSet<>();
            int duplicateCount = 0;

            for (Section section : sections) {
                if (firstOccurrence.containsKey(section.key)) {
                    duplicateCount++;
                    ConsoleLogger.warn("[YamlCleaner] ⚠ Removing duplicate key '" + section.key
                            + "' in " + fileName + " (line " + (section.start + 1) + ")");
                    for (int i = section.start; i <= section.end; i++) {
                        linesToRemove.add(i);
                    }
                } else {
                    firstOccurrence.put(section.key, section);
                }
            }

            if (linesToRemove.isEmpty()) return false;

            // Build the cleaned file (skip the removed lines)
            List<String> cleaned = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                if (!linesToRemove.contains(i)) {
                    cleaned.add(lines.get(i));
                }
            }

            Files.write(file.toPath(), cleaned);

            ConsoleLogger.warn("[YamlCleaner] ✔ Removed " + duplicateCount + " duplicate section(s) ("
                    + linesToRemove.size() + " lines) from " + fileName);
            return true;

        } catch (IOException e) {
            ConsoleLogger.warn("[YamlCleaner] ✗ Failed to clean duplicates in " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds all root-level sections in a YAML file.
     * <p>
     * Section = a line with a root key + all lines up to the next root key (or end of file).
     */
    private static List<Section> findRootSections(List<String> lines) {
        // Find the indices of all root-level keys
        List<Integer> keyLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isRootKey(lines.get(i))) {
                keyLines.add(i);
            }
        }

        if (keyLines.isEmpty()) return Collections.emptyList();

        // Build sections: from a key to the next key (or end of file)
        List<Section> sections = new ArrayList<>(keyLines.size());
        for (int k = 0; k < keyLines.size(); k++) {
            int start = keyLines.get(k);
            int end = (k + 1 < keyLines.size()) ? keyLines.get(k + 1) - 1 : lines.size() - 1;
            String key = extractKey(lines.get(start));
            sections.add(new Section(key, start, end));
        }

        return sections;
    }

    /**
     * Checks whether a line is a root-level YAML key:
     * <ul>
     *   <li>No leading spaces/tabs</li>
     *   <li>Not a comment</li>
     *   <li>Not a list item ({@code - })</li>
     *   <li>Matches the {@code [word_chars]:} pattern</li>
     * </ul>
     */
    private static boolean isRootKey(String line) {
        if (line == null || line.isEmpty()) return false;
        // Must not start with a space or tab
        char first = line.charAt(0);
        if (first == ' ' || first == '\t') return false;
        // Not a comment
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) return false;
        // Not a root-level list item
        if (trimmed.startsWith("- ")) return false;
        // Must be a YAML key: letters/digits/underscore/hyphen, then a colon
        return trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_-]*:.*");
    }

    /**
     * Extracts the key name from a line of the form {@code key: value}.
     */
    private static String extractKey(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon);
        }
        return trimmed;
    }

    /**
     * Inner class describing a root-level key section in a YAML file.
     */
    private static class Section {
        final String key;
        final int start;
        final int end;

        Section(String key, int start, int end) {
            this.key = key;
            this.start = start;
            this.end = end;
        }
    }
}
