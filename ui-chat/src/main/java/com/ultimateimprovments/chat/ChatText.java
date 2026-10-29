package com.ultimateimprovments.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hardening and analysis of player-supplied chat text.
 *
 * <h2>Line breaks</h2>
 * Minecraft chat renders a component's child lines one under another. If a
 * player manages to smuggle a line break into the <em>message</em> part of a
 * chat format, every following line is rendered WITHOUT the chat prefix and
 * looks like a server/system line — e.g. typing
 * {@code <newline>[Server] Restart in 5s} (possible while
 * {@code chat.player_minimessage: true}) forges a fake "[Server]" line.
 * Convention: player text is never rendered as one blob; it is split into
 * single-line pieces here and {@link ChatManager} renders EVERY piece through
 * the full channel format.
 *
 * <h2>Abuse metrics</h2>
 * {@link #charsToKeep}, {@link #capsPercent} and {@link #randomnessPercent}
 * implement the configurable chat limits (total length, capital letters,
 * "random character soup"). They are pure functions on strings so they can be
 * unit-tested without a server.
 */
public final class ChatText {

    /** Default cap for the number of rendered lines of a single message. */
    public static final int DEFAULT_MAX_LINES = 3;

    /** Default cap for the total number of characters of a single message. */
    public static final int DEFAULT_MAX_CHARS = 512;

    /** Suffix appended to the last line when the message was cut by a limit. */
    public static final String TRUNCATED_SUFFIX = "\u2026";

    /**
     * Minimum token length (code points) before it is judged for "randomness":
     * short tokens are statistically meaningless (2-3 letters already look
     * "unique"), so spam-like soup is only detected on longer tokens.
     */
    public static final int MIN_TOKEN_FOR_RANDOM = 8;

    /**
     * Symbol budget used to normalize entropy. Chat text is written with a
     * printable alphabet far below the full Unicode set; 64 is the size of the
     * "typical" machine-generated alphabet (letters + digits + a few symbols)
     * and keeps the percentage comparable between messages of different length.
     */
    public static final int RANDOM_ALPHABET = 64;

    /** Minimum entropy share (0..1) for a token to be considered random-like. */
    public static final double RANDOM_MIN_ENTROPY_SHARE = 0.70;

    /** A run of this many consonants in a row is untypable for a real word. */
    public static final int RANDOM_CONSONANT_RUN = 7;

    /**
     * Letters and digits must be interleaved at least this many times: a digit
     * group at the edge of a token ({@code Steve123}, {@code Dave2024}) is a
     * nickname, not machine soup ({@code asdk123zxcv}).
     */
    public static final int RANDOM_DIGIT_TRANSITIONS = 2;

    /**
     * Raw line separators. Client-typed chat cannot contain them, but chat text
     * can arrive from placeholders, dialogs, plugins or the console, so every
     * Unicode line terminator is treated as a break.
     */
    private static final Pattern LINE_SPLIT =
            Pattern.compile("\r\n|[\r\n\u000B\u000C\u0085\u2028\u2029]");

    /**
     * MiniMessage tags that the client expands into a real line break.
     * {@code <newline>} is the standard one; {@code <br>} variants are accepted
     * because plugins/configs use them as an alias. Matched case-insensitively.
     */
    private static final Pattern NEWLINE_TAG =
            Pattern.compile("(?i)<\\s*/?\\s*(?:newline|br)\\s*/?\\s*>");

    /**
     * Number of code points (one emoji = 1, one Cyrillic letter = 1).
     * Used for the «too short to judge» gate of the CAPS check.
     */
    public static int charCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.codePointCount(0, text.length());
    }

    /**
     * Returns true when the text contains at least one Cyrillic letter.
     * Backs the optional «no Cyrillic» chat mode (chat.limits.block_cyrillic),
     * which is off by default.
     */
    public static boolean containsCyrillic(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC) return true;
        }
        return false;
    }

    /**
     * Returns true when the text contains at least one non-ASCII character
     * (code point above U+007F): Cyrillic, emoji, exotic symbols, invisible
     * space characters, etc. Backs the optional «ASCII only» chat mode
     * (chat.limits.block_unicode), which is off by default and is stricter than
     * block_cyrillic: it rejects every Unicode character, not only Cyrillic.
     */
    public static boolean containsNonAscii(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp > 0x7F) return true;
        }
        return false;
    }

    /**
     * Similarity of two messages in percent (0..100), backing the duplicate
     * message guard (chat.limits.duplicate_percent).
     * <p>
     * Both texts are normalized first (trimmed, inner whitespace collapsed,
     * case-folded), so {@code "Привет  мир"} and {@code "привет мир"} count as
     * identical. The percentage is Levenshtein-based:
     * {@code 100 * (1 - distance / max(length))}, computed on code points, so
     * one changed letter in a 20-character message scores 95%. {@code 100} means
     * a normalized exact match; two empty texts are also 100% similar, while an
     * empty text against a non-empty one is 0%.
     *
     * @param first  the previous message (may be null)
     * @param second the new message (may be null)
     * @return 0..100 — how much the messages overlap
     */
    public static int similarityPercent(String first, String second) {
        String a = normalizeForCompare(first);
        String b = normalizeForCompare(second);
        if (a.isEmpty() && b.isEmpty()) return 100;
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 100;

        int[] ca = a.codePoints().toArray();
        int[] cb = b.codePoints().toArray();
        int max = Math.max(ca.length, cb.length);
        if (max == 0) return 100;

        // Levenshtein similarity: 1 edit in 20 chars = 95%, fully different
        // strings of the same length = 0%.
        int distance = levenshtein(ca, cb);
        int similarity = (int) Math.round(100.0 * (max - distance) / max);
        return Math.max(0, Math.min(100, similarity));
    }

    /** Trim, collapse inner whitespace and case-fold: "  Привет   МИР " → "привет мир". */
    private static String normalizeForCompare(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    /** Edit distance between two code point sequences (two-row DP, O(min) memory). */
    private static int levenshtein(int[] a, int[] b) {
        if (a.length > b.length) {
            int[] swap = a;
            a = b;
            b = swap;
        }
        int[] previous = new int[a.length + 1];
        int[] current = new int[a.length + 1];
        for (int i = 0; i <= a.length; i++) previous[i] = i;
        for (int j = 1; j <= b.length; j++) {
            current[0] = j;
            for (int i = 1; i <= a.length; i++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                current[i] = Math.min(
                        Math.min(current[i - 1] + 1, previous[i] + 1),
                        previous[i - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[a.length];
    }

    /** Legacy section-sign colour codes ({@code §a}, {@code §l}, ...), removed with their letter. */
    private static final Pattern LEGACY_CODES = Pattern.compile("\u00A7[0-9a-fk-orx]");

    /** Whitespace used to split a message into tokens (all Unicode whitespace). */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ChatText() {}

    // ========================= LINE SPLITTING =========================

    /**
     * Splits a message into the lines that must be rendered individually.
     *
     * @param text        the message text (placeholders already resolved)
     * @param miniMessage {@code true} when the player may use MiniMessage, so
     *                    newline-producing tags also count as line breaks
     * @param maxLines    hard cap of returned lines ({@code <= 0} → default);
     *                    when the message is longer, the last kept line gets an
     *                    ellipsis marker
     * @return the lines to render; never empty (a blank message yields one empty
     *         line so the chat format itself is still shown)
     */
    public static List<String> splitLines(String text, boolean miniMessage, int maxLines) {
        if (text == null || text.isEmpty()) return List.of("");

        String normalized = miniMessage
                ? NEWLINE_TAG.matcher(text).replaceAll("\n")
                : text;

        List<String> lines = new ArrayList<>();
        for (String part : LINE_SPLIT.split(normalized, -1)) {
            String line = part.strip();
            if (!line.isEmpty()) lines.add(line);
        }
        if (lines.isEmpty()) return List.of("");

        int limit = maxLines > 0 ? maxLines : DEFAULT_MAX_LINES;
        if (lines.size() > limit) {
            List<String> capped = new ArrayList<>(lines.subList(0, limit));
            int last = limit - 1;
            capped.set(last, capped.get(last) + TRUNCATED_SUFFIX);
            return capped;
        }
        return lines;
    }

    // ========================= LENGTH LIMIT =========================

    /**
     * Cuts the message to the configured total character budget (counted in
     * code points, so one emoji is one character). The marker itself is counted,
     * therefore {@code charsToKeep} never returns a string longer than
     * {@code maxChars}.
     *
     * @param text     the message text
     * @param maxChars total character budget ({@code <= 0} → no limit)
     * @return the text itself when it fits, otherwise a cut ending with an
     *         ellipsis
     */
    public static String charsToKeep(String text, int maxChars) {
        if (text == null || text.isEmpty() || maxChars <= 0) return text;
        int count = text.codePointCount(0, text.length());
        if (count <= maxChars) return text;
        int keep = Math.max(0, maxChars - TRUNCATED_SUFFIX.length());
        int end = text.offsetByCodePoints(0, Math.min(keep, count));
        return text.substring(0, end) + TRUNCATED_SUFFIX;
    }

    // ========================= CAPS =========================

    /** Number of letters (any script) in the text. */
    public static int letterCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int letters = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) letters++;
        }
        return letters;
    }

    /**
     * Percentage of capital letters among all letters (0..100).
     * <p>
     * Only letters are counted, so digits, spaces and punctuation never dilute
     * the number. Returns {@code 0} when the message has no letters.
     */
    public static int capsPercent(String text) {
        if (text == null || text.isEmpty()) return 0;
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            letters++;
            if (Character.isUpperCase(cp)) upper++;
        }
        if (letters == 0) return 0;
        return (int) Math.round(100.0 * upper / letters);
    }

    // ========================= RANDOMNESS ("entropy") =========================

    /**
     * Percentage (0..100) of the message's characters that belong to tokens
     * looking like a random character soup.
     * <p>
     * Why not the plain Shannon entropy of the whole message: a chat message is
     * short (tens of characters), and on such samples the per-character entropy
     * of natural text ("координаты") and of a machine-generated token
     * ("asdkjghqwe") is almost identical — long distinct-letter words are
     * statistically "unique" too. So a token is only reported as random when
     * its entropy is near the maximum for its length <b>and</b> it shows a
     * structural sign of being machine-made:
     * <ul>
     *   <li>a run of {@value #RANDOM_CONSONANT_RUN}+ consonants in a row
     *       (nobody types "asdkjgh");</li>
     *   <li>letters and digits interleaved inside one token
     *       ({@code asdk123zxcv}); a digit group at the edge is a nickname
     *       ({@code Steve123}) and is not counted;</li>
     *   <li>a letter-only token without a single vowel.</li>
     * </ul>
     * URLs are skipped: a link legitimately mixes letters, digits and symbols.
     * <p>
     * The result is a share of the message, not a flag, so "купи алмазы тут
     * asdkjghqwe123" is caught (68% random) while a normal sentence with a
     * coordinate list is not (0%).
     *
     * @param text the message text
     * @return 0..100 — percentage of characters inside random-looking tokens
     */
    public static int randomnessPercent(String text) {
        if (text == null || text.isEmpty()) return 0;

        int randomChars = 0;
        int totalChars = 0;

        for (String token : WHITESPACE.split(text.strip())) {
            if (token.isEmpty()) continue;
            int tokenChars = token.codePointCount(0, token.length());
            totalChars += tokenChars;

            if (tokenChars < MIN_TOKEN_FOR_RANDOM) continue;
            if (isLink(token)) continue;
            if (looksRandom(token)) randomChars += tokenChars;
        }

        if (totalChars == 0) return 0;
        return (int) Math.round(100.0 * randomChars / totalChars);
    }

    /** Links legitimately mix letters, digits and symbols — never judged as soup. */
    private static boolean isLink(String token) {
        String lower = token.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("://") || lower.startsWith("www.");
    }

    /** One token: near-maximal entropy for its length plus a machine-made structure sign. */
    private static boolean looksRandom(String token) {
        int[] cps = token.codePoints().toArray();
        int n = cps.length;

        double entropyShare = shannonEntropy(cps) / log2(Math.min(RANDOM_ALPHABET, n));
        if (entropyShare < RANDOM_MIN_ENTROPY_SHARE) return false;

        int letters = 0;
        int digits = 0;
        int vowels = 0;
        for (int cp : cps) {
            if (Character.isDigit(cp)) {
                digits++;
            } else if (Character.isLetter(cp)) {
                letters++;
                if (isVowel(cp)) vowels++;
            }
        }

        // No vowels at all in a longer letter-only token.
        if (letters >= 8 && digits == 0 && vowels == 0) return true;

        // Un-typable consonant cluster.
        if (longestConsonantRun(cps) >= RANDOM_CONSONANT_RUN) return true;

        // Letters and digits interleaved (a nick like "Steve123" has them at
        // the edge only, so it does not qualify).
        return digitTransitions(cps) >= RANDOM_DIGIT_TRANSITIONS;
    }

    /**
     * Number of letter↔digit switches inside a token. "Steve123" → 1 (edge),
     * "asdk123zxcv" → 2, "a1b2c3" → 5.
     */
    private static int digitTransitions(int[] cps) {
        int transitions = 0;
        int previous = -1;
        for (int cp : cps) {
            if (!Character.isLetterOrDigit(cp)) continue;
            int kind = Character.isDigit(cp) ? 1 : 0;
            if (previous != -1 && kind != previous) transitions++;
            previous = kind;
        }
        return transitions;
    }

    /** Shannon entropy in bits per code point. */
    private static double shannonEntropy(int[] cps) {
        int n = cps.length;
        if (n == 0) return 0;
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int cp : cps) counts.merge(cp, 1, Integer::sum);
        double sum = 0;
        for (int count : counts.values()) {
            double p = (double) count / n;
            sum -= p * log2(p);
        }
        return sum;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    /** Longest run of consonants in Latin/Cyrillic (digits and symbols break the run). */
    private static int longestConsonantRun(int[] cps) {
        int best = 0;
        int current = 0;
        for (int cp : cps) {
            if (Character.isLetter(cp) && !isVowel(cp)) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 0;
            }
        }
        return best;
    }

    /** Vowels of the Latin and Cyrillic alphabets (y/й count as consonants). */
    private static boolean isVowel(int cp) {
        return switch (Character.toLowerCase(cp)) {
            case 'a', 'e', 'i', 'o', 'u',
                 'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я' -> true;
            default -> false;
        };
    }

    // ========================= ESCAPING =========================

    /**
     * Escapes MiniMessage syntax so player text renders literally.
     * <p>
     * Backslashes are doubled first: MiniMessage treats a backslash as an escape
     * character, so an un-doubled player-supplied {@code \\} could cancel the
     * escape of the following {@code <} and re-enable tag parsing.
     * Legacy {@code §} codes are dropped (MiniMessage does not understand them
     * and they bypass the tag escaping).
     *
     * @param text the raw player text
     * @return text that renders literally in MiniMessage
     */
    public static String escapeMiniMessage(String text) {
        if (text == null || text.isEmpty()) return "";
        String withoutLegacy = LEGACY_CODES.matcher(text).replaceAll("");
        return withoutLegacy.replace("\\", "\\\\")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("\u00A7", "");
    }
}
