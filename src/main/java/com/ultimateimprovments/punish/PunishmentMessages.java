package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * PunishmentMessages — configurable default punishment messages from config.yml.
 * <p>
 * Placeholders: {@code %player%}, {@code %punisher%}, {@code %reason%},
 * {@code %duration%}, {@code %discord_url%}
 * <p>
 * For chat messages (mute/warn) the Discord link is rendered as a clickable component.
 * For kick/ban messages the link is plain text (kick screen does not support click events).
 */
public final class PunishmentMessages {

    private PunishmentMessages() {}

    // =========================
    // PLACEHOLDER CONSTANTS
    // =========================
    private static final String PLAYER     = "%player%";
    private static final String PUNISHER   = "%punisher%";
    private static final String REASON     = "%reason%";
    private static final String DURATION   = "%duration%";
    private static final String DISCORD    = "%discord_url%";

    // =========================
    // CONFIG PATHS
    // =========================
    private static String path(String key) {
        String lang = getConfig().getString("messages.lang", "en");
        String root = "en".equalsIgnoreCase(lang) ? "messages_en" : "messages";
        return root + ".punishment." + key;
    }

    private static FileConfiguration getConfig() {
        return Main.getInstance().getConfig();
    }

    /**
     * Reads a punishment message from the config. Messages are stored as
     * a YAML list of lines — joined into a single multi-line string with "\n".
     */
    private static String raw(String key) {
        java.util.List<String> lines = getConfig().getStringList(path(key));
        if (lines.isEmpty()) return "";
        return String.join("\n", lines);
    }

    // =========================
    // BAN (kick screen — no clickable links)
    // =========================

    /**
     * Builds the kick-screen message for a banned player.
     * Uses legacy serialization because the kick screen only supports legacy colors.
     */
    public static String buildBanKickMessage(String player, String punisher,
                                              String reason, String duration,
                                              String discordUrl) {
        String msg = raw("ban")
                .replace(PLAYER,   player)
                .replace(PUNISHER, punisher)
                .replace(REASON,   reason)
                .replace(DURATION, duration)
                .replace(DISCORD,  discordUrl);
        return MessageUtil.legacy(msg);
    }

    // =========================
    // KICK (kick screen — no clickable links)
    // =========================

    public static String buildKickMessage(String player, String punisher,
                                           String reason, String discordUrl) {
        String msg = raw("kick")
                .replace(PLAYER,   player)
                .replace(PUNISHER, punisher)
                .replace(REASON,   reason)
                .replace(DISCORD,  discordUrl);
        return MessageUtil.legacy(msg);
    }

    // =========================
    // MUTE (chat — clickable Discord link)
    // =========================

    public static Component buildMuteChatMessage(String punisher, String reason,
                                                  String duration, String discordUrl) {
        String msg = raw("mute")
                .replace(PUNISHER, punisher)
                .replace(REASON,   reason)
                .replace(DURATION, duration)
                .replace(DISCORD,  discordUrl);
        return withClickableLink(msg, discordUrl);
    }

    // =========================
    // WARN (chat — clickable Discord link)
    // =========================

    public static Component buildWarnChatMessage(String punisher, String reason,
                                                  String duration, String discordUrl) {
        String msg = raw("warn")
                .replace(PUNISHER, punisher)
                .replace(REASON,   reason)
                .replace(DURATION, duration)
                .replace(DISCORD,  discordUrl);
        return withClickableLink(msg, discordUrl);
    }

    // =========================
    // WHITELIST (kick screen)
    // =========================

    public static String buildWhitelistKickMessage(String player, String discordUrl) {
        String msg = raw("whitelist")
                .replace(PLAYER,  player)
                .replace(DISCORD, discordUrl);
        return MessageUtil.legacy(msg);
    }

    // =========================
    // BLACKLIST (kick screen)
    // =========================

    public static String buildBlacklistKickMessage(String player, String discordUrl) {
        String msg = raw("blacklist")
                .replace(PLAYER,  player)
                .replace(DISCORD, discordUrl);
        return MessageUtil.legacy(msg);
    }

    // =========================
    // UTILITY
    // =========================

    /**
     * Converts a miniMessage string to a Component, then makes the Discord URL
     * in the last line a clickable {@code OPEN_URL} component.
     */
    private static Component withClickableLink(String msg, String discordUrl) {
        Component component = MessageUtil.parse(msg);
        if (discordUrl == null || discordUrl.isEmpty()) return component;

        try {
            URL url = new URL(discordUrl);
            return component.replaceText(config -> config
                    .matchLiteral(discordUrl)
                    .replacement(Component.text(discordUrl)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.text("Click to open")))
                            .color(NamedTextColor.WHITE)
                    )
            );
        } catch (MalformedURLException e) {
            // Invalid URL — return as plain text
            return component;
        }
    }

    /**
     * Formats a remaining time in milliseconds as a compact string.
     */
    public static String formatTime(long remainingMs) {
        return PunishmentManager.formatRemaining(remainingMs);
    }

    /**
     * Returns the Discord appeal URL from the config.
     */
    public static String getDiscordUrl() {
        return getConfig().getString(path("discord_url"), "https://dsc.gg/rizer001-development");
    }
}
