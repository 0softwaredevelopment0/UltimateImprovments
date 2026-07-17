package com.mcplugin.chat;

import com.mcplugin.core.Main;
import com.mcplugin.report.ReportManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.PlaceholderResolver;
import com.mcplugin.util.ConsoleLogger;
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
 * Кастомная система чата.
 * <p>
 * Перехватывает AsyncPlayerChatEvent и форматирует сообщение по шаблону
 * из config.yml. Поддерживает:
 * <ul>
 *   <li>MiniMessage в формате и сообщениях игроков</li>
 *   <li>Встроенные плейсхолдеры %player_name%, %world_name% и т.д.</li>
 *   <li>PAPI плейсхолдеры %luckperms_prefix%, %player_world% и т.д.</li>
 *   <li>Режим static — единый формат для всех</li>
 *   <li>Режим per-group — свой формат для каждой группы LuckPerms</li>
 *   <li>Режим per-world — свой формат для каждого мира</li>
 *   <li>Пинги (@everyone, @ник, @non-op, @is-admin, @is-non-admin)</li>
 * </ul>
 * По умолчанию система ОТКЛЮЧЕНА (chat.enabled: false).
 */
public class ChatManager implements Listener {

    private static ChatManager instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Режим чата. */
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
        this.bypassPermission = cfg.getString("chat.bypass_permission", "mcplugin.chat.custom.bypass");

        // Mode: static | per-group | per-world
        String modeStr = cfg.getString("chat.mode", "static").toLowerCase().replace(" ", "_");
        switch (modeStr) {
            case "per_group" -> this.mode = Mode.PER_GROUP;
            case "per_world" -> this.mode = Mode.PER_WORLD;
            default -> this.mode = Mode.STATIC;
        }

        this.defaultFormat = cfg.getString("chat.format",
                "<dark_gray>[</dark_gray><white>%player_name%</white><dark_gray>]</dark_gray> <white>%message%</white>");

        // ===== Per-group (LuckPerms) — загружается всегда для /mp chat reload =====
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

        // ===== Per-world — загружается всегда =====
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
        // 🛡 MODERATION SESSION — не отправляем в чат сообщения модератора.
        // НЕ отменяем ивент — ReportManager (тоже LOWEST priority) сам отменит
        // и обработает сообщение (заключение/вердикт).
        // =========================
        if (ReportManager.isInModeration(player)) {
            return;
        }

        // =========================
        // 🛡 MUTE CHECK — проверяем, не замучен ли игрок
        // =========================
        if (com.mcplugin.punish.PunishJoinListener.isMuted(player)) {
            event.setCancelled(true);
            var muteRecord = com.mcplugin.punish.PunishJoinListener.getMuteRecord(player);
            if (muteRecord != null) {
                String duration = muteRecord.isPermanent() ? "permanent" : muteRecord.getRemainingMs() / 1000 + "s";
                player.sendMessage(MessageUtil.parse(
                        "<red>🔇 You are muted!</red>\n" +
                        "<gray>Reason:</gray> <white>" + muteRecord.reason + "</white>\n" +
                        "<gray>Remaining:</gray> <white>" + duration + "</white>"
                ));
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
        // 🔔 PING PROCESSING — обработка @everyone, @ник, @non-op, @is-admin
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
            // playerMiniMessage: false — экранируем < и > только в тексте игрока,
            // MiniMessage-теги от пингов (server-generated) вставляем уже в формат
            String escapedRaw = rawMessage.replace("<", "\\<").replace(">", "\\>");
            // Если есть пинги — применяем замену @меток на уже готовые MiniMessage-теги
            // на экранированное сообщение, чтобы теги пингов не экранировались
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

        // ⚠ Paper 1.21.4 может не заполнять recipients
        // Если recipients пуст — шлём всем онлайн
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

        // 🔔 Play ping sounds + send notification for pinged players
        if (!pingedPlayers.isEmpty()) {
            ChatPingManager.notifyPingedPlayers(pingedPlayers, player);
        }
    }

    /**
     * Парсит сообщение игрока в Component (с учётом пингов и MiniMessage).
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
