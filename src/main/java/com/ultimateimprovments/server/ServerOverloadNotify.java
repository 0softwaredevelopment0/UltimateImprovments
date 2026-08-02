package com.ultimateimprovments.server;

import com.ultimateimprovments.util.AlertBroadcast;

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

    public static void broadcast(String message) {
        long now = System.currentTimeMillis();
        if (cooldownEnabled && cooldownMs > 0 && (now - lastBroadcastTime) < cooldownMs) {
            return;
        }
        lastBroadcastTime = now;

        AlertBroadcast.send(message);
    }

    public static void broadcastForce(String message) {
        AlertBroadcast.send(message);
    }
}
