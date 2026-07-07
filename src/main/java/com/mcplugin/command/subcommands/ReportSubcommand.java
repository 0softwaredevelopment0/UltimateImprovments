package com.mcplugin.command.subcommands;

import com.mcplugin.config.MessagesManager;
import com.mcplugin.report.ReportManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /mp report <player> <reason> — подача жалобы на игрока.
 */
public final class ReportSubcommand {

    private ReportSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!player.hasPermission("mcplugin.command.report")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp report <player> <reason></white>"));
            return true;
        }

        String targetName = args[1];
        // Собираем причину из оставшихся аргументов
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
                            "<green>✔</green> <white>Репорт на игрока </white><yellow>{player}</yellow> <white>отправлен!</white>")
                            .replace("{player}", targetName)));
        }

        return true;
    }
}
