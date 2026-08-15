package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /ui execchat — send a chat message on behalf of a player.
 * <p>
 * Full input simulation: the text goes through the whole normal chat pipeline
 * (AsyncPlayerChatEvent → filters, mutes, custom chat formatting),
 * and if the text starts with {@code /} — it is executed as a command
 * (the same logic as {@code Player#chat}, where the server itself routes
 * chat and commands).
 *
 * <pre>{@code
 * /ui execchat Rizer "hello everyone"
 * /ui execchat Rizer "/home"        // executes /home as Rizer
 * }</pre>
 * <p>
 * Semantics:
 * <ul>
 *   <li>Commands run with the TARGET's permissions (as if the player typed it themselves),
 *       not the sender's — no privilege escalation.</li>
 *   <li>Chat messages go through the normal checks: mutes and filters apply,
 *       so a muted player's message will be blocked.</li>
 *   <li>Command permission: {@code ui.command.execchat} (default: false).</li>
 * </ul>
 */
public final class ExecChatSubcommand {

    private ExecChatSubcommand() {}

    /**
     * Joins all arguments starting from startIndex into a single string
     * and strips surrounding quotes: {@code "/home test"} → {@code /home test}.
     */
    private static String joinMessage(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        String message = sb.toString().trim();
        if (message.length() >= 2
                && ((message.startsWith("\"") && message.endsWith("\""))
                    || (message.startsWith("'") && message.endsWith("'")))) {
            message = message.substring(1, message.length() - 1).trim();
        }
        return message;
    }

    @SuppressWarnings("deprecation") // Player#chat — the only correct way to fully simulate input in 1.21+
    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.execchat")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui execchat <player> \"<message>\"</white>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player </red><yellow>" + args[1] + "</yellow><red> not found or offline!</red>"));
            return true;
        }

        String message = joinMessage(args, 2);
        if (message.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Message cannot be empty!</red>"));
            return true;
        }

        // Full input simulation: "/..." → command, otherwise → chat (all filters/mutes apply)
        target.chat(message);

        String kind = message.startsWith("/") ? "command" : "chat message";
        ConsoleLogger.info("[ExecChat] " + sender.getName() + " made " + target.getName()
                + " send " + kind + ": " + message);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Executed as </white><yellow>" + target.getName()
                        + "</yellow><white> (" + kind + "): </white><gray>" + message + "</gray>"));
        return true;
    }

    public static List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        } else if (args.length == 3) {
            result.add("\"message\"");
            result.add("\"/command\"");
        }
        return result;
    }
}
