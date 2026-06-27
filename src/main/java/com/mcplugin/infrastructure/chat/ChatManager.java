package com.mcplugin.infrastructure.chat;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Кастомная система чата.
 * <p>
 * Перехватывает AsyncPlayerChatEvent и форматирует сообщение по шаблону
 * из config.yml. Поддерживает:
 * <ul>
 *   <li>MiniMessage в формате и сообщениях игроков</li>
 *   <li>Встроенные плейсхолдеры {player_name}, {world_name} и т.д.</li>
 *   <li>PAPI плейсхолдеры %luckperms_prefix%, %player_world% и т.д.</li>
 *   <li>Per-group форматы для LuckPerms (enabled: false по умолчанию)</li>
 *   <li>Per-world форматы (enabled: false по умолчанию)</li>
 * </ul>
 * По умолчанию система ОТКЛЮЧЕНА (chat.enabled: false).
 */
public class ChatManager implements Listener {

    private static ChatManager instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private boolean enabled;
    private boolean playerMiniMessage;
    private String bypassPermission;

    // Default format
    private String defaultFormat;

    // Per-group formats (LuckPerms)
    private boolean groupsEnabled;
    private Map<String, String> groupFormats;
    private String defaultGroupFormat;

    // Per-world formats
    private boolean perWorldEnabled;
    private Map<String, String> worldFormats;

    public static void init() {
        instance = new ChatManager();
        Main.getInstance().getServer().getPluginManager().registerEvents(instance, Main.getInstance());
        instance.reloadConfig();
    }

    public static void shutdown() {
        instance = null;
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
        }
    }

    private void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        this.enabled = cfg.getBoolean("chat.enabled", false);
        this.playerMiniMessage = cfg.getBoolean("chat.player_minimessage", false);
        this.bypassPermission = cfg.getString("chat.bypass_permission", "mcplugin.chat.custom.bypass");

        this.defaultFormat = cfg.getString("chat.format",
                "<dark_gray>[</dark_gray><white>{player_name}</white><dark_gray>]</dark_gray> <white>{message}</white>");

        // ===== Per-group (LuckPerms) =====
        this.groupsEnabled = cfg.getBoolean("chat.groups.enabled", false);
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

        // ===== Per-world =====
        this.perWorldEnabled = cfg.getBoolean("chat.per_world.enabled", false);
        this.worldFormats = new HashMap<>();
        if (cfg.isConfigurationSection("chat.per_world.worlds")) {
            for (String key : cfg.getConfigurationSection("chat.per_world.worlds").getKeys(false)) {
                String fmt = cfg.getString("chat.per_world.worlds." + key);
                if (fmt != null && !fmt.isEmpty()) {
                    worldFormats.put(key.toLowerCase(), fmt);
                }
            }
        }

        Main.getInstance().getLogger().info("[Chat] Custom chat "
                + (enabled ? "enabled" : "disabled")
                + " | groups=" + groupsEnabled + " worlds=" + perWorldEnabled
                + " | player-minimessage=" + playerMiniMessage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Bypass permission
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return;

        // Determine format for this player
        String format = resolveFormat(player);
        if (format == null || format.isEmpty()) return;

        // Build message component
        String rawMessage = event.getMessage();
        Component messageComponent;
        if (playerMiniMessage) {
            // Parse player message as MiniMessage (allows color, bold, etc.)
            try {
                messageComponent = MM.deserialize(rawMessage);
            } catch (Exception e) {
                // Fallback to literal text if MiniMessage parse fails
                messageComponent = Component.text(rawMessage);
            }
        } else {
            // Literal text — MiniMessage tags not interpreted
            messageComponent = Component.text(rawMessage);
        }

        // Resolve placeholders in format (except {message})
        String resolved = PlaceholderResolver.resolve(format, player);

        // Split on {message} and rebuild with Component
        String[] parts = resolved.split("\\{message\\}", -1);

        // Build final broadcast component
        Component broadcast = Component.empty();
        boolean hasMessageToken = resolved.contains("{message}");

        if (!hasMessageToken) {
            // No {message} in format — append message at end
            broadcast = MessageUtil.parse(resolved)
                    .append(Component.text(" "))
                    .append(messageComponent);
        } else {
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    broadcast = broadcast.append(MessageUtil.parse(parts[i]));
                }
                if (i < parts.length - 1) {
                    broadcast = broadcast.append(messageComponent);
                }
            }
        }

        // Cancel original event and broadcast manually
        event.setCancelled(true);

        // Get recipients (already filtered by Bukkit for vanish/world)
        // Send to all recipients including the sender (for consistency)
        for (Player recipient : event.getRecipients()) {
            recipient.sendMessage(broadcast);
        }

        // Ensure the sender always sees their message
        // (in case they were excluded from recipients for some reason)
        if (!event.getRecipients().contains(player)) {
            player.sendMessage(broadcast);
        }

        // Console log
        Main.getInstance().getLogger().info(PlainTextComponentSerializer.plainText().serialize(broadcast));
    }

    /**
     * Resolves the chat format for a player based on per-world and per-group settings.
     * Priority: per-world (if enabled) > per-group (if enabled) > default format.
     */
    private String resolveFormat(Player player) {
        String worldName = player.getWorld().getName().toLowerCase();

        // 1. Per-world format (highest priority)
        if (perWorldEnabled) {
            String wf = worldFormats.get(worldName);
            if (wf != null) return wf;
        }

        // 2. Per-group format (LuckPerms)
        if (groupsEnabled) {
            String group = getPrimaryGroup(player);
            if (group != null) {
                String gf = groupFormats.get(group.toLowerCase());
                if (gf != null) return gf;
            }
            // Fallback to default group format
            if (defaultGroupFormat != null) return defaultGroupFormat;
        }

        // 3. Default format
        return defaultFormat;
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
