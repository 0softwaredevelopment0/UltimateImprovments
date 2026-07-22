package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.maintenance.MaintenanceManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /mp maint subcommand — maintenance mode control.
 * <p>
 * Usage:
 *   /mp maint list           — show whitelisted players
 *   /mp maint add <player>   — add player to whitelist
 *   /mp maint remove <player> — remove player from whitelist
 *   /mp maint on [-time:<Ns|Nm|Nh|Nd>]   — enable maintenance (optionally with delay)
 *   /mp maint off [-time:<Ns|Nm|Nh|Nd>]  — disable maintenance (optionally scheduled)
 */
public final class MaintSubcommand {

    private MaintSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.maintenance")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use maintenance commands!</red>")));
            return true;
        }

        // Проверка: включена ли фича техработ в config.yml
        if (!MaintenanceManager.getInstance().isFeatureEnabled()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Maintenance system is disabled in config!</red>"));
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
            case "on" -> enableMaintenance(sender, args);
            case "off" -> disableMaintenance(sender, args);
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
                "<white>/mp maint list</white> <gray>— show whitelisted players</gray>\n" +
                "<white>/mp maint add <player></white> <gray>— add player to whitelist</gray>\n" +
                "<white>/mp maint remove <player></white> <gray>— remove player from whitelist</gray>\n" +
                "<white>/mp maint on [-time:30s|5m|2h|1d]</white> <gray>— enable maintenance mode</gray>\n" +
                "<white>/mp maint off [-time:30s|5m|2h|1d]</white> <gray>— disable maintenance mode</gray>"
        ));
    }

    // =========================
    // LIST
    // =========================

    private static boolean listWhitelist(CommandSender sender) {
        var mm = MaintenanceManager.getInstance();
        List<String> whitelist = mm.getWhitelistNames();
        boolean isOn = mm.isMaintenanceMode();

        String status = isOn ? "<red>⛏ ENABLED</red>" : "<green>✔ DISABLED</green>";
        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Maintenance Mode</white> ═══</gray>"
        ));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Status: " + status + "</gray>"
        ));

        if (mm.hasScheduledTask()) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⏰ Scheduled task pending</yellow>"
            ));
        }

        sender.sendMessage(MessageUtil.parse(
                "<gray>Whitelist (<white>" + whitelist.size() + "</white>):</gray>"
        ));

        if (whitelist.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "  <dark_gray>(empty)</dark_gray>"
            ));
        } else {
            for (String name : whitelist) {
                // Check if player is online
                Player p = Bukkit.getPlayerExact(name);
                String statusStr = (p != null && p.isOnline())
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
    // ADD
    // =========================

    private static boolean addToWhitelist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp maint add <player></white>"
            ));
            return true;
        }

        String playerName = args[2];
        var mm = MaintenanceManager.getInstance();

        if (mm.addWhitelist(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>added to maintenance whitelist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Player</white> <yellow>" + playerName + "</yellow> <white>is already whitelisted.</white>"
            ));
        }

        return true;
    }

    // =========================
    // REMOVE
    // =========================

    private static boolean removeFromWhitelist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp maint remove <player></white>"
            ));
            return true;
        }

        String playerName = args[2];
        var mm = MaintenanceManager.getInstance();

        if (mm.removeWhitelist(playerName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + playerName + "</yellow> <white>removed from maintenance whitelist.</white>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌</red> <white>Player</white> <yellow>" + playerName + "</yellow> <white>not found in whitelist.</white>"
            ));
        }

        return true;
    }

    // =========================
    // ENABLE
    // =========================

    private static boolean enableMaintenance(CommandSender sender, String[] args) {
        var mm = MaintenanceManager.getInstance();

        // Check for -time flag
        String timeStr = extractTimeFlag(args, 2);
        if (timeStr != null) {
            long ticks = MaintenanceManager.parseTimeToTicks(timeStr);
            if (ticks <= 0) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid time format! Use: </red><white>-time:30s</white><gray>, </gray><white>5m</white><gray>, </gray><white>2h</white><gray>, </gray><white>1d</white>"
                ));
                return true;
            }
            mm.enableLater(ticks);
            // Message is broadcast by enableLater
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Scheduled maintenance</white> <green>ENABLE</green> <white>in </white><yellow>" + timeStr + "</yellow>"
            ));
        } else {
            if (mm.isMaintenanceMode()) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Maintenance mode is already enabled.</white>"
                ));
                return true;
            }
            mm.enable();
            // Message is broadcast by enable()
        }

        return true;
    }

    // =========================
    // DISABLE
    // =========================

    private static boolean disableMaintenance(CommandSender sender, String[] args) {
        var mm = MaintenanceManager.getInstance();

        // Check for -time flag
        String timeStr = extractTimeFlag(args, 2);
        if (timeStr != null) {
            long ticks = MaintenanceManager.parseTimeToTicks(timeStr);
            if (ticks <= 0) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid time format! Use: </red><white>-time:30s</white><gray>, </gray><white>5m</white><gray>, </gray><white>2h</white><gray>, </gray><white>1d</white>"
                ));
                return true;
            }
            mm.disableLater(ticks);
            // Message is broadcast by disableLater
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Scheduled maintenance</white> <red>DISABLE</red> <white>in </white><yellow>" + timeStr + "</yellow>"
            ));
        } else {
            if (!mm.isMaintenanceMode()) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Maintenance mode is already disabled.</white>"
                ));
                return true;
            }
            mm.disable();
            // Message is broadcast by disable()
        }

        return true;
    }

    // =========================
    // TIME FLAG PARSING
    // =========================

    /**
     * Extracts the -time flag from args, starting from startIndex.
     * @return the time value string (e.g. "30s", "5m"), or null if not found
     */
    private static String extractTimeFlag(String[] args, int startIndex) {
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.startsWith("-time:")) {
                return arg.substring(6); // Remove "-time:"
            }
        }
        return null;
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
            switch (action) {
                case "add":
                case "remove":
                case "rm":
                case "del":
                    // Suggest online players
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                    break;
                case "on":
                case "off":
                    // Suggest time flags
                    String last = args[2].toLowerCase();
                    if (!last.startsWith("-time:")) {
                        completions.add("-time:30s");
                        completions.add("-time:5m");
                        completions.add("-time:10m");
                        completions.add("-time:1h");
                        completions.add("-time:2h");
                        completions.add("-time:1d");
                    }
                    break;
            }
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
