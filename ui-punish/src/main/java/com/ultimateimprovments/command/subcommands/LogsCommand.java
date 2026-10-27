package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.punish.LogsMessages;
import com.ultimateimprovments.punish.PardonMessages;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 📜 LogsCommand — {@code /ui punish logs}: the punishment EVENT history.
 * <pre>
 * /ui punish logs view [-p &lt;page&gt;] [-t &lt;type&gt;]
 * /ui punish logs purge -a [-confirm] | -t &lt;1h|30m|7d|2w&gt;
 * </pre>
 * <p>
 * Replaces the old {@code /ui punish actionlist} (no alias). Unlike that command it
 * shows the LOG, not the currently active punishments: every punishment handed out
 * and every lifting of it (pardon by a moderator or automatic expiry) is one entry,
 * read from the append-only {@code punishment_log} table.
 * <p>
 * The list is rendered like {@code /ui help}: type filter buttons on top, clickable
 * page arrows below; the whole state lives in the command arguments, so the buttons
 * are plain {@code run_command} clicks ({@code /ui punish logs view -p 2 -t ban}).
 * <p>
 * {@code purge} deletes log entries only — active bans/mutes/blocks/warns and their
 * audit columns are never touched.
 */
public final class LogsCommand {

    private LogsCommand() {}

    /** Entries per page. */
    private static final int PAGE_SIZE = 8;

    /** Selectable type filters, in button order. */
    private static final List<String> TYPES = List.of("all", "ban", "mute", "block", "warn", "kick");

    /** Suggestions for the purge {@code -t} flag. */
    private static final List<String> TIME_SUGGESTIONS = List.of("30m", "1h", "12h", "1d", "7d", "1w");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static boolean execute(CommandSender sender, String[] args) {
        // args[0] = "punish", args[1] = "logs", args[2] = sub-command or a flag
        String sub = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "view";

        if (sub.startsWith("-")) {
            // /ui punish logs -p 2 -t ban — flags without the "view" word
            return view(sender, args, 2);
        }
        return switch (sub) {
            case "view" -> view(sender, args, 3);
            case "purge" -> purge(sender, args, 3);
            default -> {
                sender.sendMessage(MessageUtil.parse(LogsMessages.usage()));
                yield true;
            }
        };
    }

    // =========================
    // VIEW
    // =========================

