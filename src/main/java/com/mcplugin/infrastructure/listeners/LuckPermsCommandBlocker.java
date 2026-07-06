package com.mcplugin.infrastructure.listeners;

import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ⚠ LuckPermsCommandBlocker — warns when someone tries to grant ALL permissions (*)
 * via LuckPerms, and requires re-typing the command within 15 seconds to confirm.
 * <p>
 * Granting {@code *} is a major security risk — it gives unrestricted access to every
 * command and feature on the server, including sensitive admin tools.
 * <p>
 * Intercepts commands like:
 * <ul>
 *   <li>{@code /lp user <name> permission set * true}</li>
 *   <li>{@code /lp group <name> permission set * true}</li>
 *   <li>{@code /luckperms:lp ...}, {@code /luckperms ...} variants</li>
 * </ul>
 * <p>
 * Flow:
 * <ol>
 *   <li>First attempt → blocked, warning shown, player told to re-type to confirm</li>
 *   <li>Second attempt within 15s → allowed through</li>
 * </ol>
 * Console commands are only warned (no confirmation needed — console is trusted).
 */
public class LuckPermsCommandBlocker implements Listener {

    /** Confirmation timeout in milliseconds. */
    private static final long CONFIRM_TIMEOUT_MS = 15_000L;

    /** Tracks players who have been warned and are awaiting confirmation. */
    private final Map<UUID, Long> pendingConfirmations = new HashMap<>();

    private static final String WARNING_MESSAGE_BODY =
            "<red>⚠ <bold>WARNING:</bold> You are about to grant <bold>ALL PERMISSIONS (*)</bold> via LuckPerms.</red>\n"
            + "<gray>This is <bold>extremely dangerous</bold> — it gives unrestricted access to every</gray>\n"
            + "<gray>command and feature on the server, including sensitive admin tools.</gray>\n"
            + "<gray>Once granted, malicious actors or accidental misuse can </gray><red>cause irreversible damage</red><gray>.</gray>\n"
            + "\n"
            + "<green>💡 <bold>Strongly recommended:</bold> Grant only the specific permissions needed.</green>\n"
            + "<gray>   Example: <white>/lp user <name> permission set mcplugin.command.reload true</white></gray>\n"
            + "\n"
            + "<yellow>⚠ If you are <bold>absolutely sure</bold> you want to grant <bold>*</bold> —</yellow>\n"
            + "<yellow>  <bold>type the same command again</bold> within 15 seconds to confirm.</yellow>\n"
            + "<dark_red>  Otherwise, the command will be cancelled.</dark_red>";

    private static final String CONFIRMED_MESSAGE =
            "<green>✅ <bold>Confirmed.</bold> Executing LuckPerms command with wildcard permission.</green>\n"
            + "<gray>  Please double-check that this is what you intended.</gray>";

    private static final String WARNING_CONSOLE =
            "<red>⚠ WARNING: You are about to grant ALL PERMISSIONS (*) via LuckPerms.</red>\n"
            + "<gray>This is extremely dangerous — grant only specific permissions when possible.</gray>\n"
            + "<gray>  Use: /lp <user|group> <name> permission set <perm> true</gray>";

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
        return normalized.contains(" set * ") || normalized.endsWith(" set *");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase().trim();

        if (!isGrantingStarPermission(msg)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if this is a confirmation (player already warned and is re-typing)
        Long warnedAt = pendingConfirmations.remove(uuid);
        if (warnedAt != null && (System.currentTimeMillis() - warnedAt) < CONFIRM_TIMEOUT_MS) {
            // Confirmed — let the command through
            player.sendMessage(MessageUtil.parse(CONFIRMED_MESSAGE));
            return;
        }

        // First attempt — block and warn
        event.setCancelled(true);

        // Clean expired entry before inserting new one
        Long existing = pendingConfirmations.get(uuid);
        if (existing != null && (System.currentTimeMillis() - existing) >= CONFIRM_TIMEOUT_MS) {
            pendingConfirmations.remove(uuid);
        }
        pendingConfirmations.put(uuid, System.currentTimeMillis());

        player.sendMessage(MessageUtil.parse(WARNING_MESSAGE_BODY));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase().trim();

        if (isGrantingStarPermission("/" + command)) {
            // Console is trusted — show warning but do NOT block
            event.getSender().sendMessage(MessageUtil.parse(WARNING_CONSOLE));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up pending confirmation on disconnect
        pendingConfirmations.remove(event.getPlayer().getUniqueId());
    }
}
