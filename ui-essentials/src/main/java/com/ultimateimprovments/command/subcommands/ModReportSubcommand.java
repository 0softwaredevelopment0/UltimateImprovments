package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.report.ReportManager;
import com.ultimateimprovments.report.ReportManager.ReportData;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /ui modreport <name> — moderation of a report.
 * <p>
 * Opens an input mode for the moderator:
 * 1. Writes a conclusion (text)
 * 2. Chooses a verdict (1 — Confirmed, 2 — Rejected, 3 — Closed)
 * Or cancel to abort.
 */
public final class ModReportSubcommand {

    private ModReportSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!player.hasPermission("ui.command.reports")) {
            CommandErrors.noPermission(player);
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui modreport <name></white>"));
            return true;
        }

        String modName = args[1];

        // Check that such a name exists in the mod queue
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

        // Show the report info
        player.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Moderate: </white><yellow>" + modName + "</yellow> ═══</gray>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Reported: </gray><white>" + report.reportedName + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>By: </gray><white>" + report.reporterName + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Reason: </gray><white>" + report.reason + "</white>"));

        // Start the moderation session
        ReportManager.startModeration(player, reportId, modName);

        return true;
    }
}
