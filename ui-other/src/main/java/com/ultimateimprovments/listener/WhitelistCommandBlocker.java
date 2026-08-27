package com.ultimateimprovments.listener;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 🚫 WhitelistCommandBlocker — blocks the vanilla /whitelist command,
 * because UltimateImprovments uses its own whitelist (/ui whitelist).
 * <p>
 * Intercepts:
 * <ul>
 *   <li>{@code /whitelist}, {@code /minecraft:whitelist}, {@code /bukkit:whitelist} (any arguments)</li>
 *   <li>Both from players (PlayerCommandPreprocessEvent) and from the console (ServerCommandEvent)</li>
 * </ul>
 */
public class WhitelistCommandBlocker implements Listener {

    private static final String BLOCK_MESSAGE = "<red>❌ Vanilla /whitelist is disabled.</red> <gray>Use:</gray> <white>/ui whitelist</white>";

    /**
     * Checks whether the command is a variant of /whitelist (considering namespaces and arguments).
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
