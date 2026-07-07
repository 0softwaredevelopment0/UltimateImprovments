package com.mcplugin.listener;

import com.mcplugin.core.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat filter (protection against profanity).
 *
 * Checks player messages for prohibited words from config.
 * Supports wildcard patterns:
 *   - "badword"     → exact word match (whole word, case-insensitive)
 *   - "badword*"    → word starts with "badword"
 *   - "*badword"    → word ends with "badword"
 *   - "*badword*"   → word contained in message
 *
 * Uses Unicode property (\p{L}) for correct Cyrillic handling.
 * Case-insensitive matching.
 * On detection: message cancelled, player warned.
 */
public class ChatFilterManager implements Listener {

    private static ChatFilterManager instance;

    private boolean enabled;
    private String warningMessage;
    private List<Pattern> compiledPatterns;
    private List<String> patternSources;

    // For highlighting bad words in messages
    private List<String> highlightWords;
    private List<Pattern> highlightPatterns;

    public ChatFilterManager() {
        instance = this;
        reloadConfig();
    }

    public static void reloadConfigStatic() {
        if (instance != null) {
            instance.reloadConfig();
        }
    }

    /**
     * Loads/reloads filter settings from config.yml.
     *
     * Loads two pattern sources:
     *   1. {@code chat_filter.words} — simple words with wildcard (*): "badword", "*bad*"
     *   2. {@code chat_filter.regex_patterns} — complex Java regex expressions (YAML literal block scalars)
     */
    public void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("chat_filter.enabled", true);
        warningMessage = MessagesManager.getString("chat_filter.message", "");

        compiledPatterns = new ArrayList<>();
        patternSources = new ArrayList<>();
        highlightWords = new ArrayList<>();
        highlightPatterns = new ArrayList<>();

        // 1. Load simple words with wildcard
        List<String> rawWords = cfg.getStringList("chat_filter.words");
        if (rawWords == null) {
            rawWords = new ArrayList<>();
        }
        for (String raw : rawWords) {
            Pattern p = compileWordPattern(raw);
            if (p != null) {
                compiledPatterns.add(p);
                patternSources.add("<green>word</green><white>: </white><red>" + raw.trim() + "</red><white></white>");
                // Save clean word for highlighting
                String clean = raw.trim().replace("*", "");
                if (!clean.isEmpty()) {
                    highlightWords.add(clean);
                }
            }
        }

        // 2. Load complex regex expressions from config.yml (YAML literal block scalars)
        List<String> rawRegexes = cfg.getStringList("chat_filter.regex_patterns");
        if (rawRegexes == null) {
            rawRegexes = new ArrayList<>();
        }
        for (String raw : rawRegexes) {
            Pattern p = compileRegexPattern(raw);
            if (p != null) {
                compiledPatterns.add(p);
                patternSources.add("<green>regex</green><white>: </white><gray>" + raw.trim() + "</gray><white></white>");
                // Create highlight finder without .* around edges for precise position detection
                Pattern finder = compileHighlightFinder(raw);
                if (finder != null) {
                    highlightPatterns.add(finder);
                }
            }
        }

