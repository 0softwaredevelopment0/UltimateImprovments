package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.command.PowerManager;
import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PowerSubcommand {

    private PowerSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Error: </red><gray>Usage: /mp power off|reboot|confirm|undo</gray>"));
            return true;
        }

        PowerManager pm = PowerManager.getInstance();

        return switch (args[1].toLowerCase()) {
            case "off" -> handleOff(sender, pm);
            case "reboot" -> handleReboot(sender, pm);
            case "confirm" -> handleConfirm(sender, pm);
            case "undo" -> handleUndo(sender, pm);
            default -> {
                sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Error: </red><gray>Usage: /mp power off|reboot|confirm|undo</gray>"));
                yield true;
            }
        };
    }

    private static boolean handleOff(CommandSender sender, PowerManager pm) {
        if (!(sender instanceof Player)) { pm.executeDirect(false); return true; }
        Player player = (Player) sender;
        if (!player.hasPermission("mcplugin.command.power.off")) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to shut down the server!</red>")); return true;
        }
        if (pm.hasPendingRequest()) { sender.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>There is already an active power management request.</red>")); return true; }
        pm.requestStop(player.getName(), player.getUniqueId());
        player.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Server shutdown initiated, waiting for console confirmation.</yellow>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Server shutdown requested by player </yellow><white>" + player.getName() + "</white><yellow>. Confirm with: </yellow><white>/mp power confirm</white>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Cancel: </yellow><white>/mp power undo</white>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Request will be automatically cancelled after </yellow><white>30</white><yellow> seconds.</yellow>"));
        return true;
    }

    private static boolean handleReboot(CommandSender sender, PowerManager pm) {
        if (!(sender instanceof Player)) { pm.executeDirect(true); return true; }
        Player player = (Player) sender;
        if (!player.hasPermission("mcplugin.command.power.reboot")) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to restart the server!</red>")); return true;
        }
        if (pm.hasPendingRequest()) { sender.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>There is already an active power management request.</red>")); return true; }
        pm.requestRestart(player.getName(), player.getUniqueId());
        player.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Server restart initiated, waiting for console confirmation.</yellow>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Server restart requested by player </yellow><white>" + player.getName() + "</white><yellow>. Confirm with: </yellow><white>/mp power confirm</white>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Cancel: </yellow><white>/mp power undo</white>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <yellow>Request will be automatically cancelled after </yellow><white>30</white><yellow> seconds.</yellow>"));
        return true;
    }

    private static boolean handleConfirm(CommandSender sender, PowerManager pm) {
        if (sender instanceof Player) { sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Error: </red><gray>Only console can confirm a request.</gray>")); return true; }
        if (!pm.hasPendingRequest()) { sender.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>No active shutdown/restart requests.</red>")); return true; }
        String action = pm.getCurrentRequestType() == PowerManager.RequestType.STOP ? "Shutdown" : "Restart";
        String requester = pm.getRequesterName();
        if (pm.confirmRequest()) {
            sender.sendMessage(MessageUtil.parse("<dark_gray>[<green>✔</green>]</dark_gray> <green>" + action + " confirmed (request from " + requester + ").</green>"));
            Bukkit.broadcast(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>" + action + " confirmed by console.</red>"));
        } else sender.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>Error during confirmation.</red>"));
        return true;
    }

    private static boolean handleUndo(CommandSender sender, PowerManager pm) {
        if (!pm.hasPendingRequest()) { sender.sendMessage(MessageUtil.parse("<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>No active shutdown/restart requests.</red>")); return true; }
        if (sender instanceof Player player && !player.hasPermission("mcplugin.command.power.undo")) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have permission to cancel a request!</red>")); return true;
        }
        String undoerName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        String action = pm.undoRequest(undoerName);
        sender.sendMessage(action != null ? MessageUtil.parse("<dark_gray>[<green>✔</green>]</dark_gray> <green>" + action + " cancelled.</green>") : MessageUtil.parse("<dark_red>❌</dark_red> <red>Error during cancellation.</red>"));
        return true;
    }
}
