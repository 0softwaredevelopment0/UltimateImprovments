package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.util.Broadcast;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class BroadcastSubcommand {

    private BroadcastSubcommand() {}

    /**
     * Joins all args starting from startIndex into one string,
     * excluding the -clean flag. Strips surrounding quotes so that
     * {@code /ui broadcast "<red>test"} doesn't show the quotes in chat.
     */
    private static String parseMessage(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].equals("-clean")) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        String message = sb.toString().trim();
        // Strip surrounding quotes if present: "<red>test" → <red>test
        if (message.length() >= 2
                && ((message.startsWith("\"") && message.endsWith("\""))
                    || (message.startsWith("'") && message.endsWith("'")))) {
            message = message.substring(1, message.length() - 1).trim();
        }
        return message;
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
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage:</red> <white>/ui broadcast \"<message>\" [-clean]</white>"));
            return true;
        }

        if (!sender.hasPermission("ui.command.broadcast")) {
            CommandErrors.noPermission(sender);
            return true;
        }

        boolean clean = hasCleanFlag(args, 2);
        String message = parseMessage(args, 1);

        if (message.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Message cannot be empty!</red>"));
            return true;
        }

        // Resolve placeholders (%tps_avg_1s_color%, %online%, etc.)
        Player senderPlayer = sender instanceof Player ? (Player) sender : null;
        String resolved = PlaceholderResolver.resolve(message, senderPlayer);

        // Broadcast: -clean → embedded without prefix, otherwise the "[UI] " prefix
        if (clean) {
            Broadcast.sendEmbedded(resolved);
        } else {
            Broadcast.sendServer(resolved);
        }

        // Log to console (PREFIX already ends with a single space)
        String fullMessage = (clean ? "" : MessageUtil.PREFIX) + resolved;
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(fullMessage));

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
