package com.mcplugin.infrastructure.listeners;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Перехватывает команды /stop и /restart на самом раннем этапе.
 *
 * Зачем: В Paper команда /restart обрабатывается внутренним кодом DedicatedServer
 * до того, как Bukkit CommandMap получает управление. Поэтому обычное переопределение
 * через CommandMap (registerOverride) для /restart не работает, хотя для /stop работает.
 *
 * PlayerCommandPreprocessEvent срабатывает на уровне сетевого пакета (PlayerConnection),
 * ДО любой обработки команды Paper'ом — поэтому он гарантированно перехватит /restart.
 *
 * ServerCommandEvent перехватывает команды из консоли.
 *
 * Настройки читаются из config.yml -> power:
 */
public class PowerInterceptListener implements Listener {

    private static PowerInterceptListener instance;

    private String stopMessage;
    private String restartMessage;
    private boolean interceptEnabled;

    public PowerInterceptListener() {
        instance = this;
        reloadConfig();
    }

    /**
     * Позволяет обновить настройки при /mcplugin reload без пересоздания слушателя.
     */
    public static void reloadConfigStatic() {
        if (instance != null) {
            instance.reloadConfig();
        }
    }

    /**
     * Перезагружает настройки из config.yml.
     */
    public void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        interceptEnabled = cfg.getBoolean("power.intercept_commands", true);
        stopMessage = MessagesManager.getString("power.stop_message",
                "<dark_gray>[<dark_red>⚠</dark_red>] <red>Команда /stop отключена. Используйте: <white>/mp power off</white></dark_gray>");
        restartMessage = MessagesManager.getString("power.restart_message",
                "<dark_gray>[<dark_red>⚠</dark_red>] <red>Команда /restart отключена. Используйте: <white>/mp power reboot</white></dark_gray>");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!interceptEnabled) return;

        String msg = event.getMessage().toLowerCase().trim();

        // =========================
        // BLOCK /RESTART (player)
        // =========================
        // Проверяем точное совпадение и начало (чтобы поймать /restart с аргументами)
        // А также варианты с неймспейсами minecraft: и bukkit:
        if (isRestartCommand(msg)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.parse(restartMessage));
            return;
        }

        // =========================
        // BLOCK /STOP (player) — дублирующий перехват на случай,
        // если CommandMap override по какой-то причине не сработает
        // =========================
        if (isStopCommand(msg)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.parse(stopMessage));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!interceptEnabled) return;

        String command = event.getCommand().toLowerCase().trim();

        // =========================
        // BLOCK /RESTART (console)
        // =========================
        if (isRestartCommand("/" + command)) {
            event.setCancelled(true);
            event.getSender().sendMessage(MessageUtil.parse(restartMessage));
            return;
        }

        // =========================
        // BLOCK /STOP (console)
        // =========================
        if (isStopCommand("/" + command)) {
            event.setCancelled(true);
            event.getSender().sendMessage(MessageUtil.parse(stopMessage));
            return;
        }
    }

    /**
     * Проверяет, является ли сообщение командой /restart (с учётом неймспейсов и аргументов).
     */
    private boolean isRestartCommand(String msg) {
        return msg.equals("/restart")
                || msg.startsWith("/restart ")
                || msg.equals("/minecraft:restart")
                || msg.startsWith("/minecraft:restart ")
                || msg.equals("/bukkit:restart")
                || msg.startsWith("/bukkit:restart ");
    }

    /**
     * Проверяет, является ли сообщение командой /stop (с учётом неймспейсов и аргументов).
     */
    private boolean isStopCommand(String msg) {
        return msg.equals("/stop")
                || msg.startsWith("/stop ")
                || msg.equals("/minecraft:stop")
                || msg.startsWith("/minecraft:stop ")
                || msg.equals("/bukkit:stop")
                || msg.startsWith("/bukkit:stop ");
    }
}