    private static boolean view(CommandSender sender, String[] args, int start) {
        if (!sender.hasPermission(Permissions.CMD_PUNISH_LOGS)) {
            CommandErrors.noPermission(sender, Permissions.CMD_PUNISH_LOGS);
            return true;
        }

        int page = 1;
        String type = "all";
        for (int i = start; i < args.length; i++) {
            String raw = args[i];
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.equals("-p") || lower.startsWith("-p:")) {
                String value = lower.equals("-p")
                        ? (i + 1 < args.length ? args[++i] : "")
                        : unquote(raw.substring(3));
                try {
                    page = Math.max(1, Integer.parseInt(value.trim()));
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtil.parse(LogsMessages.usage()));
                    return true;
                }
            } else if (lower.equals("-t") || lower.startsWith("-t:")) {
                String value = lower.equals("-t")
                        ? (i + 1 < args.length ? args[++i] : "")
                        : unquote(raw.substring(3));
                type = value.trim().toLowerCase(Locale.ROOT);
            } else {
                sender.sendMessage(MessageUtil.parse(LogsMessages.usage()));
                return true;
            }
        }

        if (!TYPES.contains(type)) {
            sender.sendMessage(MessageUtil.parse(LogsMessages.unknownType(type)));
            return true;
        }

        int total = PunishmentManager.countLogEntries(type);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        page = Math.min(page, totalPages);

        List<PunishmentManager.LogEntry> entries = PunishmentManager.findLogEntries(
                type, (page - 1) * PAGE_SIZE, PAGE_SIZE);
        boolean clickable = sender instanceof Player;

        // Header
        sender.sendMessage(MessageUtil.parse(
                LogsMessages.header(total, typeLabel(type))));

        // Type filter buttons
        StringBuilder tabs = new StringBuilder();
        for (String tab : TYPES) {
            String label = typeLabel(tab);
            if (tab.equals(type)) {
                tabs.append("<dark_aqua>[</dark_aqua>").append(label).append("<dark_aqua>]</dark_aqua> ");
            } else if (clickable) {
                tabs.append("<click:run_command:/ui punish logs view -p 1 -t ").append(tab).append('>')
                        .append("<gray>[</gray>").append(label).append("<gray>]</gray></click> ");
            } else {
                tabs.append("<gray>[</gray>").append(label).append("<gray>]</gray> ");
            }
        }
        sender.sendMessage(MessageUtil.parse(tabs.toString()));

        // Entries
        if (entries.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(LogsMessages.empty()));
        } else {
            for (PunishmentManager.LogEntry entry : entries) {
                sender.sendMessage(MessageUtil.parse(renderEntry(entry)));
            }
        }

        // Page arrows
        String prev = page > 1
                ? (clickable
                    ? "<click:run_command:/ui punish logs view -p " + (page - 1) + " -t " + type
                        + "><dark_aqua>[« Prev]</dark_aqua></click>"
                    : "<dark_aqua>[« Prev]</dark_aqua>")
                : "<dark_gray>[« Prev]</dark_gray>";
        String next = page < totalPages
                ? (clickable
                    ? "<click:run_command:/ui punish logs view -p " + (page + 1) + " -t " + type
                        + "><dark_aqua>[Next »]</dark_aqua></click>"
                    : "<dark_aqua>[Next »]</dark_aqua>")
                : "<dark_gray>[Next »]</dark_gray>";
        sender.sendMessage(MessageUtil.parse(prev + "  " + LogsMessages.page(page, totalPages) + "  " + next));
        return true;
    }

    /** Renders one log entry according to its action. */
    private static String renderEntry(PunishmentManager.LogEntry entry) {
        String typeLabel = typeLabel(entry.type.name().toLowerCase(Locale.ROOT));
        String time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.createdAt));
        return switch (entry.action) {
            case PARDON -> LogsMessages.entryPardon(
                    entry.id, typeLabel, entry.playerName, entry.actor, time, entry.reason);
            case EXPIRE -> LogsMessages.entryExpire(
                    entry.id, typeLabel, entry.playerName, entry.actor, time, entry.reason);
            case PUNISH -> LogsMessages.entryPunish(
                    entry.id, typeLabel, entry.playerName, entry.actor, time, entry.reason);
        };
    }

    /** Localized label of a type filter ("all" included). */
    private static String typeLabel(String type) {
        if (type == null || type.equals("all")) return PardonMessages.allLabel();
        try {
            return PardonMessages.typeLabel(
                    PunishmentManager.PunishType.valueOf(type.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return type;
        }
    }

    // =========================
    // PURGE
    // =========================

    private static boolean purge(CommandSender sender, String[] args, int start) {
        if (!sender.hasPermission(Permissions.CMD_PUNISH_LOGS_PURGE)) {
            CommandErrors.noPermission(sender, Permissions.CMD_PUNISH_LOGS_PURGE);
            return true;
        }

        boolean all = false;
        boolean confirm = false;
        String timeValue = null;

        for (int i = start; i < args.length; i++) {
            String raw = args[i];
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.equals("-a") || lower.equals("-all")) {
                all = true;
            } else if (lower.equals("-confirm") || lower.equals("-yes")) {
                confirm = true;
            } else if (lower.equals("-t") || lower.startsWith("-t:")) {
                timeValue = unquote(lower.equals("-t")
                        ? (i + 1 < args.length ? args[++i] : "")
                        : raw.substring(3));
            } else {
                sender.sendMessage(MessageUtil.parse(LogsMessages.purgeUsage()));
                return true;
            }
        }

        if (!all && (timeValue == null || timeValue.isBlank())) {
            sender.sendMessage(MessageUtil.parse(LogsMessages.purgeUsage()));
            return true;
        }

        // -a wipes the whole log: always ask for an explicit confirmation.
        if (all) {
            if (!confirm) {
                int count = PunishmentManager.countLogEntries(null);
                sender.sendMessage(MessageUtil.parse(LogsMessages.purgeConfirm(
                        count, "/ui punish logs purge -a -confirm")));
                return true;
            }
            int deleted = PunishmentManager.purgeLogs(true, 0);
            sender.sendMessage(MessageUtil.parse(LogsMessages.purgeDone(deleted)));
            return true;
        }

        // -t <time>: delete everything older than "now - time".
        long durationMs = parseDurationMs(timeValue);
        if (durationMs <= 0) {
            sender.sendMessage(MessageUtil.parse(LogsMessages.purgeInvalidTime(timeValue)));
            return true;
        }
        int deleted = PunishmentManager.purgeLogs(false, System.currentTimeMillis() - durationMs);
        sender.sendMessage(MessageUtil.parse(LogsMessages.purgeDone(deleted)));
        return true;
    }

    /**
     * Parses a duration with a suffix: {@code s} seconds, {@code m} minutes,
     * {@code h} hours, {@code d} days, {@code w} weeks.
     *
     * @return duration in millis, or -1 for an invalid value
     */
    static long parseDurationMs(String value) {
        if (value == null) return -1;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.length() < 2) return -1;

        char unit = lower.charAt(lower.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(lower.substring(0, lower.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
        if (amount <= 0) return -1;

        long ms = switch (unit) {
            case 's' -> amount * 1000L;
            case 'm' -> amount * 60_000L;
            case 'h' -> amount * 3_600_000L;
            case 'd' -> amount * 86_400_000L;
            case 'w' -> amount * 604_800_000L;
            default -> -1L;
        };
        return ms > 0 ? ms : -1;
    }

    /** Strips surrounding double quotes kept by the dispatcher's tokenizer. */
    private static String unquote(String value) {
        if (value == null || value.length() < 2) return value == null ? "" : value;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    // =========================
    // TAB COMPLETION
    // =========================

    /**
     * Suggestions for the CURRENT (last) argument; the caller filters them by the
     * typed prefix.
     */
    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();
        // args[0] = "punish", args[1] = "logs"
        if (args.length == 3) {
            completions.add("view");
            completions.add("purge");
            return completions;
        }

        String sub = args[2].toLowerCase(Locale.ROOT);
        String previous = args.length >= 2 ? args[args.length - 2].toLowerCase(Locale.ROOT) : "";

        if (sub.equals("view") || sub.startsWith("-")) {
            if (previous.equals("-t") || previous.equals("-t:")) {
                completions.addAll(TYPES);
            } else if (previous.equals("-p") || previous.equals("-p:")) {
                completions.add("1");
            } else {
                completions.add("-p");
                completions.add("-t");
            }
        } else if (sub.equals("purge")) {
            if (previous.equals("-t") || previous.equals("-t:")) {
                completions.addAll(TIME_SUGGESTIONS);
            } else {
                completions.add("-a");
                completions.add("-t");
                completions.add("-confirm");
            }
        }
        return completions;
    }
}
