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
}
