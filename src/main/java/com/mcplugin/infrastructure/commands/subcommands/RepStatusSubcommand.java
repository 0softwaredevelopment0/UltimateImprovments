package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.report.ReportManager;
import com.mcplugin.infrastructure.report.ReportManager.ReportData;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /mp repstatus — проверка статуса своего репорта.
 */
public final class RepStatusSubcommand {

    private RepStatusSubcommand() {}

    public static boolean execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        ReportData report = ReportManager.getActiveReport(player.getUniqueId().toString());
        if (report == null) {
            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("report.no_active_report",
                            "<yellow>✦</yellow> <white>У вас нет активных репортов.</white>")));
            return true;
        }

        String timeLeft = ReportManager.formatTimeLeft(report.expiresAt);

        player.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Report Status</white> ═══</gray>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Reported: </gray><white>" + report.reportedName + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Reason: </gray><white>" + report.reason + "</white>"));
        if (report.status.equals("pending")) {
            player.sendMessage(MessageUtil.parse(
                    "<gray>Status: </gray><yellow>На рассмотрении</yellow>"));
            player.sendMessage(MessageUtil.parse(
                    "<gray>Expires: </gray><white>" + timeLeft + "</white>"));
        }

        return true;
    }
}
