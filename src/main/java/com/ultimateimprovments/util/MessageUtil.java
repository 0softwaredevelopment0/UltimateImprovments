package com.ultimateimprovments.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    /** Префикс плагина для всех сообщений: "[UI] ". */
    public static final String PREFIX = "<white>[<green>UI<white>] <reset>";

    /**
     * Main entry point: renders text with FULL placeholder resolution and
     * MiniMessage parsing.
     * <ol>
     *   <li>If the text contains '%' — {@link PlaceholderResolver#resolve(String, Player)}
     *       passes through all our BUILTIN placeholders (including dynamic tps/mspt/online/ram/ping),
     *       and applies PlaceholderAPI at the very end. This is how placeholders
     *       of ANY PAPI plugin work.</li>
     *   <li>If there is no '%' — the string goes straight to MiniMessage (fast-path).</li>
     * </ol>
     *
     * @param text    a MiniMessage string with {@code %name%} placeholders
     * @param player  the target player ({@code null} for server strings — PAPI still works for server placeholders)
     */
    public static Component parse(String text, @Nullable Player player) {
        if (text == null) return Component.empty();
        if (!text.isEmpty() && text.indexOf('%') >= 0) {
            text = PlaceholderResolver.resolve(text, player);
        }
        return deserialize(text);
    }

    /**
     * Safe deserializer. MiniMessage does not understand legacy § codes — but a
     * message can legitimately mix BOTH: e.g. a miniMessage template with an item
     * name embedded via {@code getDisplayName()} which Paper returns as a legacy
     * §-formatted string. Sending such a string through the legacy serializer
     * would print the {@code <red>} tags literally (all white), so instead we
     * convert every § code to its miniMessage tag and then parse the whole
     * string with MiniMessage.
     */
    private static Component deserialize(String text) {
        if (text == null) return Component.empty();
        if (text.indexOf('\u00A7') >= 0) {
            text = legacyToMiniMessage(text);
        }
        return MINI_MESSAGE.deserialize(text);
    }

    /** Legacy § codes → MiniMessage tag names. */
    private static final Map<Character, String> LEGACY_CODES = new HashMap<>();
    static {
        LEGACY_CODES.put('0', "black");
        LEGACY_CODES.put('1', "dark_blue");
        LEGACY_CODES.put('2', "dark_green");
        LEGACY_CODES.put('3', "dark_aqua");
        LEGACY_CODES.put('4', "dark_red");
        LEGACY_CODES.put('5', "dark_purple");
        LEGACY_CODES.put('6', "gold");
        LEGACY_CODES.put('7', "gray");
        LEGACY_CODES.put('8', "dark_gray");
        LEGACY_CODES.put('9', "blue");
        LEGACY_CODES.put('a', "green");
        LEGACY_CODES.put('b', "aqua");
        LEGACY_CODES.put('c', "red");
        LEGACY_CODES.put('d', "light_purple");
        LEGACY_CODES.put('e', "yellow");
        LEGACY_CODES.put('f', "white");
        LEGACY_CODES.put('k', "obfuscated");
        LEGACY_CODES.put('l', "bold");
        LEGACY_CODES.put('m', "strikethrough");
        LEGACY_CODES.put('n', "underline");
        LEGACY_CODES.put('o', "italic");
        LEGACY_CODES.put('r', "reset");
    }

    /**
     * Converts legacy {@code §}-codes (incl. {@code §x} RGB hex) to MiniMessage
     * tags so the result can be parsed by MiniMessage. Unknown codes are dropped.
     */
    private static String legacyToMiniMessage(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));

                // §x§R§R§G§G§B§B — legacy RGB hex (1.16+)
                if (code == 'x' && i + 12 < text.length()) {
                    StringBuilder hex = new StringBuilder(6);
                    boolean ok = true;
                    for (int j = 0; j < 6; j++) {
                        if (text.charAt(i + 1) == '\u00A7' && isHexDigit(text.charAt(i + 2))) {
                            hex.append(text.charAt(i + 2));
                            i += 2;
                        } else {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) sb.append("<#").append(hex).append('>');
                    continue;
                }

                String tag = LEGACY_CODES.get(code);
                if (tag != null) {
                    sb.append('<').append(tag).append('>');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * No-player scenario — for static content (GUI titles, MOTD, broadcast).
     * Delegates to {@link #parse(String, Player)} with {@code player=null}.
     */
    public static Component parse(String miniMessage) {
        return parse(miniMessage, null);
    }

    public static List<Component> parse(List<String> miniMessages) {
        return miniMessages.stream()
                .map(MessageUtil::deserialize)
                .collect(Collectors.toList());
    }

    /**
     * Converts a MiniMessage string to a legacy §-formatted string.
     * Useful for APIs that still require legacy format (e.g. kickPlayer).
     */
    public static String legacy(String miniMessage) {
        return LEGACY_SERIALIZER.serialize(deserialize(miniMessage));
    }

    /**
     * Converts a MiniMessage string to plain text (strips all formatting).
     * Useful for APIs that require plain strings (e.g. player sample names).
     */
    public static String toPlainText(String miniMessage) {
        Component component = deserialize(miniMessage);
        return PLAIN_SERIALIZER.serialize(component);
    }
}
