package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.whitelist.TimedAccessLists;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AccessListCommands — shared command engine for the four access lists
 * (/ui whitelist, /ui blacklist, /ui opwhitelist, /ui opblacklist).
 * <p>
 * Commands (identical for every list):
 * <pre>
 * /ui &lt;list&gt; on [-t &lt;time&gt;]                    — enable (after a delay)
 * /ui &lt;list&gt; off [-t &lt;time&gt;]                   — disable (after a delay)
 * /ui &lt;list&gt; add &lt;player&gt; [-t &lt;time&gt;]          — add (after a delay)
 * /ui &lt;list&gt; remove &lt;player&gt; [-t &lt;time&gt;]       — remove (after a delay)
 * /ui &lt;list&gt; add-temp &lt;player&gt; -t &lt;time&gt;       — add for a time
 * /ui &lt;list&gt; remove-temp &lt;player&gt; -t &lt;time&gt;    — remove for a time
 * /ui &lt;list&gt; on-temp -t &lt;time&gt;                 — enable for a time
 * /ui &lt;list&gt; off-temp -t &lt;time&gt;                — disable for a time
 * /ui &lt;list&gt; list                              — show the list
 * </pre>
 * Time format: 30s, 5m, 2h, 1d. Temp commands REQUIRE -t.
 * <p>
 * Opposite actions cancel pending schedules: an instant/delayed ADD kills a
 * pending REMOVE for that player (and vice versa); an instant/delayed ENABLE
 * kills a pending DISABLE for the list state (and vice versa). Scheduling the
 * same effect again replaces the previous schedule.
 */
public final class AccessListCommands {

    private AccessListCommands() {}

    // =========================
    // DISPATCH
    // =========================

