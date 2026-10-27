package com.ultimateimprovments.punish;

import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * LogsMessages — every text of {@code /ui punish logs}.
 * <p>
 * Strings live in the composite config under {@code messages.punish.logs.*}
 * (Russian) and {@code messages_en.punish.logs.*} (English, the default), routed
 * to {@code configs/UI-Punish.toml}. The defaults below are a safety net only.
 * <p>
 * Reasons are stored as MiniMessage (the {@code -r} flag accepts markup), so they
 * are inserted as-is; player names and types are escaped.
 * <p>
 * Placeholders: {@code %id%}, {@code %type%}, {@code %player%}, {@code %actor%},
 * {@code %time%}, {@code %reason%}, {@code %count%}, {@code %page%},
 * {@code %total%}, {@code %command%}, {@code %value%}.
 */
public final class LogsMessages {

    private LogsMessages() {}

    private static String raw(String key, String def) {
        String value = MessageUtil.raw("punish.logs." + key, def);
        return value == null ? def : value;
    }

    /** Escapes plain text (names, type labels, raw arguments) for MiniMessage. */
    private static String esc(String text) {
        return text == null ? "" : MiniMessage.miniMessage().escapeTags(text);
    }

    // =========================
    // COMMAND FEEDBACK
    // =========================

    public static String usage() {
        return raw("usage",
                "<red>❌ Usage:</red> <white>/ui punish logs view [-p <page>] [-t <type>]</white>\n"
                + "<white>/ui punish logs purge -a|-t <1h|30m|7d|2w> [-confirm]</white>");
    }

    public static String unknownType(String type) {
        return raw("unknown_type",
                "<red>❌ Unknown type: </red><yellow>%type%</yellow><red>.</red> "
                + "<gray>Available: </gray><white>all, ban, mute, block, warn, kick</white>")
                .replace("%type%", esc(type));
    }

    // =========================
    // VIEW
    // =========================

    public static String header(int count, String typeLabel) {
        return raw("header",
                "<gray>═══ <white>📜 Punishment logs</white> <dark_gray>(%count% entries, %type%)</dark_gray> ═══</gray>")
                .replace("%count%", Integer.toString(count))
                .replace("%type%", typeLabel);
    }

    public static String empty() {
        return raw("empty", "  <dark_gray>(no log entries)</dark_gray>");
    }

    public static String page(int page, int total) {
        return raw("page", "<white>Page %page%/%total%</white>")
                .replace("%page%", Integer.toString(page))
                .replace("%total%", Integer.toString(total));
    }

    /** One log line: a punishment was handed out. */
    public static String entryPunish(int id, String typeLabel, String player, String actor,
                                     String time, String reason) {
        return fill(raw("entry_punish",
                "  <white>#%id%</white> %type% <yellow>%player%</yellow> "
                + "<gray>applied by</gray> <white>%actor%</white> <dark_gray>| %time%</dark_gray>\n"
                + "      <gray>%reason%</gray>"),
                id, typeLabel, player, actor, time, reason);
    }

    /** One log line: a punishment was lifted by a moderator. */
    public static String entryPardon(int id, String typeLabel, String player, String actor,
                                     String time, String reason) {
        return fill(raw("entry_pardon",
                "  <white>#%id%</white> %type% <yellow>%player%</yellow> "
                + "<green>lifted</green> <gray>by</gray> <white>%actor%</white> <dark_gray>| %time%</dark_gray>\n"
                + "      <gray>%reason%</gray>"),
                id, typeLabel, player, actor, time, reason);
    }

    /** One log line: a punishment expired on its own. */
    public static String entryExpire(int id, String typeLabel, String player, String actor,
                                     String time, String reason) {
        return fill(raw("entry_expire",
                "  <white>#%id%</white> %type% <yellow>%player%</yellow> "
                + "<dark_gray>expired | %time%</dark_gray>"),
                id, typeLabel, player, actor, time, reason);
    }

    private static String fill(String template, int id, String typeLabel, String player,
                               String actor, String time, String reason) {
        return template
                .replace("%id%", Integer.toString(id))
                .replace("%type%", typeLabel)
                .replace("%player%", esc(player))
                .replace("%actor%", esc(actor))
                .replace("%time%", esc(time))
                .replace("%reason%", reason == null || reason.isEmpty() ? "-" : reason);
    }

    // =========================
    // PURGE
    // =========================

    public static String purgeUsage() {
        return raw("purge_usage",
                "<red>❌ Usage:</red> <white>/ui punish logs purge -a [-confirm]</white> "
                + "<gray>|</gray> <white>/ui punish logs purge -t <1h|30m|7d|2w></white>");
    }

    /** Confirmation of a mass purge: the clickable command re-runs it with {@code -confirm}. */
    public static String purgeConfirm(int count, String command) {
        return raw("purge_confirm",
                "<yellow>⚠</yellow> <white>This deletes ALL punishment logs</white> "
                + "<dark_gray>(%count% entries)</dark_gray><white>.</white> "
                + "<click:run_command:%command%><dark_aqua>[✔ Confirm]</dark_aqua></click>")
                .replace("%count%", Integer.toString(count))
                .replace("%command%", command);
    }

    public static String purgeDone(int count) {
        return raw("purge_done",
                "<green>✔</green> <white>Deleted</white> <yellow>%count%</yellow> "
                + "<white>punishment log entries.</white>")
                .replace("%count%", Integer.toString(count));
    }

    public static String purgeInvalidTime(String value) {
        return raw("purge_invalid_time",
                "<red>❌ Invalid time: </red><yellow>%value%</yellow> "
                + "<gray>— use a number with a suffix: s, m, h, d, w (e.g. 30m, 12h, 7d)</gray>")
                .replace("%value%", esc(value));
    }
}
