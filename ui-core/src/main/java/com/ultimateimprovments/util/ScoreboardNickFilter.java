package com.ultimateimprovments.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Rule engine behind the "forbidden syntax" check of scoreboard nicknames.
 * <p>
 * The engine works on the <b>visible</b> text: the caller strips MiniMessage
 * tags (and therefore all colours/styles) before calling
 * {@link #check(String, Mode, List, List)}, so {@code <red>nick} is checked as
 * {@code nick} — styling is never a forbidden character.
 *
 * <h2>Wildcard rules</h2>
 * <ul>
 *   <li>{@code test} — a <b>standalone</b> word: blocks {@code my test nick} but
 *       not {@code testing} / {@code protest} (word boundaries are Unicode
 *       letters/digits);</li>
 *   <li>{@code test*} — the text starts with {@code test};</li>
 *   <li>{@code *test} — the text ends with {@code test};</li>
 *   <li>{@code *test*} — {@code test} appears anywhere, even inside a word;</li>
 *   <li>stars inside a rule ({@code t*st}) behave like glob {@code *} — any run
 *       of characters.</li>
 * </ul>
 * All wildcard rules are case-insensitive and Unicode-aware.
 *
 * <h2>Regex rules</h2>
 * Plain Java regular expressions, matched with {@link Pattern#find()} and
 * compiled case-insensitively with {@link Pattern#UNICODE_CASE}. A rule that
 * does not compile is <b>ignored</b> (an admin typo must never lock everybody
 * out) — neither section can block the text because of it.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li>{@link Mode#BLACKLIST} — blocked as soon as <b>any</b> rule matches;</li>
 *   <li>{@link Mode#WHITELIST} — allowed only when <b>every</b> rule matches
 *       (an empty rule set allows everything, as there is nothing to match).</li>
 * </ul>
 */
public final class ScoreboardNickFilter {

    /** Filter mode from the config ({@code scoreboard.nick.filter.mode}). */
    public enum Mode { BLACKLIST, WHITELIST }

    /** Rule sections, as named in the config and in the error message. */
    public static final String SECTION_WILDCARD = "wildcard";
    public static final String SECTION_REGEX = "regex";

    /** The rule that decided the outcome. */
    public record Hit(String section, String rule) {}

    /** Outcome of one check; {@code hit} is {@code null} when allowed. */
    public record Result(boolean allowed, Hit hit) {
        public static Result allow() {
            return new Result(true, null);
        }

        public static Result deny(String section, String rule) {
            return new Result(false, new Hit(section, rule));
        }
    }

    /** Compiled regex rules + wildcard rules (both glob-derived). */
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    /** Rules whose pattern does not compile — skipped by every check. */
    private static final Map<String, Boolean> INVALID_RULES = new ConcurrentHashMap<>();

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private ScoreboardNickFilter() {}

    /**
     * Checks the visible text against the configured rules.
     *
     * @param text      visible text (MiniMessage tags already stripped)
     * @param mode      blacklist or whitelist
     * @param wildcards wildcard rules ({@code *word}, {@code word*}, {@code *word*}, plain {@code word})
     * @param regexes   Java regex rules
     * @return allowed + the rule that decided (null when allowed)
     */
    public static Result check(String text, Mode mode, List<String> wildcards, List<String> regexes) {
        String subject = text == null ? "" : text;
        Mode effective = mode == null ? Mode.BLACKLIST : mode;
        boolean blacklist = effective == Mode.BLACKLIST;

        for (String rule : safe(wildcards)) {
            Pattern pattern = wildcardPattern(rule);
            if (pattern == null) continue;
            boolean matches = pattern.matcher(subject).find();
            if (blacklist && matches) return Result.deny(SECTION_WILDCARD, rule);
            if (!blacklist && !matches) return Result.deny(SECTION_WILDCARD, rule);
        }
        for (String rule : safe(regexes)) {
            Pattern pattern = regexPattern(rule);
            if (pattern == null) continue;
            boolean matches = pattern.matcher(subject).find();
            if (blacklist && matches) return Result.deny(SECTION_REGEX, rule);
            if (!blacklist && !matches) return Result.deny(SECTION_REGEX, rule);
        }
        return Result.allow();
    }

    /** True when the single rule matches the visible text (ignoring blank/invalid rules). */
    public static boolean ruleMatches(String section, String rule, String text) {
        String subject = text == null ? "" : text;
        if (SECTION_REGEX.equals(section)) {
            Pattern pattern = regexPattern(rule);
            return pattern != null && pattern.matcher(subject).find();
        }
        Pattern pattern = wildcardPattern(rule);
        return pattern != null && pattern.matcher(subject).find();
    }

    /** Compiles a wildcard rule; {@code null} for blank/unusable rules. */
    static Pattern wildcardPattern(String rawRule) {
        String rule = rawRule == null ? "" : rawRule.trim();
        if (rule.isEmpty()) return null;
        return PATTERN_CACHE.computeIfAbsent("w\u0000" + rule, key -> {
            try {
                return compileWildcard(rule);
            } catch (RuntimeException e) {
                INVALID_RULES.put(rule, Boolean.TRUE);
                return null;
            }
        });
    }

    /** Compiles a regex rule; {@code null} for blank/uncompilable rules. */
    static Pattern regexPattern(String rawRule) {
        String rule = rawRule == null ? "" : rawRule.trim();
        if (rule.isEmpty()) return null;
        return PATTERN_CACHE.computeIfAbsent("r\u0000" + rule, key -> {
            try {
                return Pattern.compile(rule, FLAGS);
            } catch (RuntimeException e) {
                INVALID_RULES.put(rule, Boolean.TRUE);
                return null;
            }
        });
    }

    /** True when the rule was already seen and could not be compiled. */
    public static boolean isInvalid(String rawRule) {
        return rawRule != null && INVALID_RULES.containsKey(rawRule.trim());
    }

    /**
     * Translates a wildcard rule into an anchored glob pattern.
     * <p>
     * A rule without any star is a standalone-word rule
     * ({@code (?<!\p{L}|\p{N})word(?!\p{L}|\p{N})}), everything else is a
     * classic glob where {@code *} matches any run of characters and the
     * missing leading/trailing star anchors the match at that end.
     */
    private static Pattern compileWildcard(String rule) {
        boolean leadingStar = rule.startsWith("*");
        boolean trailingStar = rule.endsWith("*");

        String core = rule;
        while (core.startsWith("*")) core = core.substring(1);
        while (core.endsWith("*")) core = core.substring(0, core.length() - 1);
        if (core.isEmpty()) return null; // "*" / "**" — matches everything, useless as a rule

        // A rule without any star is a standalone-word rule; a rule with an
        // inner star (t*st) is a glob anchored at both ends.
        if (rule.indexOf('*') < 0) {
            return Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(core) + "(?![\\p{L}\\p{N}])", FLAGS);
        }

        StringBuilder body = new StringBuilder();
        String[] parts = rule.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) body.append(".*");
            body.append(Pattern.quote(parts[i]));
        }
        if (!leadingStar) body.insert(0, '^');
        if (!trailingStar) body.append('$');
        return Pattern.compile(body.toString(), FLAGS);
    }

    private static List<String> safe(List<String> rules) {
        return rules == null ? List.of() : rules;
    }
}