    /**
     * Executes a list command. The permission check is done by the caller.
     *
     * @param list   the access list to operate on
     * @param args   full command args ({@code args[0]} = the list name)
     * @param opTag  whether the list view shows the [OP] tag (OP lists)
     */
    public static boolean execute(CommandSender sender, TimedAccessLists.AccessList list,
                                  String[] args, boolean opTag) {
        if (args.length < 2) {
            sendUsage(sender, list);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        return switch (action) {
            case "on", "on-temp" -> stateCommand(sender, list, args, true, action.endsWith("-temp"));
            case "off", "off-temp" -> stateCommand(sender, list, args, false, action.endsWith("-temp"));
            case "add", "add-temp", "remove", "remove-temp", "rm", "del" -> playerCommand(sender, list, args, action);
            case "list" -> listView(sender, list, opTag);
            default -> {
                sendUsage(sender, list);
                yield true;
            }
        };
    }

    // =========================
    // PLAYER COMMANDS
    // =========================

    private static boolean playerCommand(CommandSender sender, TimedAccessLists.AccessList list,
                                         String[] args, String action) {
        boolean temp = action.endsWith("-temp");
        String base = switch (action) {
            case "rm", "del" -> "remove";
            case "add-temp", "remove-temp" -> action.substring(0, action.length() - 5);
            default -> action;
        };
        boolean addEffect = base.equals("add");

        if (args.length < 3) {
            usageLine(sender, list, action, true, temp);
            return true;
        }
        String player = args[2];

        Long delay = TimedAccessLists.parseTimeFlag(sender, args, 3);
        if (delay == null) return true; // malformed flag — error already sent

        if (temp && delay == 0) {
            // Temp commands are meaningless without a time — require the flag.
            sender.sendMessage(TimedAccessLists.timedMessage(list, "temp_requires_time", null, null));
            return true;
        }

        // The latest intent wins: kill the opposing pending schedule first.
        TimedAccessLists.cancelOpposingPlayer(list, player, addEffect);

        // Instant (legacy behavior)
        if (!temp && delay == 0) {
            boolean ok = addEffect ? list.add(player) : list.remove(player);
            if (addEffect) {
                if (ok) {
                    sender.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Player</white> <yellow>" + player + "</yellow> <white>added to "
                                    + list.title() + ".</white>"));
                } else {
                    sender.sendMessage(MessageUtil.parse(
                            "<yellow>⚠</yellow> <white>Player</white> <yellow>" + player
                                    + "</yellow> <white>is already in the " + list.title() + ".</white>"));
                }
            } else {
                if (ok) {
                    sender.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Player</white> <yellow>" + player + "</yellow> <white>removed from "
                                    + list.title() + ".</white>"));
                } else {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌</red> <white>Player</white> <yellow>" + player + "</yellow> <white>not found in "
                                    + list.title() + ".</white>"));
                }
            }
            return true;
        }

        // Delayed and temp: the instant/delayed application must actually
        // change something, otherwise report the conflict.
        boolean conflict = addEffect ? list.contains(player) : !list.contains(player);
        if (conflict) {
            sender.sendMessage(TimedAccessLists.timedMessage(list,
                    addEffect ? "already_in_list" : "not_in_list", player, null));
            return true;
        }

        // Temp: apply now, auto-revert after the delay
        if (temp) {
            if (addEffect) {
                list.add(player);
            } else {
                list.remove(player);
            }
            TimedAccessLists.schedulePlayer(list, player, !addEffect, delay);
            sender.sendMessage(TimedAccessLists.timedMessage(list, addEffect ? "add_temp" : "remove_temp", player, delay));
            return true;
        }

        // Delayed: apply after the delay
        TimedAccessLists.schedulePlayer(list, player, addEffect, delay);
        sender.sendMessage(TimedAccessLists.timedMessage(list,
                addEffect ? "add_scheduled" : "remove_scheduled", player, delay));
        return true;
    }

    // =========================
    // STATE COMMANDS (on / off)
    // =========================

    private static boolean stateCommand(CommandSender sender, TimedAccessLists.AccessList list,
                                        String[] args, boolean enableIntent, boolean temp) {
        Long delay = TimedAccessLists.parseTimeFlag(sender, args, 2);
        if (delay == null) return true; // malformed flag — error already sent

        if (temp && delay == 0) {
            sender.sendMessage(TimedAccessLists.timedMessage(list, "temp_requires_time", null, null));
            return true;
        }

        // The latest intent wins: kill the opposing pending state first.
        TimedAccessLists.cancelOpposingState(list, enableIntent);

        // Instant (legacy behavior)
        if (!temp && delay == 0) {
            if (list.setEnabled(enableIntent)) {
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>" + list.title() + "</white> "
                                + (enableIntent ? "<green>ENABLED</green>" : "<red>DISABLED</red>")
                                + "<white>.</white>"));
            } else {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>" + list.title() + " is already "
                                + (enableIntent ? "enabled" : "disabled") + ".</white>"));
            }
            return true;
        }

        // Delayed and temp: report the conflict when the list is already in
        // the requested state and nothing is pending.
        if (list.isEnabled() == enableIntent && !TimedAccessLists.hasStatePending(list)) {
            sender.sendMessage(TimedAccessLists.timedMessage(list,
                    enableIntent ? "already_enabled" : "already_disabled", null, null));
            return true;
        }

        // Temp: apply now, auto-revert after the delay
        if (temp) {
            list.setEnabled(enableIntent);
            TimedAccessLists.scheduleState(list, !enableIntent, delay);
            sender.sendMessage(TimedAccessLists.timedMessage(list, enableIntent ? "on_temp" : "off_temp", null, delay));
            return true;
        }

        // Delayed: apply after the delay
        TimedAccessLists.scheduleState(list, enableIntent, delay);
        sender.sendMessage(TimedAccessLists.timedMessage(list,
                enableIntent ? "on_scheduled" : "off_scheduled", null, delay));
        return true;
    }

    // =========================
    // LIST VIEW
    // =========================

    private static boolean listView(CommandSender sender, TimedAccessLists.AccessList list, boolean opTag) {
        List<String> names = list.getNames();
        boolean isOn = list.isEnabled();

        String status = isOn ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
        String dotOnline = list.dangerList() ? "<red>●</red>" : "<green>●</green>";
        String dotOffline = "<dark_gray>●</dark_gray>";

        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>" + list.title() + "</white> ═══</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Status: " + status + "</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Players (<white>" + names.size() + "</white>):</gray>"));

        if (names.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("  <dark_gray>(empty)</dark_gray>"));
        } else {
            Map<String, Player> onlineByLower = new java.util.HashMap<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                onlineByLower.put(p.getName().toLowerCase(Locale.ROOT), p);
            }

            for (String name : names) {
                Player online = onlineByLower.get(name.toLowerCase(Locale.ROOT));
                String dot = online != null ? dotOnline : dotOffline;
                String opStatus = opTag && online != null && online.isOp() ? " <gold>[OP]</gold>" : "";
                String displayName = online != null ? online.getName() : name;
                sender.sendMessage(MessageUtil.parse(
                        "  " + dot + " <white>" + displayName + "</white>" + opStatus));
            }
        }

        return true;
    }

    // =========================
    // USAGE
    // =========================

    private static void sendUsage(CommandSender sender, TimedAccessLists.AccessList list) {
        String cmd = "/ui " + list.key();
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n" +
                "<white>" + cmd + " on [-t &lt;time&gt;]</white> <gray>— enable (after a delay)</gray>\n" +
                "<white>" + cmd + " off [-t &lt;time&gt;]</white> <gray>— disable (after a delay)</gray>\n" +
                "<white>" + cmd + " add &lt;player&gt; [-t &lt;time&gt;]</white> <gray>— add player (after a delay)</gray>\n" +
                "<white>" + cmd + " remove &lt;player&gt; [-t &lt;time&gt;]</white> <gray>— remove player (after a delay)</gray>\n" +
                "<white>" + cmd + " add-temp &lt;player&gt; -t &lt;time&gt;</white> <gray>— add player for a time</gray>\n" +
                "<white>" + cmd + " remove-temp &lt;player&gt; -t &lt;time&gt;</white> <gray>— remove player for a time</gray>\n" +
                "<white>" + cmd + " on-temp -t &lt;time&gt;</white> <gray>— enable for a time</gray>\n" +
                "<white>" + cmd + " off-temp -t &lt;time&gt;</white> <gray>— disable for a time</gray>\n" +
                "<white>" + cmd + " list</white> <gray>— list players</gray>"
        ));
    }

    private static void usageLine(CommandSender sender, TimedAccessLists.AccessList list,
                                  String action, boolean player, boolean temp) {
        String timePart = temp ? "-t &lt;time&gt;" : "[-t &lt;time&gt;]";
        String target = player ? " &lt;player&gt;" : "";
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage: </red><white>/ui " + list.key() + " " + action + target + " " + timePart + "</white>"));
    }

    // =========================
    // TAB COMPLETION
    // =========================

    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        boolean playerAction = action.equals("add") || action.equals("remove") || action.equals("rm")
                || action.equals("del") || action.equals("add-temp") || action.equals("remove-temp");
        boolean stateAction = action.equals("on") || action.equals("off")
                || action.equals("on-temp") || action.equals("off-temp");

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
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
                addTimeSuggestions(completions, args[args.length - 1].toLowerCase(Locale.ROOT));
            }
        } else if (args.length >= 4 && args[args.length - 1].startsWith("-")) {
            addTimeSuggestions(completions, args[args.length - 1].toLowerCase(Locale.ROOT));
        }

        // Value position right after a standalone "-t" — suggest durations
        if (args.length >= 3 && args[args.length - 2].equalsIgnoreCase("-t")) {
            addTimeSuggestions(completions, args[args.length - 1].toLowerCase(Locale.ROOT));
        }

        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
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
