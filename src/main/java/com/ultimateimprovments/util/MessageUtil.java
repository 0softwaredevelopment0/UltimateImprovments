package com.ultimateimprovments.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

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
     * Safe deserializer: MiniMessage does not understand legacy § codes and throws
     * ParsingExceptionImpl (which crashes Bukkit tasks). If the string contains § —
     * convert via LegacyComponentSerializer instead of crashing.
     */
    private static Component deserialize(String text) {
        if (text != null && text.indexOf('\u00A7') >= 0) {
            return LEGACY_SERIALIZER.deserialize(text);
        }
        return MINI_MESSAGE.deserialize(text);
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
