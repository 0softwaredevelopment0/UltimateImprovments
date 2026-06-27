package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.report.ReportManager;
import com.mcplugin.infrastructure.report.ReportManager.ReportData;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /mp modreport <name> — moderation of a report.
 * <p>
 * Открывает режим ввода для модератора:
 * 1. Пишет заключение (текст)
 * 2. Выбирает вердикт (1 — Подтверждён, 2 — Отклонён, 3 — Закрыт)
 * Или cancel для отмены.
 */
public final class ModReportSubcommand {

    private ModReportSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!player.hasPermission("mcplugin.command.reports")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp modreport <name></white>"));
            return true;
        }

        String modName = args[1];

        // Проверяем, что такое имя существует в мод-очереди
        if (!ReportManager.isModNameExists(modName)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Moderation report </red><yellow>" + modName + "</yellow> <red>not found!</red>"));
            return true;
        }

        int reportId = ReportManager.getReportIdByModName(modName);
        if (reportId < 0) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Report not found for </red><yellow>" + modName + "</yellow>"));
            return true;
        }

        ReportData report = ReportManager.getReportById(reportId);
        if (report == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Report #" + reportId + " not found!</red>"));
            return true;
        }

        if (!report.status.equals("pending")) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Report #" + reportId + " already has verdict: </red><white>" + report.verdictOption + "</white>"));
            return true;
        }

        // Показываем информацию о репорте
        player.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Moderate: </white><yellow>" + modName + "</yellow> ═══</gray>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Reported: </gray><white>" + report.reportedName + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>By: </gray><white>" + report.reporterName + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Reason: </gray><white>" + report.reason + "</white>"));

        // Начинаем сессию модерации
        ReportManager.startModeration(player, reportId, modName);

        return true;
    }
}
