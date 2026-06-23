package com.mcplugin.infrastructure.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.stream.Collectors;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public static Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
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
}
