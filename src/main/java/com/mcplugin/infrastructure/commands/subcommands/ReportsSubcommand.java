package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.report.ReportManager;
import com.mcplugin.infrastructure.report.ReportManager.ReportData;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles /mp reports list|add <id> <name>|remove <id> — admin reports management.
 */
public final class ReportsSubcommand {

    private ReportsSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.reports")) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        return switch (action) {
            case "list" -> listReports(sender);
            case "add" -> addToModQueue(sender, args);
            case "remove" -> removeFromModQueue(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\\n" +
                "<white>/mp reports list</white> <gray>— show all reports</gray>\\n" +
                "<white>/mp reports add <id> <name></white> <gray>— add report to moderation queue</gray>\\n" +
                "<white>/mp reports remove <id></white> <gray>— remove report from moderation queue</gray>"));
    }

    private static boolean listReports(CommandSender sender) {
        List<ReportData> reports = ReportManager.getAllReports();

        sender.sendMessage(MessageUtil.parse(
                "<gray>═══ <white>Reports</white> ═══</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Total: <white>" + reports.size() + "</white></gray>"));

        if (reports.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("  <dark_gray>(empty)</dark_gray>"));
            return true;
        }

        // Показываем очередь модерации
        List<String> modQueue = ReportManager.getModQueueNames();
        if (!modQueue.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Moderation queue:</gray>"));
            for (String entry : modQueue) {
                sender.sendMessage(MessageUtil.parse(
                        "  <white>" + entry + "</white>"));
            }
            sender.sendMessage(MessageUtil.parse(""));
        }

        // Показываем последние репорты
        for (ReportData r : reports) {
            String statusColor = switch (r.status) {
                case "pending" -> "<yellow>";
                case "confirmed" -> "<green>";
                case "rejected" -> "<red>";
                case "closed" -> "<gray>";
                case "expired" -> "<dark_gray>";
                default -> "<white>";
            };
            String timeLeft = r.status.equals("pending")
                    ? " <gray>(" + ReportManager.formatTimeLeft(r.expiresAt) + ")</gray>"
                    : "";
            String verdictStr = (!r.verdictOption.isEmpty())
                    ? " <gray>→</gray> " + statusColor + r.verdictOption + "</white>"
                    : "";

            sender.sendMessage(MessageUtil.parse(
                    "<gray>#" + r.id + "</gray> " +
                    "<white>" + r.reportedName + "</white>" +
                    " <gray>by</gray> <white>" + r.reporterName + "</white>" +
                    statusColor + " [" + r.status + "]" + verdictStr + timeLeft));
            sender.sendMessage(MessageUtil.parse(
                    "  <gray>└ " + r.reason + "</gray>"));
        }

        return true;
    }

    private static boolean addToModQueue(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp reports add <id> <name></white>"));
            return true;
        }

        int reportId;
        try {
            reportId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid report ID: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }

        String modName = args[3];

        // Проверяем, что репорт существует
        ReportData report = ReportManager.getReportById(reportId);
        if (report == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Report #" + reportId + " not found!</red>"));
            return true;
        }

        // Проверяем, что имя не занято
        if (ReportManager.isModNameExists(modName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Name </red><yellow>" + modName + "</yellow> <red>already in use!</red>"));
            return true;
        }

        if (ReportManager.addToModQueue(reportId, modName)) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Report #" + reportId + " added to moderation queue as </white><yellow>" + modName + "</yellow>"));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Failed to add report to moderation queue!</red>"));
        }

        return true;
    }

    private static boolean removeFromModQueue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/mp reports remove <id>|confirm</white>"));
            return true;
        }

        String target = args[2].toLowerCase();

        // Обработка подтверждения
        if (target.equals("confirm")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Only players can confirm removal!</red>"));
                return true;
            }

            int storedId = ReportManager.getPendingConfirmationId(player);
            if (storedId > 0 && ReportManager.hasRemoveConfirmation(player, storedId)) {
                String modName = ReportManager.getModNameByReportId(storedId);
                if (modName != null && ReportManager.removeFromModQueueByName(modName)) {
                    ReportManager.clearRemoveConfirmation(player);
                    player.sendMessage(MessageUtil.parse(
                            "<green>✔</green> <white>Report removed from moderation queue.</white>"));
                } else {
                    player.sendMessage(MessageUtil.parse(
                            "<red>❌ Failed to remove report.</red>"));
                }
            } else {
                player.sendMessage(MessageUtil.parse(
                        "<red>❌ No pending removal confirmation!</red>"));
            }
            return true;
        }

        // Удаление по ID
        int reportId;
        try {
            reportId = Integer.parseInt(target);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid report ID: </red><yellow>" + target + "</yellow>"));
            return true;
        }

        if (!ReportManager.isInModQueue(reportId)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Report #" + reportId + " is not in moderation queue!</red>"));
            return true;
        }

        if (sender instanceof Player player) {
            String modName = ReportManager.getModNameByReportId(reportId);
            if (modName == null) modName = "#" + reportId;

            ReportManager.requestRemoveConfirmation(player, reportId, modName);
        } else {
            // Консоль — без подтверждения
            if (ReportManager.removeFromModQueue(reportId)) {
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Report #" + reportId + " removed from moderation queue.</white>"));
            }
        }

        return true;
    }
}
