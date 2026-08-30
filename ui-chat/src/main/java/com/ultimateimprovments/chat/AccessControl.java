package com.ultimateimprovments.chat;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Access control for chat messages or channel commands. Replaces the old flat
 * blacklist with a flexible unit engine.
 * <pre>
 * access_control:
 *   enabled: true
 *   units:
 *     1:
 *       enabled: true
 *       mode: &quot;blacklist&quot;      # or &quot;whitelist&quot;
 *       wildcards: { 1: &quot;*...*&quot; }
 *       regexes:   { 1: &quot;regex&quot; }
 *       condition: &quot;all&quot;        # single-X | X-and-Y-... | X-to-Y | all
 *       custom_deny_msg:
 *         enabled: false
 *         message: &quot;custom deny message&quot;
 * </pre>
 *
 * <p><b>Deciding</b> — every pattern carries one combined number space per unit
 * (wildcards first, then regexes). A unit "fires" when all of its required
 * patterns (per {@code condition}) match. <b>Blacklist has precedence over
 * whitelist</b>: if any blacklist unit fires, the command/message is denied no
 * matter what a whitelist unit says; otherwise a firing whitelist unit allows it.
 * If nothing fires at all, the input is allowed unless at least one whitelist
 * unit exists (then it is denied — a whitelist is an allow-list).
 */
public final class AccessControl {

    /** whitelist = this unit's patterns are the allowed set; blacklist = forbidden set. */
    public enum Mode { WHITELIST, BLACKLIST }

    /** An immutable outcome of {@link #decide(String)}. */
    public static final class Result {
        private static final Result ALLOW = new Result(true, false, null, null);
        private static final Result NOT_ALLOWED = new Result(false, false, null, null);

        private final boolean allowed;
        private final boolean deniedByBlacklist;
        private final String forbidden;
        private final String customDenyMessage;

        private Result(boolean allowed, boolean deniedByBlacklist, String forbidden, String customDenyMessage) {
            this.allowed = allowed;
            this.deniedByBlacklist = deniedByBlacklist;
            this.forbidden = forbidden;
            this.customDenyMessage = customDenyMessage;
        }

        private static Result allow() { return ALLOW; }
        private static Result notAllowed() { return NOT_ALLOWED; }
        private static Result denyByBlacklist(String forbidden, String customDenyMessage) {
            return new Result(false, true, forbidden, customDenyMessage);
        }

        /** True if the input may pass. */
        public boolean isAllowed() { return allowed; }
        /** True if denial came from a blacklist match (has a highlightable fragment). */
        public boolean isDeniedByBlacklist() { return deniedByBlacklist; }
        /** The matched fragment shown in the block message (blacklist denial only). */
        public String forbidden() { return forbidden; }
        /** Optional per-unit custom deny message (null/blank if none configured). */
        public String customDenyMessage() { return customDenyMessage; }
    }

    private static final AccessControl DISABLED = new AccessControl(false, List.of(), false);

    private final boolean enabled;
    private final List<Unit> units;
    private final boolean hasWhitelist;

    private AccessControl(boolean enabled, List<Unit> units, boolean hasWhitelist) {
        this.enabled = enabled;
        this.units = units;
        this.hasWhitelist = hasWhitelist;
    }

    /** An always-allowing control (used before config is loaded). */
    public static AccessControl disabled() { return DISABLED; }

    /**
     * Loads an access control from {@code path + ".enabled"} and {@code path + ".units"}.
     */
    public static AccessControl load(FileConfiguration cfg, String path) {
        boolean enabled = cfg.getBoolean(path + ".enabled", true);
        List<Unit> units = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection(path + ".units");
        if (sec != null) {
            for (String key : sortedNumeric(sec.getKeys(false))) {
                ConfigurationSection us = sec.getConfigurationSection(key);
                if (us == null) continue;

                boolean unitEnabled = us.getBoolean("enabled", true);
                String modeStr = us.getString("mode", "blacklist");
                Mode mode = "whitelist".equalsIgnoreCase(modeStr) ? Mode.WHITELIST : Mode.BLACKLIST;

                List<Pattern> patterns = new ArrayList<>();
                ConfigurationSection wcSec = us.getConfigurationSection("wildcards");
                if (wcSec != null) {
                    for (String k : sortedNumeric(wcSec.getKeys(false))) {
                        String wc = wcSec.getString(k, "");
                        if (wc == null || wc.isBlank()) continue;
                        Pattern p = compileWildcard(wc);
                        if (p != null) patterns.add(p);
                    }
                }
                ConfigurationSection rxSec = us.getConfigurationSection("regexes");
                if (rxSec != null) {
                    for (String k : sortedNumeric(rxSec.getKeys(false))) {
                        String rx = rxSec.getString(k, "");
                        if (rx == null || rx.isBlank()) continue;
                        Pattern p = compileRegex(rx);
                        if (p != null) patterns.add(p);
                    }
                }

                String condition = us.getString("condition", "all");
                boolean customDenyEnabled = us.getBoolean("custom_deny_msg.enabled", false);
                String customDenyMessage = us.getString("custom_deny_msg.message", "");
                units.add(new Unit(unitEnabled, mode, patterns, condition,
                        customDenyEnabled, customDenyMessage));
            }
        }
        boolean hasWhitelist = false;
        for (Unit u : units) if (u.mode == Mode.WHITELIST && u.enabled) { hasWhitelist = true; break; }
        return new AccessControl(enabled, units, hasWhitelist);
    }

