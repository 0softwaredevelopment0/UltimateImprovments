package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.api.CheckBridge;

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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private boolean consoleEnabled;
    private java.util.List<String> consoleWhitelist = java.util.List.of();
    private Blacklist consoleBlacklist = new Blacklist(false, List.of());
    private boolean linuxEnabled;
    private java.util.List<String> linuxWhitelist = java.util.List.of();
    private Blacklist linuxBlacklist = new Blacklist(false, List.of());

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

        // Console channel settings (loaded in both modes so the toggle can validate)
        this.consoleEnabled = cfg.getBoolean("chat.channels.console.enabled", true);
        this.consoleWhitelist = cfg.getStringList("chat.channels.console.whitelist");
        this.consoleBlacklist = loadBlacklist(cfg, "chat.channels.console.blacklist");

        // Linux (host terminal) channel settings
        this.linuxEnabled = cfg.getBoolean("chat.channels.linux.enabled", false);
        this.linuxWhitelist = cfg.getStringList("chat.channels.linux.whitelist");
        this.linuxBlacklist = loadBlacklist(cfg, "chat.channels.linux.blacklist");

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
            channelFormats.put(ChatChannel.CHECK,
                    cfg.getString(basePath + ".check.format",
                            "<green>\u1d04\u1d1c\u1d04\u1d07 <dark_gray>\u00bb<dark_gray>[<red>C<dark_gray>] <reset>%luckperms_prefix%<white>%player_name%<gray>: <white>%message%"));

            this.localRadius = cfg.getInt(basePath + ".local.radius", 100);
        }

        ConsoleLogger.info("[Chat] Custom chat " + (enabled ? "enabled" : "disabled")
                + " | mode=" + mode.name().toLowerCase()
                + " | player-minimessage=" + playerMiniMessage
                + " | message-placeholders=" + messagePlaceholders);
    }

    // ========================= EVENT HANDLERS =========================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Restore the player's channel + private target from the DB
        PlayerChannelManager.loadFromDatabase(event.getPlayer());
    }

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

        // CONSOLE channel — the message is executed as a console command.
        // No chat format is broadcast: nothing is written to chat by this plugin.
        if (channel == ChatChannel.CONSOLE) {
            if (!consoleEnabled || !isConsoleAllowed(player)) {
                // Channel disabled or access revoked while the mode was active
                // (permission removed / de-whitelisted)
                boolean disabled = !consoleEnabled;
                PlayerChannelManager.setChannel(player, ChatChannel.GLOBAL);
                player.sendMessage(MessageUtil.parse(disabled
                        ? "<red>\u274c Console channel is disabled — chat restored.</red>"
                        : "<red>\u274c Console channel access revoked — chat restored.</red>"));
                channel = ChatChannel.GLOBAL;
                format = channelFormats.get(channel);
                if (format == null || format.isEmpty()) format = staticFormat;
            } else {
                event.setCancelled(true);
                String cmd = event.getMessage().trim();
                if (!cmd.isEmpty()) {
                    String forbidden = consoleBlacklist.findForbidden(cmd);
                    if (forbidden != null) {
                        sendForbiddenWarning(player, cmd, forbidden);
                        ConsoleLogger.warn("[ConsoleChat] blocked " + player.getName()
                                + ": /" + cmd + " (forbidden: " + forbidden + ")");
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        ConsoleLogger.info("[ConsoleChat] " + player.getName() + " executed: /" + cmd);
                    }
                }
                return;
            }
        }

        // LINUX channel — the message is executed on the host shell (terminal),
        // its output is sent back to the player, and ^C acts like Ctrl+C.
        if (channel == ChatChannel.LINUX) {
            if (!linuxEnabled || !isLinuxAllowed(player)) {
                // Channel disabled or access revoked while the mode was active
                boolean disabled = !linuxEnabled;
                PlayerChannelManager.setChannel(player, ChatChannel.GLOBAL);
                player.sendMessage(MessageUtil.parse(disabled
                        ? "<red>\u274c Linux channel is disabled — chat restored.</red>"
                        : "<red>\u274c Linux channel access revoked — chat restored.</red>"));
                channel = ChatChannel.GLOBAL;
                format = channelFormats.get(channel);
                if (format == null || format.isEmpty()) format = staticFormat;
            } else {
                event.setCancelled(true);
                String cmd = event.getMessage().trim();
                if (cmd.isEmpty()) return;
                if (cmd.equalsIgnoreCase("^C")) {
                    HostTerminal.interrupt(player);
                    return;
                }
                String forbidden = linuxBlacklist.findForbidden(cmd);
                if (forbidden != null) {
                    sendForbiddenWarning(player, cmd, forbidden);
                    ConsoleLogger.warn("[LinuxChat] blocked " + player.getName()
                            + ": " + cmd + " (forbidden: " + forbidden + ")");
                } else {
                    HostTerminal.execute(player, cmd);
                    ConsoleLogger.info("[LinuxChat] " + player.getName() + " ran: " + cmd);
                }
                return;
            }
        }

        // PRIVATE channel renders with the same style as /msg
        // (sender sees "You » target", receiver sees "sender » You").
        if (channel == ChatChannel.PRIVATE) {
            sendPrivateStyled(player, event.getMessage());
            event.setCancelled(true);
            return;
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
            case CHECK -> {
                // Messages go ONLY to the inspector (moderator).
                // If the player is no longer under a check, fall back to global.
                result.add(sender);
                CheckBridge bridge = CheckBridge.get();
                Player inspector = bridge != null ? bridge.getInspector(sender) : null;
                if (inspector != null && inspector.isOnline()) {
                    result.add(inspector);
                } else {
                    result.addAll(Bukkit.getOnlinePlayers());
                }
            }
        }
        return result;
    }

    // =========================
    // PRIVATE CHANNEL — rendered like /msg (sender: "You » target", receiver: "sender » You")
    // =========================
    private void sendPrivateStyled(Player sender, String raw) {
        String targetName = PlayerChannelManager.getPrivateTarget(sender);
        if (targetName == null) {
            sender.sendMessage(MessageUtil.parse("<red>No private chat target set. Use </red><white>/ui chatchnl private <player></white>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Private chat target is offline.</red>"));
            return;
        }
        String msg = messagePlaceholders ? PlaceholderResolver.resolve(raw, sender) : raw;
        String escaped = msg.replace("<", "\\<").replace(">", "\\>")
                .replaceAll("\u00A7[0-9a-fk-orx]", "").replace("\u00A7", "");
        String senderView = "<white>[<gray>You <yellow>» <gray>" + target.getName() + "<white>] <reset>" + escaped;
        String targetView = "<white>[<gray>" + sender.getName() + " <yellow>» <gray>You<white>] <reset>" + escaped;
        sender.sendMessage(MM.deserialize(senderView));
        target.sendMessage(MM.deserialize(targetView));
        ConsoleLogger.info("[Private] " + sender.getName() + " → " + target.getName() + ": " + msg);
    }

    // ========================= HELPERS =========================

    /**
     * Loads a blacklist from config, reading its numbered units:
     * <pre>units:
     *   1:
     *     regex: &quot;...&quot;
     *     wildcard: &quot;*...*&quot;</pre>
     * Units are evaluated in numeric order (1, 2, 3, ...). A unit matches if its
     * regex OR its wildcard matches.
     */
    private static Blacklist loadBlacklist(FileConfiguration cfg, String path) {
        boolean enabled = cfg.getBoolean(path + ".enabled", true);
        java.util.ArrayList<BlacklistUnit> units = new java.util.ArrayList<>();
        org.bukkit.configuration.ConfigurationSection sec = cfg.getConfigurationSection(path + ".units");
        if (sec != null) {
            List<String> keys = new java.util.ArrayList<>(sec.getKeys(false));
            keys.sort((a, b) -> Integer.compare(parseUnitKey(a), parseUnitKey(b)));
            for (String key : keys) {
                String regex = sec.getString(key + ".regex", "");
                String wildcard = sec.getString(key + ".wildcard", "");
                units.add(new BlacklistUnit(regex, wildcard));
            }
        }
        return new Blacklist(enabled, units);
    }

    private static int parseUnitKey(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Converts a wildcard to a regex. {@code *} matches any text (empty included),
     * so {@code *rm -rf /*} matches any command containing {@code rm -rf /}.
     */
    private static Pattern compileWildcard(String wc) {
        StringBuilder sb = new StringBuilder("(?s)^");
        for (int i = 0; i < wc.length(); i++) {
            char c = wc.charAt(i);
            if (c == '*') sb.append(".*");
            else if ("\\.^$+?{}[]()|".indexOf(c) >= 0) sb.append('\\').append(c);
            else sb.append(c);
        }
        sb.append('$');
        try {
            return Pattern.compile(sb.toString());
        } catch (java.util.regex.PatternSyntaxException e) {
            ConsoleLogger.warn("[Chat] Invalid console-channel blacklist wildcard: " + wc
                    + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /** A single blacklist unit — an optional regex and/or an optional wildcard. */
    private static final class BlacklistUnit {
        private final List<Pattern> patterns = new java.util.ArrayList<>();
        private final String source;

        BlacklistUnit(String regex, String wildcard) {
            String src = "";
            if (regex != null && !regex.isBlank()) {
                try {
                    patterns.add(Pattern.compile(regex));
                    src = "regex:" + regex;
                } catch (java.util.regex.PatternSyntaxException e) {
                    ConsoleLogger.warn("[Chat] Invalid console-channel blacklist regex: " + regex
                            + " (" + e.getMessage() + ")");
                }
            }
            if (wildcard != null && !wildcard.isBlank()) {
                Pattern p = compileWildcard(wildcard);
                if (p != null) {
                    patterns.add(p);
                    src = (src.isEmpty() ? "" : src + " ") + "wildcard:" + wildcard;
                }
            }
            this.source = src.isEmpty() ? "(empty)" : src;
        }

        /** Returns the first forbidden substring matched in cmd, or null. */
        String findForbidden(String cmd) {
            for (Pattern p : patterns) {
                Matcher m = p.matcher(cmd);
                if (m.find()) return m.group();
            }
            return null;
        }

        String getSource() { return source; }
    }

    /** An ordered blacklist made of numbered units, with an enabled toggle. */
    private static final class Blacklist {
        private final boolean enabled;
        private final List<BlacklistUnit> units;

        Blacklist(boolean enabled, List<BlacklistUnit> units) {
            this.enabled = enabled;
            this.units = units;
        }

        /** Returns the first forbidden substring matched by any enabled unit, or null. */
        String findForbidden(String cmd) {
            if (!enabled) return null;
            for (BlacklistUnit u : units) {
                String f = u.findForbidden(cmd);
                if (f != null) return f;
            }
            return null;
        }
    }

    /**
     * Tells the player their command was cancelled and shows it with the
     * forbidden part highlighted in red.
     */
    private void sendForbiddenWarning(Player player, String cmd, String forbidden) {
        String esc = cmd.replace("<", "\\<").replace(">", "\\>");
        String fEsc = forbidden.replace("<", "\\<").replace(">", "\\>");
        String highlighted = esc.replace(fEsc, "<red>" + fEsc + "</red>");
        player.sendMessage(MessageUtil.parse(
                "<red>\u274c Your command contains forbidden content and was cancelled.</red>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>  Command: </gray><white>" + highlighted + "</white>"));
    }

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

    /** Returns true if the console channel is enabled in the config. */
    public static boolean isConsoleEnabled() { return instance == null || instance.consoleEnabled; }

    /**
     * Returns true if the player is whitelisted for the console channel
     * (nickname in the config whitelist, case-insensitive).
     */
    public static boolean isConsoleWhitelisted(Player player) {
        if (instance == null || player == null) return false;
        for (String name : instance.consoleWhitelist) {
            if (name != null && name.equalsIgnoreCase(player.getName())) return true;
        }
        return false;
    }

    /**
     * Returns true if the player may use the console channel:
     * requires the channel permission AND a whitelist entry.
     */
    public static boolean isConsoleAllowed(Player player) {
        return player != null
                && player.hasPermission(ChatChannel.CONSOLE.getPermission())
                && isConsoleWhitelisted(player);
    }

    /** Returns true if the linux (host terminal) channel is enabled in the config. */
    public static boolean isLinuxEnabled() { return instance != null && instance.linuxEnabled; }

    /**
     * Returns true if the player is whitelisted for the linux channel
     * (nickname in the config whitelist, case-insensitive).
     */
    public static boolean isLinuxWhitelisted(Player player) {
        if (instance == null || player == null) return false;
        for (String name : instance.linuxWhitelist) {
            if (name != null && name.equalsIgnoreCase(player.getName())) return true;
        }
        return false;
    }

    /**
     * Returns true if the player may use the linux channel:
     * requires the channel permission AND a whitelist entry.
     */
    public static boolean isLinuxAllowed(Player player) {
        return player != null
                && player.hasPermission(ChatChannel.LINUX.getPermission())
                && isLinuxWhitelisted(player);
    }
}
