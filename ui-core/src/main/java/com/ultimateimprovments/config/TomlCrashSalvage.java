package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 🧯 TomlCrashSalvage — TOML counterpart of {@link ConfigCrashSalvage}.
 * <p>
 * When {@code config.toml} fails to parse, the offending line(s) are commented
 * out and the parse is retried until the file is stable (bounded by
 * {@link #MAX_ROUNDS}). The commented subtree falls back to defaults from the
 * bundled reference — the user's file is never deleted or replaced.
 * <p>
 * TOML specifics vs the YAML salvage:
 * <ul>
 *   <li>tolem4j reports errors with a {@code line X:Y} context, matched by the
 *       same {@code line N} regex as before;</li>
 *   <li>after commenting a line, leftover broken table headers
 *       ({@code [section]}) are harmless — they simply stop contributing keys;</li>
 *   <li>commenting a {@code key = value} line may leave an orphan continuation
 *       (e.g. a multi-line array) — the next salvage round comments those too.</li>
 * </ul>
 */
public final class TomlCrashSalvage {

    private static final int MAX_ROUNDS = 1000;
    private static final java.util.regex.Pattern LINE_IN_MESSAGE =
            java.util.regex.Pattern.compile("(?i)line\\s+(\\d+)");

    private TomlCrashSalvage() {}

    /** Result of a salvage operation. */
    public static final class Result {
        /** true — the file now parses (problem lines commented out). */
        public final boolean success;
        /** Commented line numbers (0-based, same convention as ConfigCrashSalvage). */
        public final List<Integer> commentedLines;
        /** Human-readable result description. */
        public final String message;

        Result(boolean success, List<Integer> commentedLines, String message) {
            this.success = success;
            this.commentedLines = commentedLines;
            this.message = message;
        }
    }

    /**
     * Salvages {@code config.toml} by commenting out lines that break parsing.
     * Never deletes the file.
     *
     * @return true if the file now parses (or was already valid)
     */
    public static boolean salvageFile(File tomlFile, Consumer<String> log) {
        if (!tomlFile.exists()) {
            log.accept("config.toml does not exist (first run?) — nothing to salvage");
            return false;
        }

        Result result = salvage(tomlFile);

        for (int line : result.commentedLines) {
            log.accept("⚠ Commented broken line " + (line + 1)
                    + " — the default value from the bundled reference will be used");
        }
        log.accept(result.success ? "✔ " + result.message : "✗ " + result.message);
        return result.success;
    }

    /** Pure salvage logic (JUnit-testable). */
    public static Result salvage(File tomlFile) {
        List<Integer> commented = new ArrayList<>();
        String message;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (parses(tomlFile)) {
                message = commented.isEmpty()
                        ? "config.toml is healthy"
                        : "config.toml stabilized after commenting " + commented.size() + " line(s)";
                return new Result(true, commented, message);
            }

            String error = lastParseError(tomlFile);
            Integer line = extractLine(error);

            if (line == null || !commentOutLine(tomlFile, line)) {
                message = "could not locate a fixable parse error"
                        + (error != null ? ": " + firstLine(error) : "");
                return new Result(false, commented, message);
            }
            commented.add(line - 1); // 0-based, matching ConfigCrashSalvage convention
        }

        return new Result(false, commented, "salvage did not converge in " + MAX_ROUNDS + " rounds");
    }

    // ========================================================================
    // INTERNALS
    // ========================================================================

    private static boolean parses(File file) {
        return lastParseError(file) == null;
    }

    /** @return null if the file parses, otherwise the exception message. */
    private static String lastParseError(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            new Toml().read(new InputStreamReader(in, StandardCharsets.UTF_8));
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /** Extracts the 1-based line number from a toml4j error message. */
    private static Integer extractLine(String errorMessage) {
        if (errorMessage == null) return null;
        java.util.regex.Matcher m = LINE_IN_MESSAGE.matcher(errorMessage);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /**
     * Comments out (prefix {@code # }) the given 1-based line and rewrites the file.
     * @return false if the line is out of bounds or already commented
     */
    private static boolean commentOutLine(File file, int lineNumber) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lineNumber < 1 || lineNumber > lines.size()) return false;
            String line = lines.get(lineNumber - 1);
            if (line.trim().startsWith("#")) return false;
            lines.set(lineNumber - 1, "# " + line);
            java.nio.file.Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }
}
