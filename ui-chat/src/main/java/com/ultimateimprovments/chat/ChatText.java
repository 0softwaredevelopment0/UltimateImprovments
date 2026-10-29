package com.ultimateimprovments.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hardening of player-supplied chat text.
 * <p>
 * Minecraft chat renders a component's child lines one under another. If a
 * player manages to smuggle a line break into the <em>message</em> part of a
 * chat format, every following line is rendered WITHOUT the chat prefix and
 * looks like a server/system line — e.g. typing
 * {@code <newline>[Server] Restart in 5s} (possible while
 * {@code chat.player_minimessage: true}) forges a fake "[Server]" line.
 * <p>
 * Convention: player text is never rendered as one blob. It is split into
 * single-line pieces here, and {@link ChatManager} renders EVERY piece through
 * the full channel format, so each line carries its prefix and sender name.
 * The number of pieces is capped to keep chat readable and flood-proof.
 */
public final class ChatText {

    /** Default cap for the number of rendered lines of a single message. */
    public static final int DEFAULT_MAX_LINES = 3;

    /** Suffix appended to the last line when the message was cut by the cap. */
    private static final String TRUNCATED_SUFFIX = "\u2026";

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

    /** Legacy section-sign colour codes ({@code §a}, {@code §l}, ...), removed with their letter. */
    private static final Pattern LEGACY_CODES = Pattern.compile("\u00A7[0-9a-fk-orx]");

    private ChatText() {}

    /**
     * Splits a message into the lines that must be rendered individually.
     *
     * @param text           the message text (placeholders already resolved)
     * @param miniMessage    {@code true} when the player may use MiniMessage, so
     *                       newline-producing tags also count as line breaks
     * @param maxLines       hard cap of returned lines ({@code <= 0} → default);
     *                       when the message is longer, the last kept line gets
     *                       an ellipsis marker
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