        ConsoleLogger.info("[CHAT-FILTER] Loaded " + compiledPatterns.size() + " pattern(s) ("
                + rawWords.size() + " word(s) + "
                + (rawRegexes != null ? rawRegexes.size() : 0) + " regex(es)).");
    }

    /**
     * Converts a string with optional wildcard (*) into a regex pattern.
     *
     * Uses Unicode property \p{L} for word boundaries to correctly handle
     * Cyrillic (unlike \b which only works with [a-zA-Z0-9_]).
     *
     * Examples:
     *   "badword"     → (?i).*(?<!\p{L})badword(?!\p{L}).*
     *   "badword*"    → (?i).*(?<!\p{L})badword.*
     *   "*badword"    → (?i).*badword(?!\p{L}).*
     *   "*badword*"   → (?i).*badword.*
     */
    private Pattern compileWordPattern(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String word = raw.trim();
        boolean startsWithStar = word.startsWith("*");
        boolean endsWithStar = word.endsWith("*");

        // Remove wildcard symbols to get the clean word
        if (startsWithStar) word = word.substring(1);
        if (endsWithStar) word = word.substring(0, word.length() - (endsWithStar ? 1 : 0));

        if (word.isEmpty()) return null;

        // Escape regex special characters
        String escaped = Pattern.quote(word);

        StringBuilder regex = new StringBuilder("(?i).*");

        if (!startsWithStar) {
            // Word start: no Unicode letter before the word
            regex.append("(?<!\\p{L})");
        }
        regex.append(escaped);
        if (!endsWithStar) {
            // Word end: no Unicode letter after the word
            regex.append("(?!\\p{L})");
        }

        regex.append(".*");

        try {
            return Pattern.compile(regex.toString());
        } catch (PatternSyntaxException e) {
            ConsoleLogger.warn("[CHAT-FILTER] Invalid pattern: " + raw + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * Compiles a raw string as a Java regex expression.
     *
     * Automatically adds (?i) for case-insensitivity,
     * but does not duplicate it if already present.
     *
     * The pattern is applied to the ENTIRE message via Matcher.matches(),
     * so use .* at start and end for substring matching.
     */
    private Pattern compileRegexPattern(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String regex = raw.trim();

        // Add (?i) at the start if not already present
        if (!regex.startsWith("(?i)") && !regex.startsWith("(?-i)")) {
            regex = "(?i)" + regex;
        }

        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            ConsoleLogger.warn("[CHAT-FILTER] Invalid regex: " + raw + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * Creates a lightweight pattern for find(), without .* around edges,
     * for precise match position detection.
     */
    private Pattern compileHighlightFinder(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String stripped = raw.trim();

        // Remove leading .* and trailing .*
        if (stripped.startsWith(".*")) stripped = stripped.substring(2);
        if (stripped.endsWith(".*")) stripped = stripped.substring(0, stripped.length() - 2);

        if (stripped.isEmpty()) return null;

        // Add (?i) if not present
        if (!stripped.startsWith("(?i)") && !stripped.startsWith("(?-i)")) {
            stripped = "(?i)" + stripped;
        }

        try {
            return Pattern.compile(stripped);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /**
     * Builds a string with bad words highlighted in red (§c…§r).
     * Finds all occurrences from highlightWords and highlightPatterns,
     * merges overlapping ranges and inserts color codes.
     */
    private String highlightBadWords(String message) {
        if (message == null || message.isEmpty()) return message;

        List<int[]> ranges = new ArrayList<>();
        String lowerMsg = message.toLowerCase();

        // 1. Find simple word occurrences
        for (String word : highlightWords) {
            String lowerWord = word.toLowerCase();
            int idx = 0;
            while ((idx = lowerMsg.indexOf(lowerWord, idx)) != -1) {
                ranges.add(new int[]{idx, idx + word.length()});
                idx += word.length();
            }
        }

        // 2. Find regex pattern matches via find()
        for (Pattern p : highlightPatterns) {
            Matcher m = p.matcher(message);
            while (m.find()) {
                ranges.add(new int[]{m.start(), m.end()});
            }
        }

        if (ranges.isEmpty()) {
            return message;
        }

        // Sort by start position
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Merge overlapping ranges
        List<int[]> merged = new ArrayList<>();
        for (int[] r : ranges) {
            if (merged.isEmpty()) {
                merged.add(new int[]{r[0], r[1]});
            } else {
                int[] last = merged.get(merged.size() - 1);
                if (r[0] <= last[1]) {
                    last[1] = Math.max(last[1], r[1]);
                } else {
                    merged.add(new int[]{r[0], r[1]});
                }
            }
        }

        // Build final string with §c…§r
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        for (int[] r : merged) {
            sb.append(message, lastEnd, r[0]);
            sb.append("§c");
            sb.append(message, r[0], r[1]);
            sb.append("§r");
            lastEnd = r[1];
        }
        sb.append(message.substring(lastEnd));

        return sb.toString();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Skip if player has bypass permission
        if (player.hasPermission("mcplugin.chat.filter.bypass")) return;

        String message = event.getMessage();

        for (int i = 0; i < compiledPatterns.size(); i++) {
            Pattern pattern = compiledPatterns.get(i);
            if (pattern.matcher(message).matches()) {
                event.setCancelled(true);

                String source = patternSources.get(i);
                String plainSource = source.replaceAll("<[^>]+>", ""); // remove MiniMessage tags for console

                // Message with highlighting
                String highlighted = highlightBadWords(message);

                // Console log (with highlighted message)
                ConsoleLogger.warn("[CHAT-FILTER] " + player.getName()
                        + " violated rule '" + plainSource + "': " + highlighted);

                // Player message
                player.sendMessage(MessageUtil.parse(warningMessage));
                player.sendMessage("§7→ §f" + highlighted);
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("chat_filter.violation_match", "<gray>└</gray> <white>Match:</white> {source}").replace("{source}", source)));
                return;
            }
        }
    }
}
