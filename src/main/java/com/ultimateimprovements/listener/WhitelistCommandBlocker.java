package com.ultimateimprovements.listener;

import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 🚫 WhitelistCommandBlocker — блокирует ванильную команду /whitelist,
 * так как UltimateImprovements использует свой собственный вайтлист (/mp whitelist).
 * <p>
 * Перехватывает:
 * <ul>
 *   <li>{@code /whitelist}, {@code /minecraft:whitelist}, {@code /bukkit:whitelist} (любые аргументы)</li>
 *   <li>Как от игроков (PlayerCommandPreprocessEvent), так и из консоли (ServerCommandEvent)</li>
 * </ul>
 */
public class WhitelistCommandBlocker implements Listener {

    private static final String BLOCK_MESSAGE = "<red>❌ Vanilla /whitelist is disabled.</red> <gray>Use:</gray> <white>/mp whitelist</white>";

    /**
     * Проверяет, является ли команда вариантом /whitelist (с учётом неймспейсов и аргументов).
     */
    private static boolean isWhitelistCommand(String msg) {
        return msg.equals("/whitelist")
                || msg.startsWith("/whitelist ")
                || msg.equals("/minecraft:whitelist")
                || msg.startsWith("/minecraft:whitelist ")
                || msg.equals("/bukkit:whitelist")
                || msg.startsWith("/bukkit:whitelist ");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase().trim();

        if (isWhitelistCommand(msg)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.parse(BLOCK_MESSAGE));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase().trim();

        if (isWhitelistCommand("/" + command)) {
            event.setCancelled(true);
            event.getSender().sendMessage(MessageUtil.parse(BLOCK_MESSAGE));
        }
    }
}