    /**
     * Decides what to do with the input. See the class docs for the exact rules.
     */
    public Result decide(String cmd) {
        if (!enabled) return Result.allow();
        boolean whitelistFired = false;
        for (Unit u : units) {
            if (!u.enabled) continue;
            if (!u.fires(cmd)) continue;
            if (u.mode == Mode.BLACKLIST) {
                // Blacklist always wins over whitelist -> deny immediately.
                String custom = u.customDenyEnabled && !u.customDenyMessage.isBlank()
                        ? u.customDenyMessage : null;
                return Result.denyByBlacklist(u.matchText(cmd), custom);
            }
            whitelistFired = true;
        }
        if (whitelistFired) return Result.allow();
        return hasWhitelist ? Result.notAllowed() : Result.allow();
    }

    // ============================= UNITS =============================

    /** A single access-control unit: patterns + mode + matching rule + custom deny message. */
    private static final class Unit {
        private final boolean enabled;
        private final Mode mode;
        private final List<Pattern> patterns;
        private final boolean requireAll;
        private final List<Integer> required = new ArrayList<>();
        private final boolean customDenyEnabled;
        private final String customDenyMessage;

        Unit(boolean enabled, Mode mode, List<Pattern> patterns, String condition,
             boolean customDenyEnabled, String customDenyMessage) {
            this.enabled = enabled;
            this.mode = mode;
            this.patterns = patterns;
            this.customDenyEnabled = customDenyEnabled;
            this.customDenyMessage = customDenyMessage == null ? "" : customDenyMessage;
            this.requireAll = parseCondition(condition);
        }

        /**
         * True when the input satisfies the unit's condition: every required pattern
         * (or every pattern when condition is {@code all}) matches.
         */
        boolean fires(String cmd) {
            if (patterns.isEmpty()) return false;
            if (requireAll) {
                for (Pattern p : patterns) if (!p.matcher(cmd).find()) return false;
                return true;
            }
            for (int idx : required) {
                int i = idx - 1;
                if (i < 0 || i >= patterns.size()) return false;
                if (!patterns.get(i).matcher(cmd).find()) return false;
            }
            return true;
        }

        /** First matched substring (used to highlight a blacklist denial), or null. */
        String matchText(String cmd) {
            int[] scan = requireAll ? allIndices() : required.stream().mapToInt(Integer::intValue).toArray();
            for (int idx : scan) {
                int i = idx - 1;
                if (i < 0 || i >= patterns.size()) continue;
                Matcher m = patterns.get(i).matcher(cmd);
                if (m.find()) return m.group();
            }
            return null;
        }

        private boolean parseCondition(String cond) {
            if (cond == null) cond = "all";
            String c = cond.trim().toLowerCase();
            if (c.isEmpty()) c = "all";
            if (c.equals("all")) return true;

            // X-to-Y  (range)
            Matcher range = Pattern.compile("(\\d+)\\s*to\\s*(\\d+)").matcher(c);
            if (range.matches()) {
                int lo = Integer.parseInt(range.group(1));
                int hi = Integer.parseInt(range.group(2));
                int a = Math.min(lo, hi);
                int b = Math.max(lo, hi);
                for (int i = a; i <= b; i++) addRequired(i);
                if (required.isEmpty()) warnEmpty(cond);
                return false;
            }

            // single-X  or  X-and-Y-and-...  (enumerated)
            for (String part : c.split("-and-")) {
                String token = part.strip();
                if (token.startsWith("single-")) token = token.substring("single-".length()).trim();
                if (!token.matches("\\d+")) continue;
                addRequired(Integer.parseInt(token));
            }
            if (required.isEmpty()) warnEmpty(cond);
            return false;
        }

        private void addRequired(int idx) {
            if (idx >= 1 && idx <= patterns.size() && !required.contains(idx)) required.add(idx);
        }

        private void warnEmpty(String cond) {
            ConsoleLogger.warn("[Chat] Access-control condition \"" + cond
                    + "\" matches no pattern in a unit with " + patterns.size()
                    + " pattern(s) — unit is inactive.");
        }

        private int[] allIndices() {
            int[] a = new int[patterns.size()];
            for (int i = 0; i < a.length; i++) a[i] = i + 1;
            return a;
        }
    }

    // ============================= HELPERS =============================

    /** Sorts config keys numerically (strings like "1", "2", "10"); non-numeric go last. */
    private static List<String> sortedNumeric(java.util.Set<String> keys) {
        List<String> out = new ArrayList<>(keys);
        out.sort((a, b) -> Integer.compare(parseKey(a), parseKey(b)));
        return out;
    }

    private static int parseKey(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Compiles a regex, warning on invalid input. */
    private static Pattern compileRegex(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            ConsoleLogger.warn("[Chat] Invalid access-control regex: " + regex + " (" + e.getMessage() + ")");
        }
        return null;
    }

    /** Compiles a wildcard: {@code *} means any text (empty included). */
    private static Pattern compileWildcard(String wc) {
        StringBuilder sb = new StringBuilder("(?s)^");
        for (int i = 0; i < wc.length(); i++) {
            char c = wc.charAt(i);
            if (c == '*') sb.append(".*");
            else if ("\\.^$+?{}[]()|".indexOf(c) >= 0) sb.append('\\').append(c);
            else sb.append(c);
        }
        sb.append('$');
        try {
            return Pattern.compile(sb.toString());
        } catch (PatternSyntaxException e) {
            ConsoleLogger.warn("[Chat] Invalid access-control wildcard: " + wc + " (" + e.getMessage() + ")");
        }
        return null;
    }
}