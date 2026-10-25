package com.ultimateimprovments.mechanics.security.gsit;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * 🛡 GSitSpinGuard — protection against the GSit version-mismatch bug.
 * <p>
 * On a server whose GSit build does not match the server/client version,
 * {@code /spin} can leave the player INVISIBLE for everyone else. The bug is
 * inside GSit (it sends a pose/metadata packet the other side does not
 * understand), so the only safe server-side fix is not to run the command.
 * <p>
 * The guard is opt-in ({@code gsit_spin_guard.enabled}, default {@code false})
 * and has two modes ({@code gsit_spin_guard.mode}, default {@code 1}):
 * <ul>
 *   <li>{@code 1} — {@code /spin} (and its GSit alias {@code /gspin}) requires
 *       the explicit permission {@value Permissions#UI_GSIT_SPIN}; without it
 *       the player receives error 002 (insufficient permissions).</li>
 *   <li>{@code 2} — the command is disabled for everyone; the player receives
 *       error 011 (command disabled on this server).</li>
 * </ul>
 * Console and command blocks are not intercepted: GSit's spin is player-only.
 */
public class GSitSpinGuard implements Listener {

    /** Mode 1 — /spin requires {@link Permissions#UI_GSIT_SPIN} (error 002). */
    public static final int MODE_PERMISSION = 1;
    /** Mode 2 — /spin is disabled for everyone (error 011). */
    public static final int MODE_DISABLED = 2;

    /** Master switch ({@code gsit_spin_guard.enabled}, default false). */
    public static boolean isEnabled() {
        return cfg().getBoolean("gsit_spin_guard.enabled", false);
    }

    /** Active protection mode; anything but 2 falls back to mode 1. */
    public static int getMode() {
        return cfg().getInt("gsit_spin_guard.mode", MODE_PERMISSION) == MODE_DISABLED
                ? MODE_DISABLED : MODE_PERMISSION;
    }

    private static FileConfiguration cfg() {
        return Main.getInstance().getConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpinCommand(PlayerCommandPreprocessEvent event) {
        if (!isEnabled() || !isSpinCommand(event.getMessage())) return;

        Player player = event.getPlayer();
        if (getMode() == MODE_DISABLED) {
            event.setCancelled(true);
            CommandErrors.commandDisabled(player);
            return;
        }
        if (!player.hasPermission(Permissions.UI_GSIT_SPIN)) {
            event.setCancelled(true);
            CommandErrors.noPermission(player, Permissions.UI_GSIT_SPIN);
        }
    }

    /**
     * Whether a raw command line invokes GSit's spin command. Accepts
     * {@code /spin}, {@code /gspin} (GSit alias), namespace-qualified forms
     * such as {@code /gsit:spin}, arbitrary case and trailing arguments.
     */
    static boolean isSpinCommand(String message) {
        if (message == null) return false;
        String normalized = message.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String label = normalized.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        return label.equals("spin") || label.equals("gspin");
    }
}
