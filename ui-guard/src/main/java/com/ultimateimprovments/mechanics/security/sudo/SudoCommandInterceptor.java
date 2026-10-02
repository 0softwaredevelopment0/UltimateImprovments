package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

/**
 * 🚨 SudoCommandInterceptor — GitHub-style sudo mode.
 * <p>
 * PERMISSION MODEL (clear separation):
 * <ul>
 *   <li>{@code ui.sudo} — the right to OPEN the sudo dialog (password setup or
 *       entry). A dangerous command from a player without it is denied with
 *       error 003 (requires "ui.sudo") — the dialog never opens.</li>
 *   <li>{@code ui.command.*} — the command's own permission. It has PRIORITY:
 *       a player failing the command's base-permission probe is let through so
 *       the command itself reports error 002.</li>
 * </ul>
 * Players WITH ui.sudo get the password dialog for dangerous commands
 * ({@code /ui punish crash ...}, {@code /lp ...}, {@code /ui power off} etc. —
 * list in config); after a successful password entry the command re-runs and
 * its own permission checks (002) still apply as usual.
 */
public class SudoCommandInterceptor implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!SudoManager.isEnabled()) return;

        Player player = event.getPlayer();

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        SudoManager manager = SudoManager.getInstance();
        if (manager == null) return;

        if (!manager.isDangerous(msg)) return;

        // ── No sudo rights: 002 (command permission) beats 003 (sudo) ──
        if (!player.hasPermission("ui.sudo")) {
            List<String> base = manager.getUiBasePermissions(msg);
            if (!base.isEmpty() && base.stream().noneMatch(player::hasPermission)) {
                // Cannot run the command anyway — do not cancel; the command's
                // own guard reports error 002 with the required node.
                return;
            }
            // Could run the command, but may not open the sudo dialog → 003.
            CommandErrors.sudoRequired(player, "ui.sudo");
            event.setCancelled(true);
            return;
        }

        // Active sudo session — pass through without asking
        if (manager.isSudoActive(player.getUniqueId())) return;

        if (manager.intercept(player, event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.parse(
                    "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <gray>Enter your sudo password in the dialog to continue.</gray>"));
        }
    }
}
