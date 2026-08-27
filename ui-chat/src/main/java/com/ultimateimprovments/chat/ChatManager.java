package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;

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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom chat system with two modes:
 * <ul>
 *   <li><b>static</b> — single format for everyone (original behaviour)</li>
 *   <li><b>channels</b> — per-channel routing: local, global, world, private, admin</li>
 * </ul>
 * Player picks a channel with {@code /ui chatchnl <channel> [player]}.
 * Default channel: GLOBAL.
 */
public class ChatManager implements Listener {

    private static ChatManager instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    enum Mode { STATIC, CHANNELS }

    private boolean enabled;
    private Mode mode;
    private boolean playerMiniMessage;
    private boolean messagePlaceholders;
    private String bypassPermission;

    // ===== STATIC =====
    private String staticFormat;

    // ===== CHANNELS =====
    private Map<ChatChannel, String> channelFormats;
    private int localRadius;

    // ========================= LIFECYCLE =========================

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
        if (instance != null) instance.reloadConfig();
        ChatPingManager.reloadConfig();
    }

    private org.bukkit.configuration.file.FileConfiguration getPluginConfig() {
        var chat = com.ultimateimprovments.chat.UIChat.getInstance();
        return chat != null ? chat.getConfig() : Main.getInstance().getConfig();
    }

    private void reloadConfig() {
        FileConfiguration cfg = getPluginConfig();

        this.enabled = cfg.getBoolean("chat.enabled", false);
        this.playerMiniMessage = cfg.getBoolean("chat.player_minimessage", false);
        this.messagePlaceholders = cfg.getBoolean("chat.message_placeholders", true);
        this.bypassPermission = cfg.getString("chat.bypass_permission", "ui.chat.custom.bypass");

        // Mode: static | channels
        String modeStr = cfg.getString("chat.mode", "static").toLowerCase();
        this.mode = modeStr.equals("channels") ? Mode.CHANNELS : Mode.STATIC;

        // STATIC format
        this.staticFormat = cfg.getString("chat.format",
                "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%");

        // CHANNELS
        this.channelFormats = new HashMap<>();
        this.localRadius = cfg.getInt("chat.channels.local.radius", 100);

        if (mode == Mode.CHANNELS) {
            String basePath = "chat.channels";

            channelFormats.put(ChatChannel.LOCAL,
                    cfg.getString(basePath + ".local.format",
                            "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb<dark_gray>[<white>L<dark_gray>] <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%"));
            channelFormats.put(ChatChannel.GLOBAL,
                    cfg.getString(basePath + ".global.format",
                            "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb<dark_gray>[<white>G<dark_gray>] <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%"));
            channelFormats.put(ChatChannel.WORLD,
                    cfg.getString(basePath + ".world.format",
                            "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb<dark_gray>[<white>W<dark_gray>] <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%"));
            channelFormats.put(ChatChannel.PRIVATE,
                    cfg.getString(basePath + ".private.format",
                            "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb<dark_gray>[<white>P<dark_gray>] <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%"));
            channelFormats.put(ChatChannel.ADMIN,
                    cfg.getString(basePath + ".admin.format",
                            "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb<dark_gray>[<white>A<dark_gray>] <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%"));

            this.localRadius = cfg.getInt(basePath + ".local.radius", 100);
        }

        ConsoleLogger.info("[Chat] Custom chat " + (enabled ? "enabled" : "disabled")
                + " | mode=" + mode.name().toLowerCase()
                + " | player-minimessage=" + playerMiniMessage
                + " | message-placeholders=" + messagePlaceholders);
    }

    // ========================= EVENT HANDLERS =========================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerChannelManager.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!enabled) return;

        // Bypass permission
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return;

        // Determine channel + format
        ChatChannel channel = null;
        String format;

        if (mode == Mode.CHANNELS) {
            channel = PlayerChannelManager.getChannel(player);
            format = channelFormats.get(channel);
            if (format == null || format.isEmpty()) format = staticFormat;
        } else {
            format = staticFormat;
        }

        if (format == null || format.isEmpty()) return;

        // Build message component
        String rawMessage = event.getMessage();
        if (messagePlaceholders) {
            rawMessage = PlaceholderResolver.resolve(rawMessage, player);
        }

        // Resolve placeholders in format (except %message%)
        String resolved = PlaceholderResolver.resolve(format, player);

        // PING PROCESSING
        ChatPingManager.PingResult pingResult = ChatPingManager.processPings(rawMessage, player);
        String pingedMessage = pingResult.formattedMessage();
        List<Player> pingedPlayers = pingResult.pingedPlayers();

        // Build final broadcast component
        Component broadcast;
        String msgForBroadcast = pingedPlayers.isEmpty() ? rawMessage : pingedMessage;
        boolean hasMessageToken = resolved.contains("%message%");

        if (!hasMessageToken) {
            broadcast = MessageUtil.parse(resolved)
                    .append(Component.text(" "))
                    .append(parseMessageComponentForPing(msgForBroadcast, player));
        } else if (playerMiniMessage) {
            Component msgComp = parseMessageComponentForPing(msgForBroadcast, player);
            String serializedMsg = MM.serialize(msgComp);
            String finalFormat = resolved.replace("%message%", serializedMsg);
            broadcast = MessageUtil.parse(finalFormat);
        } else {
            String escapedRaw = rawMessage.replace("<", "\\<").replace(">", "\\>");
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

        // Cancel original event — we broadcast manually
        event.setCancelled(true);

        // Determine recipients based on channel
        java.util.Set<Player> recipients = event.getRecipients();
        if (mode == Mode.CHANNELS && channel != null) {
            recipients = resolveChannelRecipients(player, channel);
        }

        if (recipients == null || recipients.isEmpty()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(broadcast);
            }
        } else {
            for (Player recipient : recipients) {
                recipient.sendMessage(broadcast);
            }
            if (!recipients.contains(player)) {
                player.sendMessage(broadcast);
            }
        }

        // Console log
        ConsoleLogger.info(PlainTextComponentSerializer.plainText().serialize(broadcast));

        // Ping sounds
        if (!pingedPlayers.isEmpty()) {
            ChatPingManager.notifyPingedPlayers(pingedPlayers, player);
        }
    }

    // ========================= CHANNEL RECIPIENTS =========================

    /**
     * Returns the set of players who should receive the message for a given channel.
     */
    private java.util.Set<Player> resolveChannelRecipients(Player sender, ChatChannel channel) {
        java.util.HashSet<Player> result = new java.util.HashSet<>();

        switch (channel) {
            case GLOBAL -> {
                // Everyone online
                result.addAll(Bukkit.getOnlinePlayers());
            }
            case LOCAL -> {
                // Within radius
                double radiusSq = (double) localRadius * localRadius;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(sender.getWorld())
                            && p.getLocation().distanceSquared(sender.getLocation()) <= radiusSq) {
                        result.add(p);
                    }
                }
            }
            case WORLD -> {
                // Same world
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(sender.getWorld())) {
                        result.add(p);
                    }
                }
            }
            case PRIVATE -> {
                // Sender + target
                result.add(sender);
                String targetName = PlayerChannelManager.getPrivateTarget(sender);
                if (targetName != null) {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) result.add(target);
                }
            }
            case ADMIN -> {
                // Players with ui.chat.channel.admin permission
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission(ChatChannel.ADMIN.getPermission()) || p.isOp()) {
                        result.add(p);
                    }
                }
            }
        }
        return result;
    }

    // ========================= HELPERS =========================

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

    // ========================= PUBLIC API =========================

    /** Returns true if chat is enabled. */
    public static boolean isEnabled() { return instance != null && instance.enabled; }

    /** Returns the current mode. */
    public static Mode getMode() { return instance != null ? instance.mode : Mode.STATIC; }

    /** Returns local radius (used by LOCAL channel). */
    public static int getLocalRadius() { return instance != null ? instance.localRadius : 100; }
}
