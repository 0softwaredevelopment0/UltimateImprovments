package com.ultimateimprovments.server;

import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ServerOverloadNotify {

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

    /** Sends to players with ui.alerts permission / OPs, respects cooldown. */
    public static void broadcast(String message) {
        long now = System.currentTimeMillis();
        if (cooldownEnabled && cooldownMs > 0 && (now - lastBroadcastTime) < cooldownMs) {
            return;
        }
        lastBroadcastTime = now;
        AlertBroadcast.send(message);
    }

    /** Sends to players with ui.alerts permission / OPs, no cooldown check. */
    public static void broadcastForce(String message) {
        AlertBroadcast.send(message);
    }

    /**
     * Sends to EVERY online player regardless of permission.
     * Used when broadcast_to_all = true in config.
     * @param cooldownMillis own cooldown window (0 = no cooldown)
     */
    public static void broadcastAll(String message, long cooldownMillis) {
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0 && (now - lastBroadcastTime) < cooldownMillis) {
            return;
        }
        lastBroadcastTime = now;

        if (message == null) return;
        var parsed = MessageUtil.parse(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(parsed);
        }
    }
}
