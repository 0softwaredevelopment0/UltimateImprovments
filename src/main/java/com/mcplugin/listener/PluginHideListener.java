package com.mcplugin.listener;

import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class PluginHideListener implements Listener {

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {

        String rawMessage = event.getMessage();
        String message = rawMessage.toLowerCase().trim();

        // =========================
        // SILENT /mp pane_click — cancel to prevent console spam,
        // then manually dispatch so the command still works.
        // Also check !isCancelled to avoid bypassing AuthListener
        // (which fires at LOWEST priority for unauthenticated players).
        // =========================
        if (message.startsWith("/mp pane_click") && !event.isCancelled()) {
            event.setCancelled(true);
            // Strip leading '/' and dispatch silently
            String command = rawMessage.substring(1);
            Bukkit.dispatchCommand(event.getPlayer(), command);
            return;
        }

        // =========================
        // BLOCK PLUGIN LIST COMMANDS
        // =========================
        if (
                message.equals("/pl")
                        || message.equals("/plugins")
                        || message.equals("/bukkit:pl")
                        || message.equals("/bukkit:plugins")
                        || message.startsWith("/pl ")
                        || message.startsWith("/plugins ")
                        || message.startsWith("/bukkit:pl ")
                        || message.startsWith("/bukkit:plugins ")
        ) {

            if (!event.getPlayer().hasPermission("mcplugin.command.plugins")) {

                event.setCancelled(true);

                event.getPlayer().sendMessage(
                        MessageUtil.parse(MessagesManager.getString("plugin_hide.no_permission", "<dark_red>❌</dark_red> <red>Error: <gray>You don't have permission!</gray></red>"))
                );
            }

            return;
        }

        // =========================
        // BLOCK VERSION COMMANDS
        // =========================
        if (
                message.equals("/ver")
                        || message.equals("/version")
                        || message.equals("/icanhasbukkit")
                        || message.equals("/bukkit:ver")
                        || message.equals("/bukkit:version")
                        || message.equals("/bukkit:icanhasbukkit")
                        || message.startsWith("/ver ")
                        || message.startsWith("/version ")
                        || message.startsWith("/icanhasbukkit ")
                        || message.startsWith("/bukkit:ver ")
                        || message.startsWith("/bukkit:version ")
                        || message.startsWith("/bukkit:icanhasbukkit ")
                        || message.startsWith("/bukkit:about")
                        || message.startsWith("/bukkit:help")
                        || message.startsWith("/bukkit:?")
                        || message.startsWith("/help")
                        || message.startsWith("/?")
        ) {

            if (!event.getPlayer().hasPermission("mcplugin.command.version")) {

                event.setCancelled(true);

                event.getPlayer().sendMessage(
                        MessageUtil.parse(MessagesManager.getString("plugin_hide.no_permission", "<dark_red>❌</dark_red> <red>Error: <gray>You don't have permission!</gray></red>"))
                );
            }
        }
    }
}