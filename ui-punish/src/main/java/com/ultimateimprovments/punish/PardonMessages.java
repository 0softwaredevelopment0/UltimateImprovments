package com.ultimateimprovments.punish;

import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * PardonMessages — every text of {@code /ui punish pardon}.
 * <p>
 * All strings live in the composite config under {@code messages.punish.pardon.*}
 * (Russian, active when {@code messages.lang = "ru"}) and
 * {@code messages_en.punish.pardon.*} (English, the default). The Russian section
 * is a full translation, not a fallback. The defaults below are only a safety net
 * for a config written before these keys existed.
 * <p>
 * Placeholders: {@code %player%}, {@code %actor%}, {@code %type%}, {@code %types%},
 * {@code %reason%}, {@code %count%}, {@code %id%}, {@code %command%}.
 */
public final class PardonMessages {

    private PardonMessages() {}

    /** Reads one key of the {@code punish.pardon} message section. */
    private static String raw(String key, String def) {
        String value = MessageUtil.raw("punish.pardon." + key, def);
        return value == null ? def : value;
    }

    /** Escapes arbitrary admin text (reasons) so MiniMessage renders it verbatim. */
    private static String esc(String text) {
        return text == null ? "" : MiniMessage.miniMessage().escapeTags(text);
    }

    // =========================
    // COMMAND FEEDBACK
    // =========================

    public static String usage() {
        return raw("usage",
                "<red>❌ Usage:</red> <white>/ui punish pardon <ban|mute|block|warn|all> "
                + "<player|all> [-r:<reason>] [-id:<n>] [-s] [-dry] [-confirm]</white>");
    }

    public static String unknownType(String type) {
        return raw("unknown_type",
                "<red>❌ Unknown punishment type: </red><yellow>%type%</yellow><red>.</red> "
                + "<gray>Available: </gray><white>ban, mute, block, warn, all</white>")
                .replace("%type%", esc(type));
    }

    public static String kickNotPardonable() {
        return raw("kick_not_pardonable",
                "<red>❌ Kicks are log entries and cannot be pardoned.</red> "
                + "<gray>Available: </gray><white>ban, mute, block, warn, all</white>");
    }

    public static String nothingFound(String typesLabel, String player) {
        return raw("nothing_found",
                "<yellow>⚠</yellow> <white>No active %types% punishment found for</white> <yellow>%player%</yellow>")
                .replace("%types%", typesLabel)
                .replace("%player%", esc(player));
    }

    public static String invalidId(String id) {
        return raw("invalid_id",
                "<red>❌ Invalid ID: </red><yellow>%id%</yellow>")
                .replace("%id%", esc(id));
    }

    public static String unknownFlag(String flag) {
        return raw("unknown_flag",
                "<red>❌ Unknown flag: </red><yellow>%flag%</yellow>")
                .replace("%flag%", esc(flag));
    }

    // =========================
    // PREVIEW / CONFIRMATION
    // =========================

    public static String previewHeader(int count, String player) {
        return raw("preview_header",
                "<gray>═══ <white>⚖ Pardon preview</white> "
                + "<dark_gray>(%count% record(s), %player%)</dark_gray> ═══</gray>")
                .replace("%count%", Integer.toString(count))
                .replace("%player%", esc(player));
    }

    /** One record line of the preview/result list. */
    public static String line(String typeLabel, int id, String player, String reason) {
        return raw("line",
                "  <white>#%id%</white> <gray>%type%</gray> <yellow>%player%</yellow> "
                + "<dark_gray>|</dark_gray> <gray>%reason%</gray>")
                .replace("%id%", Integer.toString(id))
                .replace("%type%", typeLabel)
                .replace("%player%", esc(player))
                .replace("%reason%", esc(reason == null || reason.isEmpty() ? "-" : reason));
    }

    public static String previewMore(int count) {
        return raw("preview_more", "  <dark_gray>… and %count% more</dark_gray>")
                .replace("%count%", Integer.toString(count));
    }

    public static String confirmHint(String command) {
        return raw("confirm_hint",
                "<gray>Nothing was changed. </gray><click:run_command:%command%>"
                + "<dark_aqua>[✔ Confirm]</dark_aqua></click>")
                .replace("%command%", command);
    }

    public static String dryRun() {
        return raw("dry_run", "<yellow>⚠</yellow> <gray>Dry run — nothing was changed.</gray>");
    }

    // =========================
    // RESULT
    // =========================

    public static String result(int count, String player) {
        return raw("result",
                "<green>✔</green> <white>Pardoned</white> <yellow>%count%</yellow> "
                + "<white>punishment(s) of</white> <yellow>%player%</yellow>")
                .replace("%count%", Integer.toString(count))
                .replace("%player%", esc(player));
    }

    public static String broadcast(String player, String typesLabel, String actor, String reason) {
        return raw("broadcast",
                "<green>✔</green> <yellow>%player%</yellow> <gray>pardoned (%types%) by</gray> "
                + "<white>%actor%</white><gray> | Reason:</gray> <white>%reason%</white>")
                .replace("%player%", esc(player))
                .replace("%types%", typesLabel)
                .replace("%actor%", esc(actor))
                .replace("%reason%", esc(reason == null || reason.isEmpty() ? "-" : reason));
    }

    /** Message shown to the pardoned player, per punishment type. */
    public static String noticeFor(PunishmentManager.PunishType type) {
        return switch (type) {
            case BAN -> raw("notice_ban", "<green>✔</green> <white>You have been unbanned.</white>");
            case MUTE -> raw("notice_mute", "<green>✔</green> <white>You have been unmuted.</white>");
            case BLOCK -> raw("notice_block", "<green>✔</green> <white>You have been unblocked.</white>");
            case WARN -> raw("notice_warn",
                    "<green>✔</green> <white>A warning has been removed from your account.</white>");
            default -> raw("notice_other", "<green>✔</green> <white>A punishment has been lifted.</white>");
        };
    }

    public static String defaultReason(String actor) {
        return raw("default_reason", "Pardoned by %actor%").replace("%actor%", esc(actor));
    }

    // =========================
    // LABELS
    // =========================

    /** Localized label of one punishment type ({@code type_ban}, {@code type_mute}, …). */
    public static String typeLabel(PunishmentManager.PunishType type) {
        return switch (type) {
            case BAN -> raw("type_ban", "<red>ban</red>");
            case MUTE -> raw("type_mute", "<red>mute</red>");
            case BLOCK -> raw("type_block", "<dark_red>block</dark_red>");
            case WARN -> raw("type_warn", "<yellow>warn</yellow>");
            case KICK -> raw("type_kick", "<gray>kick</gray>");
        };
    }

    /** Label used when every type is selected. */
    public static String allLabel() {
        return raw("type_all", "<white>all</white>");
    }

    /** Target label for a mass pardon ({@code <player> = all}). */
    public static String allPlayersLabel() {
        return raw("all_players", "all online players");
    }

    /** Joined label for a type set: one label, or the localized {@code all}. */
    public static String typesLabel(java.util.Collection<PunishmentManager.PunishType> types) {
        if (types == null || types.isEmpty()) return allLabel();
        if (types.size() > 1) return allLabel();
        return typeLabel(types.iterator().next());
    }
}
