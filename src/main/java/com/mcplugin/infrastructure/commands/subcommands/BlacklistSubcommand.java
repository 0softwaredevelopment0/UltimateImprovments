package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.blacklist.BlacklistManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 📋 BlacklistSubcommand — обработчик /mp blacklist.
 * <p>
 * Команды:
 * <pre>
 * /mp blacklist on        — включить блэклист
 * /mp blacklist off       — выключить блэклист
 * /mp blacklist add <ник> — добавить игрока
 * /mp blacklist remove <ник> — удалить игрока
 * /mp blacklist list      — показать список
 * </pre>
 */
public final class BlacklistSubcommand {

    private BlacklistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.blacklist")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to manage the blacklist!</red>"
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
                "<white>/mp blacklist on</white> <gray>— enable blacklist</gray>\n" +
                "<white>/mp blacklist off</white> <gray>— disable blacklist</gray>\n" +
                "<white>/mp blacklist add <player></white> <gray>— add player</gray>\n" +
                "<white>/mp blacklist remove <player></white> <gray>— remove player</gray>\n" +
                "<white>/mp blacklist list</white> <gray>— list blacklisted players</gray>"
        ));
    }

    // =========================
    // ON / OFF
    // =========================
    private static boolean enable(CommandSender sender) {
        if (BlacklistManager.setEnabled(true)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Blacklist</white> <green>ENABLED</green><white>.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Blacklist is already enabled.</white>"
            ));
        }
        return true;
    }

    private static boolean disable(CommandSender sender) {
        if (BlacklistManager.setEnabled(false)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Blacklist</white> <red>DISABLED</red><white>.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Blacklist is already disabled.</white>"
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
                    "<red>❌ Usage: </red><white>/mp blacklist add <player></white>"
            ));
            return true;
        }

        String playerName = args[2];
        if (BlacklistManager.add(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>added to blacklist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Player</white> <yellow>" + playerName + "</yellow> <white>is already in the blacklist.</white>"
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
                    "<red>❌ Usage: </red><white>/mp blacklist remove <player></white>"
            ));
            return true;
        }

        String playerName = args[2];
        if (BlacklistManager.remove(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>removed from blacklist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌</red> <white>Player</white> <yellow>" + playerName + "</yellow> <white>not found in blacklist.</white>"
            ));
        }
        return true;
    }

    // =========================
    // LIST
    // =========================
    private static boolean list(CommandSender sender) {
        List<String> names = BlacklistManager.getBlacklistNames();
        boolean isOn = BlacklistManager.isEnabled();

        String status = isOn ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Blacklist</white> ═══</gray>"
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
                        ? "<red>●</red>"
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
