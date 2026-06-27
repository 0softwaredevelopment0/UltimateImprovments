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
 *   <li>Режим static — единый формат для всех</li>
 *   <li>Режим per-group — свой формат для каждой группы LuckPerms</li>
 *   <li>Режим per-world — свой формат для каждого мира</li>
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

        // Mode: static | per-group | per-world
        String modeStr = cfg.getString("chat.mode", "static").toLowerCase().replace("-", "_");
        switch (modeStr) {
            case "per_group" -> this.mode = Mode.PER_GROUP;
            case "per_world" -> this.mode = Mode.PER_WORLD;
            default -> this.mode = Mode.STATIC;
        }

        this.defaultFormat = cfg.getString("chat.format",
                "<dark_gray>[</dark_gray><white>{player_name}</white><dark_gray>]</dark_gray> <white>{message}</white>");

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

        Main.getInstance().getLogger().info("[Chat] Custom chat "
                + (enabled ? "enabled" : "disabled")
                + " | mode=" + mode.name().toLowerCase()
                + " | player-minimessage=" + playerMiniMessage);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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

        // Build final broadcast component
        Component broadcast;
        boolean hasMessageToken = resolved.contains("{message}");

        if (!hasMessageToken) {
            // No {message} in format — append message at end
            broadcast = MessageUtil.parse(resolved)
                    .append(Component.text(" "))
                    .append(messageComponent);
        } else if (playerMiniMessage) {
            // ⚠️ Когда playerMiniMessage: true, НЕ вставляем сообщение в строку.
            // Иначе теги игрока (</white>, <red>) могут перекрыть теги формата
            // (<white>{message}</white> → игроковский </white> закроет форматный <white>).
            // Вместо этого: парсим формат слева и справа от {message} как MiniMessage,
            // а между ними вставляем уже распаршенный messageComponent.
            int msgIdx = resolved.indexOf("{message}");
            String beforeStr = resolved.substring(0, msgIdx);
            String afterStr = resolved.substring(msgIdx + 9); // "{message}".length()

            Component beforeComp;
            Component afterComp;
            try {
                beforeComp = MessageUtil.parse(beforeStr);
            } catch (Exception e) {
                beforeComp = Component.text(beforeStr);
            }
            try {
                afterComp = MessageUtil.parse(afterStr);
            } catch (Exception e) {
                afterComp = Component.text(afterStr);
            }

            broadcast = beforeComp.append(messageComponent).append(afterComp);
        } else {
            // playerMiniMessage: false — экранируем < и > в сообщении,
            // подставляем в строку и парсим целиком.
            String msgForFormat = rawMessage.replace("<", "\\<").replace(">", "\\>");
            String finalFormat = resolved.replace("{message}", msgForFormat);
            try {
                broadcast = MessageUtil.parse(finalFormat);
            } catch (Exception e) {
                // Fallback: parse format without message, append as plain text
                String formatWithoutMsg = resolved.replace("{message}", "");
                broadcast = MessageUtil.parse(formatWithoutMsg).append(messageComponent);
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
