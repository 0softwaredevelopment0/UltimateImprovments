package com.ultimateimprovments.util;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.database.PlayerSettingsDB;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Parser + validator of player-supplied scoreboard nicknames.
 * <p>
 * The nickname is stored <b>as typed</b> (MiniMessage included), so every
 * player sees it with its styles. All checks — length and forbidden syntax —
 * run on the <b>visible</b> text only: the raw string is parsed with
 * MiniMessage and serialised back to plain text, so {@code <red>nick} yields
 * {@code nick} (4 characters, no forbidden characters from the tags).
 *
 * <h2>Order of checks</h2>
 * <ol>
 *   <li>raw sanity: non-empty, raw length cap ({@value #RAW_LENGTH_LIMIT}),
 *       no {@code %} (placeholders are not expanded inside a nickname),
 *       no legacy {@code §} colour codes;</li>
 *   <li>MiniMessage parse — a broken fragment is rejected (error 021);</li>
 *   <li>visible length vs {@code scoreboard.nick.min_length} /
 *       {@code max_length} (error 020), with the
 *       {@code ui.scoreboardnick.bypass.length[.min|.max]} bypasses;</li>
 *   <li>forbidden-syntax filter ({@code scoreboard.nick.filter}) — error 019,
 *       reporting the rule that blocked the text (blacklist) or the rule it
 *       failed to match (whitelist).</li>
 * </ol>
 */
public final class ScoreboardNickValidator {

    /** 019 — the nickname hit a forbidden-syntax rule. */
    public static final int ERR_FORBIDDEN_SYNTAX = 19;
    /** 020 — the nickname length is outside the configured limits. */
    public static final int ERR_BAD_LENGTH = 20;
    /** 021 — the nickname cannot be parsed as a nickname at all. */
    public static final int ERR_INVALID_FORMAT = 21;

    /** Hard cap on the raw (MiniMessage) string, independent of the visible limit. */
    public static final int RAW_LENGTH_LIMIT = 512;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** A validated nickname: raw MiniMessage + visible text + its length. */
    public record Nick(String raw, String plain, int visibleLength) {}

    /**
     * Outcome of {@link #validate(Player, String)} — either a {@link Nick}
     * or an error (code + ready-to-send MiniMessage body without the code prefix).
     */
    public record Validation(Nick nick, int errorCode, String errorMessage) {
        public boolean ok() {
            return nick != null;
        }
    }

    private ScoreboardNickValidator() {}

    // ═══════════ PUBLIC API ═══════════

    /** Minimum visible length from the config (never below 1). */
    public static int minLength() {
        return Math.max(1, section().getInt("scoreboard.nick.min_length", 1));
    }

    /** Maximum visible length from the config (never below the minimum). */
    public static int maxLength() {
        return Math.max(minLength(), section().getInt("scoreboard.nick.max_length", 16));
    }

    /** Whether players may set scoreboard nicknames at all ({@code scoreboard.nick.enabled}). */
    public static boolean featureEnabled() {
        return section().getBoolean("scoreboard.nick.enabled", true);
    }

    /** Whether the forbidden-syntax filter is active ({@code scoreboard.nick.filter.enabled}). */
    public static boolean filterEnabled() {
        return section().getBoolean("scoreboard.nick.filter.enabled", false);
    }

    /** Parses and validates a raw nickname for the given player. Never throws. */
    public static Validation validate(Player player, String raw) {
        String typed = raw == null ? "" : raw.trim();
        int min = minLength();
        int max = maxLength();

        if (typed.isEmpty()) {
            return reject(ERR_BAD_LENGTH, tooShort(min, 0));
        }
        if (typed.length() > RAW_LENGTH_LIMIT) {
            return reject(ERR_BAD_LENGTH, tooLong(max, typed.length()));
        }
        if (typed.indexOf('%') >= 0) {
            return reject(ERR_INVALID_FORMAT, message("scoreboard.nick.error_percent",
                    "<red>Placeholders ('%') are not allowed in a nickname</red>"));
        }
        if (typed.indexOf('§') >= 0) {
            return reject(ERR_INVALID_FORMAT, message("scoreboard.nick.error_legacy",
                    "<red>Legacy '§' colour codes are not allowed — use MiniMessage tags like &lt;red&gt;</red>"));
        }

        String plain;
        try {
            plain = PLAIN.serialize(MM.deserialize(typed));
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return reject(ERR_INVALID_FORMAT, message("scoreboard.nick.error_minimessage",
                    "<red>Invalid MiniMessage: </red><white>%error%</white>")
                    .replace("%error%", MM.escapeTags(reason)));
        }

        int visible = plain.codePointCount(0, plain.length());
        if (plain.isBlank() || visible < 1) {
            return reject(ERR_BAD_LENGTH, tooShort(min, visible));
        }

        boolean bypassAll = has(player, Permissions.SCOREBOARDNICK_BYPASS_LENGTH);
        if (!bypassAll && !has(player, Permissions.SCOREBOARDNICK_BYPASS_LENGTH_MIN) && visible < min) {
            return reject(ERR_BAD_LENGTH, tooShort(min, visible));
        }
        if (!bypassAll && !has(player, Permissions.SCOREBOARDNICK_BYPASS_LENGTH_MAX) && visible > max) {
            return reject(ERR_BAD_LENGTH, tooLong(max, visible));
        }

        Validation filter = checkFilter(player, plain);
        if (filter != null) return filter;

        return new Validation(new Nick(typed, plain, visible), 0, null);
    }

    /** True when a nickname is stored for the player AND enabled. */
    public static boolean isActive(Player player) {
        return player != null
                && PlayerSettingsDB.getScoreboardNick(player.getUniqueId()) != null
                && PlayerSettingsDB.isScoreboardNickEnabled(player.getUniqueId());
    }

    /** Visible length of an already stored nickname (falls back to the raw length when unparsable). */
    public static int visibleLength(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            String plain = PLAIN.serialize(MM.deserialize(raw));
            return plain.codePointCount(0, plain.length());
        } catch (RuntimeException e) {
            return raw.codePointCount(0, raw.length());
        }
    }

    // ═══════════ FILTER ═══════════

    /** Runs the forbidden-syntax filter; {@code null} when the text passes. */
    private static Validation checkFilter(Player player, String plain) {
        org.bukkit.configuration.ConfigurationSection cfg = section();
        if (!cfg.getBoolean("scoreboard.nick.filter.enabled", false)) return null;

        boolean bypassAll = has(player, Permissions.SCOREBOARDNICK_BYPASS_FILTER);
        boolean bypassWildcard = bypassAll || has(player, Permissions.SCOREBOARDNICK_BYPASS_WILDCARD);
        boolean bypassRegex = bypassAll || has(player, Permissions.SCOREBOARDNICK_BYPASS_REGEX);

        List<String> wildcards = bypassWildcard
                ? List.of() : cfg.getStringList("scoreboard.nick.filter.wildcard");
        List<String> regexes = bypassRegex
                ? List.of() : cfg.getStringList("scoreboard.nick.filter.regex");

        boolean whitelist = "whitelist".equalsIgnoreCase(
                cfg.getString("scoreboard.nick.filter.mode", "blacklist"));
        ScoreboardNickFilter.Mode mode = whitelist
                ? ScoreboardNickFilter.Mode.WHITELIST : ScoreboardNickFilter.Mode.BLACKLIST;

        warnAboutInvalidRules(wildcards, regexes);

        ScoreboardNickFilter.Result result = ScoreboardNickFilter.check(plain, mode, wildcards, regexes);
        if (result.allowed()) return null;

        ScoreboardNickFilter.Hit hit = result.hit();
        String section = hit == null ? "" : hit.section();
        String rule = hit == null ? "" : MM.escapeTags(hit.rule());
        String key = whitelist
                ? "scoreboard.nick.forbidden_whitelist" : "scoreboard.nick.forbidden_blacklist";
        String def = whitelist
                ? "<red>Nickname does not match the %section% rule \"<white>%rule%</white>\" required by the whitelist</red>"
                : "<red>Nickname contains forbidden syntax — blocked by the %section% rule \"<white>%rule%</white>\"</red>";
        return reject(ERR_FORBIDDEN_SYNTAX, message(key, def)
                .replace("%section%", section)
                .replace("%rule%", rule));
    }

    /** Logs broken regex/wildcard rules once per call — an admin typo is never fatal. */
    private static void warnAboutInvalidRules(List<String> wildcards, List<String> regexes) {
        for (String rule : regexes) {
            if (rule != null && !rule.isBlank() && ScoreboardNickFilter.isInvalid(rule)) {
                ConsoleLogger.warn("[ScoreboardNick] Invalid regex rule ignored: " + rule);
            }
        }
        for (String rule : wildcards) {
            if (rule != null && !rule.isBlank() && ScoreboardNickFilter.isInvalid(rule)) {
                ConsoleLogger.warn("[ScoreboardNick] Unusable wildcard rule ignored: " + rule);
            }
        }
    }

    // ═══════════ HELPERS ═══════════

    private static Validation reject(int code, String message) {
        return new Validation(null, code, message);
    }

    private static String tooShort(int min, int actual) {
        return message("scoreboard.nick.too_short",
                "<red>Nickname is too short: </red><white>%length%</white><red> chars, minimum is </red><white>%min%</white>")
                .replace("%min%", String.valueOf(min))
                .replace("%length%", String.valueOf(actual));
    }

    private static String tooLong(int max, int actual) {
        return message("scoreboard.nick.too_long",
                "<red>Nickname is too long: </red><white>%length%</white><red> chars, maximum is </red><white>%max%</white>")
                .replace("%max%", String.valueOf(max))
                .replace("%length%", String.valueOf(actual));
    }

    private static String message(String key, String def) {
        return MessagesManager.getString(key, def);
    }

    private static boolean has(Player player, String permission) {
        return player != null && player.hasPermission(permission);
    }

    private static FileConfiguration config() {
        Main plugin = Main.getInstance();
        return plugin == null ? null : plugin.getConfig();
    }

    /** Config view with a safe fallback: an empty section answers with the defaults. */
    private static org.bukkit.configuration.ConfigurationSection section() {
        FileConfiguration config = config();
        return config != null ? config : new org.bukkit.configuration.MemoryConfiguration();
    }
}
