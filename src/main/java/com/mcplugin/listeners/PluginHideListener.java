package com.mcplugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class PluginHideListener implements Listener {

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {

        String message = event.getMessage().toLowerCase().trim();

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
                        "§4❌ §cError: §7You don't have permission!"
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
                        "§4❌ §cError: §7You don't have permission!"
                );
            }
        }
    }
}