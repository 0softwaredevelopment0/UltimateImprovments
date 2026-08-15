package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Intercepts the /stop and /restart commands at the earliest stage.
 *
 * Why: In Paper the /restart command is handled by DedicatedServer's internal code
 * before the Bukkit CommandMap gets control. So the usual override
 * via CommandMap (registerOverride) does not work for /restart, although it works for /stop.
 *
 * PlayerCommandPreprocessEvent fires at the network packet level (PlayerConnection),
 * BEFORE any command processing by Paper — so it reliably intercepts /restart.
 *
 * ServerCommandEvent intercepts commands from the console.
 *
 * Settings are read from config.yml -> power:
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
     * Allows updating settings on /ui reload without recreating the listener.
     */
    public static void reloadConfigStatic() {
        if (instance != null) {
            instance.reloadConfig();
        }
    }

    /**
     * Reloads settings from config.yml.
     */
    public void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        interceptEnabled = cfg.getBoolean("power.intercept_commands", true);
        stopMessage = MessagesManager.getString("power.stop_message",
                "<dark_gray>[<dark_red>⚠</dark_red>] <red>Команда /stop отключена. Используйте: <white>/ui power off</white></dark_gray>");
        restartMessage = MessagesManager.getString("power.restart_message",
                "<dark_gray>[<dark_red>⚠</dark_red>] <red>Команда /restart отключена. Используйте: <white>/ui power reboot</white></dark_gray>");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!interceptEnabled) return;

        String msg = event.getMessage().toLowerCase().trim();

        // =========================
        // BLOCK /RESTART (player)
        // =========================
        // Check exact match and prefix (to catch /restart with arguments)
        // Also variants with minecraft: and bukkit: namespaces:
        if (isRestartCommand(msg)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.parse(restartMessage));
            return;
        }

        // =========================
        // BLOCK /STOP (player) — duplicate interception in case
        // the CommandMap override does not fire for some reason
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
     * Checks whether the message is a /restart command (considering namespaces and arguments).
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
     * Checks whether the message is a /stop command (considering namespaces and arguments).
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
