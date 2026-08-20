package com.ultimateimprovments.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 📢 Broadcast — centralized messaging to ALL players.
 * <p>
 * Unlike {@link AlertBroadcast} it does not check permissions — the message goes
 * to every online player, regardless of their permissions.
 * <p>
 * Usage:
 * <pre>{@code
 * Broadcast.send("<gold>⚡ Server reloading in 10 seconds!</gold>");
 * }</pre>
 */
public final class Broadcast {

    private Broadcast() {}

    /**
     * Sends a MiniMessage string to all online players (no permission check).
     *
     * @param miniMessage the MiniMessage-format string
     */
    public static void send(String miniMessage) {
        if (miniMessage == null) return;
        var parsed = MessageUtil.parse(miniMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(parsed);
        }
    }

    /**
     * Sends a message to all players with the plugin prefix {@code "[UI] "}
     * (see {@link MessageUtil#PREFIX}).
     *
     * @param miniMessage the message text (MiniMessage)
     */
    public static void sendServer(String miniMessage) {
        if (miniMessage == null) return;
        send(MessageUtil.PREFIX + " " + miniMessage);
    }

    /**
     * Sends a message to all players WITHOUT a prefix (embedded/clean — as-is).
     *
     * @param miniMessage the message text (MiniMessage)
     */
    public static void sendEmbedded(String miniMessage) {
        send(miniMessage);
    }
}
