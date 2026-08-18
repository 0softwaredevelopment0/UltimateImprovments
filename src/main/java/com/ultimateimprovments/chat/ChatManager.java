package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.report.ReportManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom chat system.
 * <p>
 * Intercepts AsyncPlayerChatEvent and formats the message by the template
 * from config.yml. Supports:
 * <ul>
 *   <li>MiniMessage in the format and player messages</li>
 *   <li>Built-in placeholders %player_name%, %world_name% etc.</li>
 *   <li>PAPI placeholders %luckperms_prefix%, %player_world% etc.</li>
 *   <li>Static mode — a single format for everyone</li>
 *   <li>Per-group mode — a format for each LuckPerms group</li>
 *   <li>Per-world mode — a format for each world</li>
 *   <li>Pings (@everyone, @nick, @non-op, @is-admin, @is-non-admin)</li>
 * </ul>
 * By default the system is DISABLED (chat.enabled: false).
 */
public class ChatManager implements Listener {

    private static ChatManager instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Chat mode. */
    enum Mode { STATIC, PER_GROUP, PER_WORLD }

    private boolean enabled;
    private Mode mode;
    private boolean playerMiniMessage;
    private boolean messagePlaceholders;
    private String bypassPermission;

    // Default format (used for STATIC and as fallback)
    private String defaultFormat;

    // Per-group formats (LuckPerms) — used when mode == PER_GROUP
    private Map<String, String> groupFormats;
    private String defaultGroupFormat;

    // Per-world formats — used when mode == PER_WORLD
    private Map<String, String> worldFormats;

    public static void init() {
        instance = new ChatManager();
        Main.getInstance().getServer().getPluginManager().registerEvents(instance, Main.getInstance());
        instance.reloadConfig();
        ChatPingManager.reloadConfig();
    }

    public static void shutdown() {
        if (instance != null) {
            HandlerList.unregisterAll(instance);
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
        }
        ChatPingManager.reloadConfig();
    }

    private void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        this.enabled = cfg.getBoolean("chat.enabled", false);
        this.playerMiniMessage = cfg.getBoolean("chat.player_minimessage", false);
        this.messagePlaceholders = cfg.getBoolean("chat.message_placeholders", true);
        this.bypassPermission = cfg.getString("chat.bypass_permission", "ui.chat.custom.bypass");

        // Mode: static | per-group | per-world
        String modeStr = cfg.getString("chat.mode", "static").toLowerCase().replace(" ", "_");
        switch (modeStr) {
            case "per_group" -> this.mode = Mode.PER_GROUP;
            case "per_world" -> this.mode = Mode.PER_WORLD;
            default -> this.mode = Mode.STATIC;
        }

        this.defaultFormat = cfg.getString("chat.format",
                "<dark_gray>[</dark_gray><white>%player_name%</white><dark_gray>]</dark_gray> <white>%message%</white>");

        // ===== Per-group (LuckPerms) — always loaded for /ui chat reload =====
        this.groupFormats = new HashMap<>();
        if (cfg.isConfigurationSection("chat.groups.formats")) {
            for (String key : cfg.getConfigurationSection("chat.groups.formats").getKeys(false)) {
                String fmt = cfg.getString("chat.groups.formats." + key);
                if (fmt != null && !fmt.isEmpty()) {
                    groupFormats.put(key.toLowerCase(), fmt);
                }
            }
        }
        this.defaultGroupFormat = cfg.getString("chat.groups.default", defaultFormat);

        // ===== Per-world — always loaded =====
        this.worldFormats = new HashMap<>();
        if (cfg.isConfigurationSection("chat.worlds")) {
            for (String key : cfg.getConfigurationSection("chat.worlds").getKeys(false)) {
                String fmt = cfg.getString("chat.worlds." + key);
                if (fmt != null && !fmt.isEmpty()) {
                    worldFormats.put(key.toLowerCase(), fmt);
                }
            }
        }

        ConsoleLogger.info("[Chat] Custom chat "
                + (enabled ? "enabled" : "disabled")
                + " | mode=" + mode.name().toLowerCase()
                + " | player-minimessage=" + playerMiniMessage
                + " | message-placeholders=" + messagePlaceholders);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // =========================
        // MODERATION SESSION — don't send the moderator's messages to chat.
        // Do NOT cancel the event — ReportManager (also LOWEST priority) will cancel
        // it itself and process the message (conclusion/verdict).
        // =========================
        if (ReportManager.isInModeration(player)) {
            return;
        }

        // =========================
        // MUTE CHECK — check whether the player is muted
        // =========================
        if (com.ultimateimprovments.punish.PunishJoinListener.isMuted(player)) {
            event.setCancelled(true);
            var muteRecord = com.ultimateimprovments.punish.PunishJoinListener.getMuteRecord(player);
            if (muteRecord != null) {
                String durationFmt = muteRecord.isPermanent()
                        ? "Permanent"
                        : com.ultimateimprovments.punish.PunishmentMessages.formatTime(muteRecord.getRemainingMs());
                player.sendMessage(com.ultimateimprovments.punish.PunishmentMessages.buildMuteChatMessage(
                        muteRecord.punishedBy, muteRecord.reason, durationFmt,
                        com.ultimateimprovments.punish.PunishmentMessages.getDiscordUrl()));
            }
            return;
        }

