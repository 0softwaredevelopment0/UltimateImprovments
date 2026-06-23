package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class BroadcastSubcommand {

    private static final String PREFIX = "<gray>[<white>Server</white><dark_gray>/</dark_gray><white>Info</white>]</gray> ";

    private BroadcastSubcommand() {}

    /**
     * Joins all args starting from startIndex into one string,
     * excluding the -clean flag.
     */
    private static String parseMessage(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].equals("-clean")) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /**
     * Checks if -clean flag is present in args from startIndex onward.
     */
    private static boolean hasCleanFlag(String[] args, int startIndex) {
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].equals("-clean")) {
                return true;
            }
        }
        return false;
    }

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage:</red> <white>/mp bc \"<message>\" [-clean]</white>"));
            return true;
        }

        if (!sender.hasPermission("mcplugin.command.broadcast")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to broadcast!</red>"));
            return true;
        }

        boolean clean = hasCleanFlag(args, 2);
        String message = parseMessage(args, 1);

        if (message.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Message cannot be empty!</red>"));
            return true;
        }

        // Build final MiniMessage string
        String fullMessage = clean ? message : PREFIX + message;

        Component component = MessageUtil.parse(fullMessage);

        // Broadcast to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }

        // Log to console
        Bukkit.getConsoleSender().sendMessage(component);

        return true;
    }

    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            completions.add("\"message\"");
        } else if (args.length >= 3) {
            boolean hasClean = false;
            for (int i = 2; i < args.length; i++) {
                if (args[i].equals("-clean")) {
                    hasClean = true;
                    break;
                }
            }
            if (!hasClean) {
                completions.add("-clean");
            }
        }

        return completions;
    }
}
