package com.mcplugin.util;

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
     * Главная точка входа: пишет текст с ПОЛНЫМ резолвом плейсхолдеров и
     * парсингом MiniMessage.
     * <ol>
     *   <li>Если в тексте есть '%' — {@link PlaceholderResolver#resolve(String, Player)}
     *       пропускает все наши BUILTIN (включая динамические tps/mspt/online/ram/ping),
     *       а в самом конце применяет PlaceholderAPI. Так работают плейсхолдеры
     *       ЛЮБЫХ PAPI-плагинов.</li>
     *   <li>Если '%' нет — строка проходит в MiniMessage напрямую (fast-path).</li>
     * </ol>
     *
     * @param text    MiniMessage-строка с плейсхолдерами {@code %name%}
     * @param player  целевой игрок ({@code null} для серверных строк — PAPI всё равно работает для сервер-плейсхолдеров)
     */
    public static Component parse(String text, @Nullable Player player) {
        if (text == null) return Component.empty();
        if (!text.isEmpty() && text.indexOf('%') >= 0) {
            text = PlaceholderResolver.resolve(text, player);
        }
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Сценарий без игрока — для статики (GUI titles, MOTD, broadcast).
     * Делегирует в {@link #parse(String, Player)} с {@code player=null}.
     */
    public static Component parse(String miniMessage) {
        return parse(miniMessage, null);
    }

    public static List<Component> parse(List<String> miniMessages) {
        return miniMessages.stream()
                .map(MINI_MESSAGE::deserialize)
                .collect(Collectors.toList());
    }

    /**
     * Converts a MiniMessage string to a legacy §-formatted string.
     * Useful for APIs that still require legacy format (e.g. kickPlayer).
     */
    public static String legacy(String miniMessage) {
        return LEGACY_SERIALIZER.serialize(MINI_MESSAGE.deserialize(miniMessage));
    }

    /**
     * Converts a MiniMessage string to plain text (strips all formatting).
     * Useful for APIs that require plain strings (e.g. player sample names).
     */
    public static String toPlainText(String miniMessage) {
        Component component = MINI_MESSAGE.deserialize(miniMessage);
        return PLAIN_SERIALIZER.serialize(component);
    }
}
