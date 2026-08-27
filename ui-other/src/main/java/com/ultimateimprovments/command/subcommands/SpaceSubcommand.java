package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.space.SpaceManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /ui space enter|exit — teleport to/from space dimension.
 */
public final class SpaceSubcommand {

    private SpaceSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        if (!player.hasPermission("ui.command.space")) {
            CommandErrors.noPermission(player);
            return true;
        }

        if (!SpaceManager.isEnabled()) {
            player.sendMessage(MessageUtil.parse("<red>❌ Space dimension is not available.</red>"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String action = args[1].toLowerCase();
        return switch (action) {
            case "enter" -> {
                boolean success = SpaceManager.teleportToSpace(player);
                if (success) {
                    SpaceManager.recordVisit(player.getUniqueId());
                    player.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Launching to space...</white>"));
                }
                yield true;
            }
            case "exit" -> {
                SpaceManager.teleportFromSpace(player);
                player.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Returning from space...</white>"));
                yield true;
            }
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private static void sendUsage(Player player) {
        player.sendMessage(MessageUtil.parse(
            "<gray>═══ <white>Space Commands</white> ═══</gray>"));
        player.sendMessage(MessageUtil.parse(
            "<white>/ui space enter</white> <gray>— launch to space (or use rocket)</gray>"));
        player.sendMessage(MessageUtil.parse(
            "<white>/ui space exit</white> <gray>— return to overworld</gray>"));
    }

    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("enter", "exit").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}