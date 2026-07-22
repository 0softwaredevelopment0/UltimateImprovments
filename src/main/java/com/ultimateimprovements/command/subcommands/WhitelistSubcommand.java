package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.whitelist.WhitelistManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 📋 WhitelistSubcommand — обработчик /mp whitelist.
 * <p>
 * Команды:
 * <pre>
 * /mp whitelist on        — включить вайтлист
 * /mp whitelist off       — выключить вайтлист
 * /mp whitelist add <ник> — добавить игрока
 * /mp whitelist remove <ник> — удалить игрока
 * /mp whitelist list      — показать список
 * </pre>
 */
public final class WhitelistSubcommand {

    private WhitelistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.whitelist")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to manage the whitelist!</red>"
            ));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "on" -> enable(sender);
            case "off" -> disable(sender);
            case "add" -> add(sender, args);
            case "remove", "rm", "del" -> remove(sender, args);
            case "list" -> list(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // =========================
    // USAGE
    // =========================
    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n" +
                "<white>/mp whitelist on</white> <gray>— enable whitelist</gray>\n" +
                "<white>/mp whitelist off</white> <gray>— disable whitelist</gray>\n" +
                "<white>/mp whitelist add <player></white> <gray>— add player</gray>\n" +
                "<white>/mp whitelist remove <player></white> <gray>— remove player</gray>\n" +
                "<white>/mp whitelist list</white> <gray>— list whitelisted players</gray>"
        ));
    }

    // =========================
    // ON / OFF
    // =========================
    private static boolean enable(CommandSender sender) {
        if (WhitelistManager.setEnabled(true)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Whitelist</white> <green>ENABLED</green><white>.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Whitelist is already enabled.</white>"
            ));
        }
        return true;
    }

    private static boolean disable(CommandSender sender) {
        if (WhitelistManager.setEnabled(false)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Whitelist</white> <red>DISABLED</red><white>.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Whitelist is already disabled.</white>"
            ));
        }
        return true;
    }

    // =========================
    // ADD
    // =========================
    private static boolean add(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp whitelist add <player></white>"
            ));
            return true;
        }

        String playerName = args[2];
        if (WhitelistManager.add(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>added to whitelist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Player</white> <yellow>" + playerName + "</yellow> <white>is already in the whitelist.</white>"
            ));
        }
        return true;
    }

    // =========================
    // REMOVE
    // =========================
    private static boolean remove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp whitelist remove <player></white>"
            ));
            return true;
        }

        String playerName = args[2];
        if (WhitelistManager.remove(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>removed from whitelist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌</red> <white>Player</white> <yellow>" + playerName + "</yellow> <white>not found in whitelist.</white>"
            ));
        }
        return true;
    }

    // =========================
    // LIST
    // =========================
    private static boolean list(CommandSender sender) {
        List<String> names = WhitelistManager.getWhitelistNames();
        boolean isOn = WhitelistManager.isEnabled();

        String status = isOn ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Whitelist</white> ═══</gray>"
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
            for (String name : names) {
                Player online = Bukkit.getPlayerExact(name);
                String statusStr = online != null && online.isOnline()
                        ? "<green>●</green>"
                        : "<dark_gray>●</dark_gray>";
                sender.sendMessage(MessageUtil.parse(
                        "  " + statusStr + " <white>" + name + "</white>"
                ));
            }
        }

        return true;
    }

    // =========================
    // TAB COMPLETION
    // =========================
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
