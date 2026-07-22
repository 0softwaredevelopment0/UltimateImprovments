package com.ultimateimprovements.listener;

import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 🚫 OpCommandBlocker — блокирует ванильные команды /op и /deop,
 * так как UltimateImprovements использует свой собственный интерфейс (/mp chgop).
 * <p>
 * Перехватывает:
 * <ul>
 *   <li>{@code /op}, {@code /deop} (с любыми аргументами)</li>
 *   <li>{@code /minecraft:op}, {@code /minecraft:deop}</li>
 *   <li>{@code /bukkit:op}, {@code /bukkit:deop}</li>
 *   <li>Как от игроков (PlayerCommandPreprocessEvent), так и из консоли (ServerCommandEvent)</li>
 * </ul>
 */
public class OpCommandBlocker implements Listener {

    private static final String BLOCK_MESSAGE =
            "<red>❌ Vanilla /op and /deop are disabled.</red> <gray>Use:</gray> <white>/mp chgop</white>";

    /**
     * Проверяет, является ли команда вариантом /op или /deop (с учётом неймспейсов и аргументов).
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
