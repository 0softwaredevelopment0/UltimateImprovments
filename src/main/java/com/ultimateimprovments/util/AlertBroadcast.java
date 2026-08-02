package com.ultimateimprovments.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * ⚠️ AlertBroadcast — централизованная рассылка алертов.
 * <p>
 * Отправляет MiniMessage-строку всем онлайн-игрокам, имеющим право
 * {@code ui.alerts} (плюс legacy-права для обратной совместимости:
 * {@code ui.overload.logs}, {@code ui.punish.notify},
 * {@code ui.anticheat.notify}).
 * <p>
 * Использование:
 * <pre>{@code
 * AlertBroadcast.send("<red>⚠ Сервер перегружен!</red>");
 * }</pre>
 */
public final class AlertBroadcast {

    /** Основное право на получение алертов. */
    public static final String PERMISSION = "ui.alerts";

    /** Старые права алертов (обратная совместимость). */
    private static final String[] LEGACY_PERMISSIONS = {
            "ui.overload.logs",
            "ui.punish.notify",
            "ui.anticheat.notify"
    };

    private AlertBroadcast() {}

    /**
     * Отправляет алерт всем онлайн-игрокам с правом {@code ui.alerts}
     * (или любым legacy-правом).
     *
     * @param miniMessage строка в формате MiniMessage
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
     * @return true если игрок имеет право на алерты (ui.alerts или legacy),
     *         либо является оператором (для обратной совместимости с античитом).
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
