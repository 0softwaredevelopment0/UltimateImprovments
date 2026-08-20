package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.report.ReportManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /ui report <player> <reason> — submitting a report about a player.
 */
public final class ReportSubcommand {

    private ReportSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!player.hasPermission("ui.command.report")) {
            CommandErrors.noPermission(player);
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui report <player> <reason></white>"));
            return true;
        }

        String targetName = args[1];
        // Collect the reason from the remaining arguments
        StringBuilder reason = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (reason.length() > 0) reason.append(" ");
            reason.append(args[i]);
        }

        String error = ReportManager.createReport(player, targetName, reason.toString());
        if (error != null) {
            player.sendMessage(MessageUtil.parse(error));
        } else {
            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("report.success",
                            "<green>✔</green> <white>Report against </white><yellow>%player%</yellow><white> submitted!</white>")
                            .replace("%player%", targetName)));
        }

        return true;
    }

    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            // Player names
            return filterByInput(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                    args[1]);
        }
        // args.length >= 3 — reason (free text, no suggestions)
        return List.of();
    }

    private static List<String> filterByInput(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
