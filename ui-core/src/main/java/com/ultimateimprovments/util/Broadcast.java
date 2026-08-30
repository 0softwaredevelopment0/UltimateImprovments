package com.ultimateimprovments.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Broadcast — centralized messaging to ALL players.
 * <p>
 * Unlike {@link AlertBroadcast} it does not check permissions — the message goes
 * to every online player, regardless of their permissions.
 * <p>
 * {@link #send(String)} and {@link #sendServer(String)} prepend the plugin
 * prefix {@code "[UI] "} (see {@link MessageUtil#PREFIX}); empty strings are
 * sent as-is so they can act as visual separators. Use
 * {@link #sendEmbedded(String)} when the prefix must NOT be added (e.g. the
 * {@code -clean} flag of the broadcast command).
 * <p>
 * Usage:
 * <pre>{@code
 * Broadcast.send("<gold>Server reloading in 10 seconds!</gold>");
 * Broadcast.sendEmbedded("<gold>Clean line without the [UI] prefix</gold>");
 * }</pre>
 */
public final class Broadcast {

    private Broadcast() {}

    /**
     * Sends a MiniMessage string to all online players (no permission check),
     * prefixed with {@code "[UI] "}. Empty/blank strings are sent unchanged
     * (visual separators).
     *
     * @param miniMessage the MiniMessage-format string
     */
    public static void send(String miniMessage) {
        if (miniMessage == null) return;
        if (miniMessage.isBlank()) {
            sendRaw(miniMessage);
            return;
        }
        sendRaw(MessageUtil.PREFIX + miniMessage);
    }

    /**
     * Sends a message to all players with the plugin prefix {@code "[UI] "}.
     * Equivalent to {@link #send(String)} — kept for backward compatibility.
     */
    public static void sendServer(String miniMessage) {
        send(miniMessage);
    }

    /**
     * Sends a message to all players WITHOUT the prefix (embedded/clean — as-is).
     * Used by the {@code -clean} flag of the broadcast command.
     */
    public static void sendEmbedded(String miniMessage) {
        sendRaw(miniMessage);
    }

    /** Low-level send — no prefix, no checks. */
    private static void sendRaw(String miniMessage) {
        if (miniMessage == null) return;
        var parsed = MessageUtil.parse(miniMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(parsed);
        }
    }
}
