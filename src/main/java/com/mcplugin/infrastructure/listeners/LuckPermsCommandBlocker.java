package com.mcplugin.infrastructure.listeners;

import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 🚫 LuckPermsCommandBlocker — warns when someone tries to grant ALL permissions (*)
 * via LuckPerms commands. Granting {@code *} is a major security risk as it gives
 * unrestricted access to every permission on the server.
 * <p>
 * Intercepts commands like:
 * <ul>
 *   <li>{@code /lp user <name> permission set * true}</li>
 *   <li>{@code /lp group <name> permission set * true}</li>
 *   <li>{@code /luckperms:lp ...}, {@code /luckperms ...} variants</li>
 * </ul>
 * Both player and console commands are checked.
 */
public class LuckPermsCommandBlocker implements Listener {

    private static final String WARNING_MESSAGE =
            "<red>⚠ WARNING: You tried to grant <bold>ALL PERMISSIONS (*)</bold> via LuckPerms.</red>\n"
            + "<gray>This is <bold>extremely dangerous</bold> — it gives unrestricted access to every</gray>\n"
            + "<gray>command and feature on the server, including sensitive admin tools.</gray>\n"
            + "<gray>Malicious actors or accidental misuse can cause irreversible damage.</gray>\n"
            + "\n"
            + "<white>🚫 Command has been <bold>CANCELLED</bold>.</white>\n"
            + "<green>💡 Instead, grant only the specific permissions the user/group needs.</green>\n"
            + "<aqua>   Use: <click:copy_to_clipboard:'/lp %target% permission set <perm> true'><white>/lp &lt;user|group&gt; &lt;name&gt; permission set &lt;perm&gt; true</white></click></aqua>\n"
            + "<gray>   Example: <click:suggest_command:'/lp '><white>/lp user PlayerName permission set mcplugin.command.reload true</white></click></gray>";

    /**
     * Checks if a LuckPerms command is attempting to grant {@code *} permission.
     */
    private static boolean isGrantingStarPermission(String msg) {
        // Normalize whitespace: collapse multiple spaces/tabs into single space
        String normalized = msg.toLowerCase().trim().replaceAll("\\s+", " ");

        // Must start with a LuckPerms command prefix
        if (!normalized.startsWith("/lp") && !normalized.startsWith("/luckperms")) {
            return false;
        }

        // Must contain "set *" (granting the wildcard permission)
        // Check for " set * " or " set *" at the end of args
        // The * is the permission node being set
        return normalized.contains(" set * ") || normalized.endsWith(" set *");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase().trim();

        if (isGrantingStarPermission(msg)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.parse(WARNING_MESSAGE));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase().trim();

        if (isGrantingStarPermission("/" + command)) {
            event.setCancelled(true);
            event.getSender().sendMessage(MessageUtil.parse(WARNING_MESSAGE));
        }
    }
}
