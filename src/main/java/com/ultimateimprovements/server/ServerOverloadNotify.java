package com.ultimateimprovements.server;

import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ServerOverloadNotify {

    public static final String PERMISSION = "mcplugin.overload.logs";

    private static long cooldownMs = 30_000L;
    private static long lastBroadcastTime = 0;
    private static boolean cooldownEnabled = true;

    private ServerOverloadNotify() {}

    public static void setCooldownMs(long ms) {
        cooldownMs = ms;
    }

    public static void setCooldownEnabled(boolean enabled) {
        cooldownEnabled = enabled;
    }

    public static void broadcast(String message) {
        long now = System.currentTimeMillis();
        if (cooldownEnabled && cooldownMs > 0 && (now - lastBroadcastTime) < cooldownMs) {
            return;
        }
        lastBroadcastTime = now;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION)) {
                player.sendMessage(MessageUtil.parse(message));
            }
        }
    }

    public static void broadcastForce(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION)) {
                player.sendMessage(MessageUtil.parse(message));
            }
        }
    }
}
