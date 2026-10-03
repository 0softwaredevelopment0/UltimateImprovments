package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.whitelist.BlacklistManager;
import com.ultimateimprovments.whitelist.TimedAccessLists;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 📋 BlacklistSubcommand — handler for /ui blacklist.
 * <p>
 * Commands:
 * <pre>
 * /ui blacklist on        — enable the blacklist ([-t time] — after a delay)
 * /ui blacklist off       — disable the blacklist ([-t time] — after a delay)
 * /ui blacklist add <name> [-t time] — add a player (after a delay)
 * /ui blacklist remove <name> [-t time] — remove a player (after a delay)
 * /ui blacklist add-temp <name> [-t time] — add a player for a time
 * /ui blacklist remove-temp <name> [-t time] — remove a player for a time
 * /ui blacklist on-temp [-t time] — enable the blacklist for a time
 * /ui blacklist off-temp [-t time] — disable the blacklist for a time
 * /ui blacklist list      — show the list
 * </pre>
 * Time format: 30s, 5m, 2h, 1d. Without -t the *-temp forms behave like the
 * plain instant commands.
 */
public final class BlacklistSubcommand {

    private BlacklistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.blacklist")) {
            CommandErrors.noPermission(sender, "ui.command.blacklist");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        // Timed variants: add/remove/on/off with -t and the *-temp forms.
        Boolean timed = TimedAccessLists.handle(sender, TimedAccessLists.AccessList.BLACKLIST, action, args);
        if (timed != null) return timed;

        return switch (action) {
            case "on", "on-temp" -> enable(sender);
            case "off", "off-temp" -> disable(sender);
            case "add", "add-temp" -> add(sender, args);
            case "remove", "rm", "del", "remove-temp" -> remove(sender, args);
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
                "<white>/ui blacklist on [-t &lt;time&gt;]</white> <gray>— enable blacklist (after a delay)</gray>\n" +
                "<white>/ui blacklist off [-t &lt;time&gt;]</white> <gray>— disable blacklist (after a delay)</gray>\n" +
                "<white>/ui blacklist add &lt;player&gt; [-t &lt;time&gt;]</white> <gray>— add player (after a delay)</gray>\n" +
                "<white>/ui blacklist remove &lt;player&gt; [-t &lt;time&gt;]</white> <gray>— remove player (after a delay)</gray>\n" +
                "<white>/ui blacklist add-temp &lt;player&gt; [-t &lt;time&gt;]</white> <gray>— add player for a time</gray>\n" +
                "<white>/ui blacklist remove-temp &lt;player&gt; [-t &lt;time&gt;]</white> <gray>— remove player for a time</gray>\n" +
                "<white>/ui blacklist on-temp [-t &lt;time&gt;]</white> <gray>— enable blacklist for a time</gray>\n" +
                "<white>/ui blacklist off-temp [-t &lt;time&gt;]</white> <gray>— disable blacklist for a time</gray>\n" +
                "<white>/ui blacklist list</white> <gray>— list blacklisted players</gray>"
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
                    "<red>❌ Usage: </red><white>/ui blacklist add <player></white>"
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
                    "<red>❌ Usage: </red><white>/ui blacklist remove <player></white>"
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

        String action = args.length > 1 ? args[1].toLowerCase() : "";
        boolean playerAction = action.equals("add") || action.equals("remove") || action.equals("rm")
                || action.equals("del") || action.equals("add-temp") || action.equals("remove-temp");
        boolean stateAction = action.equals("on") || action.equals("off")
                || action.equals("on-temp") || action.equals("off-temp");

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String a : List.of("list", "add", "add-temp", "remove", "remove-temp",
                    "on", "on-temp", "off", "off-temp")) {
                if (a.startsWith(prefix)) {
                    completions.add(a);
                }
            }
        } else if (args.length == 3) {
            if (playerAction && !args[2].startsWith("-")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            } else if (playerAction || stateAction) {
                addTimeSuggestions(completions, args[args.length - 1].toLowerCase());
            }
        } else if (args.length >= 4 && args[args.length - 1].startsWith("-")) {
            addTimeSuggestions(completions, args[args.length - 1].toLowerCase());
        }

        // Value position right after a standalone "-t" — suggest durations
        if (args.length >= 3 && args[args.length - 2].equalsIgnoreCase("-t")) {
            addTimeSuggestions(completions, args[args.length - 1].toLowerCase());
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    /** Adds "-t" and common duration suggestions filtered by the given prefix. */
    private static void addTimeSuggestions(List<String> completions, String prefix) {
        for (String s : List.of("-t", "10s", "30s", "5m", "30m", "1h", "6h", "1d", "7d")) {
            if (s.startsWith(prefix)) {
                completions.add(s);
            }
        }
    }
}
