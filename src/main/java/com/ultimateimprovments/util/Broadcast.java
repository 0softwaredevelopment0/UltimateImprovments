package com.ultimateimprovments.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 📢 Broadcast — централизованная рассылка сообщений ВСЕМ игрокам.
 * <p>
 * В отличие от {@link AlertBroadcast} не проверяет права — сообщение уходит
 * каждому онлайн-игроку, не важно какое право у него есть.
 * <p>
 * Использование:
 * <pre>{@code
 * Broadcast.send("<gold>⚡ Сервер перезагружается через 10 секунд!</gold>");
 * }</pre>
 */
public final class Broadcast {

    /** Server message prefix: "sᴇʀᴠᴇʀ » ". */
    public static final String SERVER_PREFIX = "<white>sᴇʀᴠᴇʀ <dark_gray>» <reset> ";

    private Broadcast() {}

    /**
     * Отправляет MiniMessage-строку всем онлайн-игрокам (без проверки прав).
     *
     * @param miniMessage строка в формате MiniMessage
     */
    public static void send(String miniMessage) {
        if (miniMessage == null) return;
        var parsed = MessageUtil.parse(miniMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(parsed);
        }
    }

    /**
     * Sends a message to all players with the server prefix {@code "sᴇʀᴠᴇʀ » "}.
     *
     * @param miniMessage the message text (MiniMessage)
     */
    public static void sendServer(String miniMessage) {
        if (miniMessage == null) return;
        send(SERVER_PREFIX + miniMessage);
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
