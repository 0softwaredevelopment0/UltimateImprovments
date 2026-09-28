package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * 🚨 SudoCommandInterceptor — GitHub-style sudo mode.
 * <p>
 * Intercepts dangerous commands from players with the {@code ui.sudo} permission:
 * {@code /ui punish crash ...}, any {@code /lp ...} and subcommands,
 * {@code /ui power off|reboot} etc. (list in config.yml).
 * If the sudo session is not active — the command is blocked and the player gets
 * the sudo password dialog.
 */
public class SudoCommandInterceptor implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!SudoManager.isEnabled()) return;

        Player player = event.getPlayer();

        // Only players with the ui.sudo permission are subject to sudo mode
        if (!player.hasPermission("ui.sudo")) return;

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        SudoManager manager = SudoManager.getInstance();
        if (manager == null) return;

        if (!manager.isDangerous(msg)) return;

        // Active sudo session — pass through without asking
        if (manager.isSudoActive(player.getUniqueId())) return;

        if (manager.intercept(player, event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.parse(
                    "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <gray>Enter your sudo password in the dialog to continue.</gray>"));
        }
    }
}
