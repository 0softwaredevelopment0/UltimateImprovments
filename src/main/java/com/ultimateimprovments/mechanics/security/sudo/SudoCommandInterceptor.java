package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * 🚨 SudoCommandInterceptor — GitHub-style sudo-режим.
 * <p>
 * Перехватывает опасные команды у игроков с правом {@code ui.sudo}:
 * {@code /ui punish crash ...}, любые {@code /lp ...} и подкоманды,
 * {@code /ui power off|reboot} и т.п. (список в config.yml).
 * Если sudo-сессия не активна — команда блокируется, игроку открывается
 * диалог ввода sudo-пароля.
 */
public class SudoCommandInterceptor implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!SudoManager.isEnabled()) return;

        Player player = event.getPlayer();

        // Только игроки с правом ui.sudo попадают под sudo-режим
        if (!player.hasPermission("ui.sudo")) return;

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        SudoManager manager = SudoManager.getInstance();
        if (manager == null) return;

        if (!manager.isDangerous(msg)) return;

        // Активная sudo-сессия — пропускаем без вопросов
        if (manager.isSudoActive(player.getUniqueId())) return;

        if (manager.intercept(player, event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.parse(
                    "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <gray>Enter your sudo password in the dialog to continue.</gray>"));
        }
    }
}
