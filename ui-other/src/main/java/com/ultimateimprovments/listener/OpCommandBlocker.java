package com.ultimateimprovments.listener;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 🚫 OpCommandBlocker — blocks the vanilla /op and /deop commands,
 * because UltimateImprovments uses its own interface (/ui op, /ui deop).
 * <p>
 * Intercepts:
 * <ul>
 *   <li>{@code /op}, {@code /deop} (with any arguments)</li>
 *   <li>{@code /minecraft:op}, {@code /minecraft:deop}</li>
 *   <li>{@code /bukkit:op}, {@code /bukkit:deop}</li>
 *   <li>Both from players (PlayerCommandPreprocessEvent) and from the console (ServerCommandEvent)</li>
 * </ul>
 */
public class OpCommandBlocker implements Listener {

    private static final String BLOCK_MESSAGE =
            "<red>Vanilla /op and /deop are disabled.</red> <gray>Use:</gray> <white>/ui op</white><gray> | </gray><white>/ui deop</white><gray> | </gray><white>/ui oplist</white>";

    /**
     * Checks whether the command is a variant of /op or /deop (considering namespaces and arguments).
     */
    private static boolean isOpCommand(String msg) {
        return msg.equals("/op")
                || msg.startsWith("/op ")
                || msg.equals("/deop")
                || msg.startsWith("/deop ")
                || msg.equals("/minecraft:op")
                || msg.startsWith("/minecraft:op ")
                || msg.equals("/minecraft:deop")
                || msg.startsWith("/minecraft:deop ")
                || msg.equals("/bukkit:op")
                || msg.startsWith("/bukkit:op ")
                || msg.equals("/bukkit:deop")
                || msg.startsWith("/bukkit:deop ");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase().trim();

        if (isOpCommand(msg)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.parse(BLOCK_MESSAGE));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase().trim();

        if (isOpCommand("/" + command)) {
            event.setCancelled(true);
            event.getSender().sendMessage(MessageUtil.parse(BLOCK_MESSAGE));
        }
    }
}
