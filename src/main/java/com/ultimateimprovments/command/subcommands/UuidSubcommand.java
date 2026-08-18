package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /ui uuid <name> — shows a player's UUID.
 * <p>
 *   /ui uuid <name> — UUID by nickname (online player, then cached players only;
 *                     never generates a UUID for unknown players)
 * <p>
 * Permission: ui.command.uuid
 */
public final class UuidSubcommand {

    private UuidSubcommand() {}

    private static final String PERMISSION = "ui.command.uuid";

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui uuid <player></white>"
            ));
            return true;
        }

        String name = args[1];
        UUID uuid = resolveUuid(name);
        if (uuid == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + name + "</yellow> <red>is not online or does not exist!</red>"
            ));
            return true;
        }

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>UUID of</white> <yellow>" + name
                        + "</yellow><white>:</white> <aqua>" + uuid + "</aqua>"
        ));
        return true;
    }

    /**
     * Resolves the UUID: online player first, then players cached from previous visits.
     * <p>
     * Never generates a UUID for unknown players — {@code Bukkit.getOfflinePlayer(name)}
     * would create one for a player who has never joined the server.
     */
    @SuppressWarnings("deprecation")
    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached.getUniqueId();
        }
        return null;
    }

    // =========================
    // TAB COMPLETION
    // =========================

    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