        if (!enabled) return;

        // Bypass permission
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return;

        // Determine format for this player
        String format = resolveFormat(player);
        if (format == null || format.isEmpty()) return;

        // Build message component (resolve placeholders if enabled)
        String rawMessage = event.getMessage();
        if (messagePlaceholders) {
            rawMessage = PlaceholderResolver.resolve(rawMessage, player);
        }

        // Resolve placeholders in format (except %message%)
        String resolved = PlaceholderResolver.resolve(format, player);

        // =========================
        // PING PROCESSING — handle @everyone, @nick, @non-op, @is-admin
        // =========================
        ChatPingManager.PingResult pingResult = ChatPingManager.processPings(rawMessage, player);
        String pingedMessage = pingResult.formattedMessage();
        List<Player> pingedPlayers = pingResult.pingedPlayers();

        // Build final broadcast component
        Component broadcast;
        String msgForBroadcast = pingedPlayers.isEmpty() ? rawMessage : pingedMessage;
        boolean hasMessageToken = resolved.contains("%message%");

        if (!hasMessageToken) {
            // No %message% in format — append message at end
            broadcast = MessageUtil.parse(resolved)
                    .append(Component.text(" "))
                    .append(parseMessageComponentForPing(msgForBroadcast, player));
        } else if (playerMiniMessage) {
            // Parse player message (with ping formatting) as MiniMessage, then embed
            Component msgComp = parseMessageComponentForPing(msgForBroadcast, player);
            String serializedMsg = MM.serialize(msgComp);
            String finalFormat = resolved.replace("%message%", serializedMsg);
            broadcast = MessageUtil.parse(finalFormat);
        } else {
            // playerMiniMessage: false — escape < and > only in the player's text,
            // ping MiniMessage tags (server-generated) are inserted into the format already built
            String escapedRaw = rawMessage.replace("<", "\\<").replace(">", "\\>");
            // If there are pings — apply the @tag replacement with already-built MiniMessage tags
            // onto the escaped message, so the ping tags don't get escaped
            ChatPingManager.PingResult pingResultEscaped = ChatPingManager.processPings(escapedRaw, player);
            String finalMsg = pingResultEscaped.formattedMessage();
            String finalFormat = resolved.replace("%message%", finalMsg);
            try {
                broadcast = MessageUtil.parse(finalFormat);
            } catch (Exception e) {
                String formatWithoutMsg = resolved.replace("%message%", "");
                broadcast = MessageUtil.parse(formatWithoutMsg).append(Component.text(escapedRaw));
            }
        }

        // Cancel original event and broadcast manually
        event.setCancelled(true);

        // Paper 1.21.4 may not fill recipients
        // If recipients is empty — send to all online players
        java.util.Set<Player> recipients = event.getRecipients();
        if (recipients == null || recipients.isEmpty()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(broadcast);
            }
        } else {
            for (Player recipient : recipients) {
                recipient.sendMessage(broadcast);
            }
            // Ensure the sender always sees their message
            if (!recipients.contains(player)) {
                player.sendMessage(broadcast);
            }
        }

        // Console log
        ConsoleLogger.info(PlainTextComponentSerializer.plainText().serialize(broadcast));

        // Play ping sounds + send notification for pinged players
        if (!pingedPlayers.isEmpty()) {
            ChatPingManager.notifyPingedPlayers(pingedPlayers, player);
        }
    }

    /**
     * Parses the player's message into a Component (taking pings and MiniMessage into account).
     */
    private Component parseMessageComponentForPing(String msg, Player player) {
        if (messagePlaceholders) {
            msg = PlaceholderResolver.resolve(msg, player);
        }
        if (playerMiniMessage) {
            try {
                return MM.deserialize(msg);
            } catch (Exception e) {
                return Component.text(msg);
            }
        }
        return Component.text(msg);
    }

    /**
     * Resolves the chat format for a player based on the configured mode.
     * Mode determines the lookup strategy: static / per-group / per-world.
     */
    private String resolveFormat(Player player) {
        switch (mode) {
            case PER_WORLD: {
                String worldName = player.getWorld().getName().toLowerCase();
                String wf = worldFormats.get(worldName);
                if (wf != null) return wf;
                // Fallback to default format
                return defaultFormat;
            }
            case PER_GROUP: {
                String group = getPrimaryGroup(player);
                if (group != null) {
                    String gf = groupFormats.get(group.toLowerCase());
                    if (gf != null) return gf;
                }
                // Fallback to default group format, then to default format
                return defaultGroupFormat != null ? defaultGroupFormat : defaultFormat;
            }
            default:
                return defaultFormat;
        }
    }

    /**
     * Gets the player's LuckPerms primary group name via PAPI placeholder.
     * Returns null if PAPI is not available or group cannot be determined.
     */
    private String getPrimaryGroup(Player player) {
        if (!PlaceholderResolver.isPapiAvailable()) return null;
        String group = PlaceholderResolver.resolve("%luckperms_primary_group_name%", player);
        if (group == null || group.isEmpty() || group.equals("%luckperms_primary_group_name%")) {
            return null;
        }
        return group;
    }
}
