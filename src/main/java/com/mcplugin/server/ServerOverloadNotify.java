package com.mcplugin.server;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Сообщения о перегрузке в чат — только игрокам с {@link #PERMISSION}.
 */
public final class ServerOverloadNotify {

    public static final String PERMISSION = "mcplugin.overload.logs";

    private ServerOverloadNotify() {}

    public static void broadcast(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION)) {
                player.sendMessage(message);
            }
        }
    }
}
