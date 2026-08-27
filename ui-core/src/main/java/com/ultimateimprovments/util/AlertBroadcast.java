package com.ultimateimprovments.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * ⚠️ AlertBroadcast — centralized alert broadcasting.
 * <p>
 * Sends a MiniMessage string to all online players having the permission
 * {@code ui.alerts} (plus legacy permissions for backward compatibility:
 * {@code ui.overload.logs}, {@code ui.punish.notify},
 * {@code ui.anticheat.notify}).
 * <p>
 * Usage:
 * <pre>{@code
 * AlertBroadcast.send("<red>⚠ Server overloaded!</red>");
 * }</pre>
 */
public final class AlertBroadcast {

    /** The main permission for receiving alerts. */
    public static final String PERMISSION = "ui.alerts";

    /** Legacy alert permissions (backward compatibility). */
    private static final String[] LEGACY_PERMISSIONS = {
            "ui.overload.logs",
            "ui.punish.notify",
            "ui.anticheat.notify"
    };

    private AlertBroadcast() {}

    /**
     * Sends an alert to all online players with the {@code ui.alerts} permission
     * (or any legacy permission).
     *
     * @param miniMessage a string in MiniMessage format
     */
    public static void send(String miniMessage) {
        if (miniMessage == null) return;
        var parsed = MessageUtil.parse(miniMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasAlertPermission(player)) {
                player.sendMessage(parsed);
            }
        }
    }

    /**
     * @return true if the player has alert permission (ui.alerts or legacy),
     *         or is an operator (for backward compatibility with the anticheat).
     */
    public static boolean hasAlertPermission(Player player) {
        if (player.isOp()) return true;
        if (player.hasPermission(PERMISSION)) return true;
        for (String legacy : LEGACY_PERMISSIONS) {
            if (player.hasPermission(legacy)) return true;
        }
        return false;
    }
}
