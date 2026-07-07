package com.mcplugin.command.subcommands;

import com.mcplugin.whitelist.OpWhitelistManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /mp opwhitelist subcommand — OP whitelist control.
 * <p>
 * Usage:
 *   /mp opwhitelist list            — show whitelisted players
 *   /mp opwhitelist add <player>    — add player to whitelist
 *   /mp opwhitelist remove <player> — remove player from whitelist
 *   /mp opwhitelist on              — enable OP whitelist
 *   /mp opwhitelist off             — disable OP whitelist
 */
public final class OpWhitelistSubcommand {

    private OpWhitelistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.opwhitelist")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to use the OP whitelist!</red>"
            ));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "list" -> listWhitelist(sender);
            case "add" -> addToWhitelist(sender, args);
            case "remove", "rm", "del" -> removeFromWhitelist(sender, args);
            case "on" -> enable(sender);
            case "off" -> disable(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // ════════════════════════════════════════
    // USAGE
    // ════════════════════════════════════════
    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n" +
                "<white>/mp opwhitelist list</white> <gray>— show whitelisted players</gray>\n" +
                "<white>/mp opwhitelist add <player></white> <gray>— add player to whitelist</gray>\n" +
                "<white>/mp opwhitelist remove <player></white> <gray>— remove player from whitelist</gray>\n" +
                "<white>/mp opwhitelist on</white> <gray>— enable OP whitelist</gray>\n" +
                "<white>/mp opwhitelist off</white> <gray>— disable OP whitelist</gray>"
        ));
    }

    // ════════════════════════════════════════
    // LIST
    // ════════════════════════════════════════
    private static boolean listWhitelist(CommandSender sender) {
        List<String> names = OpWhitelistManager.getWhitelistNames();
        boolean isOn = OpWhitelistManager.isEnabled();

        String status = isOn ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>OP Whitelist</white> ═══</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Status: " + status + "</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Players (<white>" + names.size() + "</white>):</gray>"
        ));

        if (names.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("  <dark_gray>(empty)</dark_gray>"));
        } else {
            // Собираем онлайн-игроки для быстрого поиска (lowercase → Player)
            java.util.Map<String, Player> onlineByLower = new java.util.HashMap<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                onlineByLower.put(p.getName().toLowerCase(), p);
            }

            for (String name : names) {
                Player online = onlineByLower.get(name);
                String statusStr = online != null
                        ? "<green>●</green>"
                        : "<dark_gray>●</dark_gray>";
                String opStatus = (online != null && online.isOp()) ? " <gold>[OP]</gold>" : "";
                // Показываем оригинальное имя из вайтлиста (но если игрок онлайн — его реальное)
                String displayName = online != null ? online.getName() : name;
                sender.sendMessage(MessageUtil.parse(
                        "  " + statusStr + " <white>" + displayName + "</white>" + opStatus
                ));
            }
        }

        return true;
    }

    // ════════════════════════════════════════
    // ADD
    // ════════════════════════════════════════
    private static boolean addToWhitelist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp opwhitelist add <player></white>"
            ));
            return true;
        }

        String playerName = args[2];

        if (OpWhitelistManager.add(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>added to OP whitelist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Player</white> <yellow>" + playerName + "</yellow> <white>is already in the OP whitelist.</white>"
            ));
        }

        return true;
    }

    // ════════════════════════════════════════
    // REMOVE
    // ════════════════════════════════════════
    private static boolean removeFromWhitelist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp opwhitelist remove <player></white>"
            ));
            return true;
        }

        String playerName = args[2];

        if (OpWhitelistManager.remove(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>removed from OP whitelist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌</red> <white>Player</white> <yellow>" + playerName + "</yellow> <white>not found in OP whitelist.</white>"
            ));
        }

        return true;
    }

    // ════════════════════════════════════════
    // ENABLE / DISABLE
    // ════════════════════════════════════════
    private static boolean enable(CommandSender sender) {
        if (OpWhitelistManager.setEnabled(true)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>OP whitelist</white> <green>ENABLED</green><white>.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>OP whitelist is already enabled.</white>"
            ));
        }
        return true;
    }

    private static boolean disable(CommandSender sender) {
        if (OpWhitelistManager.setEnabled(false)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>OP whitelist</white> <red>DISABLED</red><white>.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>OP whitelist is already disabled.</white>"
            ));
        }
        return true;
    }

    // ════════════════════════════════════════
    // TAB COMPLETION
    // ════════════════════════════════════════
    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String action : List.of("list", "add", "remove", "on", "off")) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3) {
            String action = args[1].toLowerCase();
            if (action.equals("add") || action.equals("remove") || action.equals("rm") || action.equals("del")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
