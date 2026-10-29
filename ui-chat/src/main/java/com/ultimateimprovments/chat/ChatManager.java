package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.api.CheckBridge;
import com.ultimateimprovments.config.MessagesManager;

import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.util.ConsoleLogger;
import io.papermc.paper.event.player.AsyncChatEvent;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    // ===== LIMITS (chat.limits.*) =====
    /**
     * Abuse limits applied to every player message: rendered line count, total
     * characters, share of capital letters and share of "random characters".
     * See {@link ChatText} for the metrics.
     */
    private boolean limitsEnabled = true;
    /** Max number of rendered lines for a single player message. */
    private int maxLines = ChatText.DEFAULT_MAX_LINES;
    /** Max characters of a single message, counted across all rendered lines. */
    private int maxChars = ChatText.DEFAULT_MAX_CHARS;
    /** Max share of capital letters (0 disables the check, 100 = allow everything). */
    private int maxCapsPercent = 50;
    /** Max share of "random looking" characters (0 disables the check, 100 = allow everything). */
    private int maxEntropyPercent = 50;
    /** Minimum interval between two messages of one player (0 disables the cooldown). */
    private int cooldownMs = 1000;
    /** Messages of at most this many characters skip the CAPS check. */
    private int capsIgnoreMaxChars = 8;
    /** Blocks messages containing Cyrillic letters (non-Cyrillic servers). */
    private boolean blockCyrillic = false;
    private String limitsBypassPermission = "ui.chat.limits.bypass";

    /** Cooldown notice throttle: a spammer must not be answered with warning spam. */
    private static final long COOLDOWN_NOTICE_INTERVAL_MS = 1000L;
    /** Last accepted message time per player (chat cooldown). */
    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    /** Last cooldown notice time per player (throttles the warning itself). */
    private final Map<UUID, Long> lastCooldownNoticeAt = new ConcurrentHashMap<>();

    private boolean consoleEnabled;
    private java.util.List<String> consoleWhitelist = java.util.List.of();
    private AccessControl consoleAccessControl = AccessControl.disabled();
    private boolean terminalEnabled;
    private java.util.List<String> terminalWhitelist = java.util.List.of();
    private AccessControl terminalAccessControl = AccessControl.disabled();
    // Chat-wide access control: filters message content in ALL channels
    private AccessControl chatAccessControl = AccessControl.disabled();

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
        // Single config lives in UI-Core (Main.getInstance().getConfig()).
        return Main.getInstance().getConfig();
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
        // LIMITS — one player message is rendered line by line, so its size and
        // shape must be capped; caps/random checks stop abuse that formatting
        // cannot repair. Legacy key chat.max_lines (alpha.39) is still honoured.
        this.limitsEnabled = cfg.getBoolean("chat.limits.enabled", true);
        int defaultLines = Math.max(1, cfg.getInt("chat.max_lines", ChatText.DEFAULT_MAX_LINES));
        this.maxLines = Math.max(1, cfg.getInt("chat.limits.max_lines", defaultLines));
        this.maxChars = cfg.getInt("chat.limits.max_chars", ChatText.DEFAULT_MAX_CHARS);
        this.maxCapsPercent = cfg.getInt("chat.limits.max_caps_percent", 50);
        this.maxEntropyPercent = cfg.getInt("chat.limits.max_entropy_percent", 50);
        this.cooldownMs = Math.max(0, cfg.getInt("chat.limits.cooldown_ms", 1000));
        this.capsIgnoreMaxChars = Math.max(0, cfg.getInt("chat.limits.caps_ignore_max_chars", 8));
        this.blockCyrillic = cfg.getBoolean("chat.limits.block_cyrillic", false);
        this.limitsBypassPermission = cfg.getString("chat.limits.bypass_permission", "ui.chat.limits.bypass");

        // Console channel settings (loaded in both modes so the toggle can validate)
        this.consoleEnabled = cfg.getBoolean("chat.channels.console.enabled", true);
        this.consoleWhitelist = cfg.getStringList("chat.channels.console.whitelist");
        this.consoleAccessControl = AccessControl.load(cfg, "chat.channels.console.access_control");

        // Terminal (host shell) channel settings. Falls back to the legacy
        // "chat.channels.linux" section for configs created before the rename.
        String terminalBase = cfg.contains("chat.channels.terminal.enabled")
                || !cfg.contains("chat.channels.linux.enabled")
                ? "chat.channels.terminal" : "chat.channels.linux";
        this.terminalEnabled = cfg.getBoolean(terminalBase + ".enabled", false);
        this.terminalWhitelist = cfg.getStringList(terminalBase + ".whitelist");
        this.terminalAccessControl = AccessControl.load(cfg, terminalBase + ".access_control");

        // Chat-wide access control for message content (all channels)
        this.chatAccessControl = AccessControl.load(cfg, "chat.access_control");

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
        // Drop the cooldown state so a reconnect starts clean and the maps do
        // not grow with players who already left.
        UUID id = event.getPlayer().getUniqueId();
        lastMessageAt.remove(id);
        lastCooldownNoticeAt.remove(id);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String messageText = PlainTextComponentSerializer.plainText().serialize(event.message());

        // MUTE CHECK — enforced even when the custom chat is disabled
        // (chat.enabled: false): previously it sat behind the enabled gate,
        // so a muted player could keep talking with the vanilla chat.
        // PunishJoinListener.isMuted() reads the DB-backed mute cache (refreshed
        // on login/join and when /ui punish mute is issued). Async event → DB
        // lookups are safe here.
        if (com.ultimateimprovments.punish.PunishJoinListener.isMuted(player)) {
            event.setCancelled(true);
            PunishmentManager.PunishmentRecord mute =
                    com.ultimateimprovments.punish.PunishJoinListener.getMuteRecord(player);
            String duration = mute != null
                    ? (mute.expiresAt == 0 ? "permanent"
                       : PunishmentManager.formatRemaining(Math.max(0, mute.expiresAt - System.currentTimeMillis())))
                    : "";
            player.sendMessage(MessageUtil.parse(
                    "<red>⛔ You are muted!</red>"
                            + (duration.isEmpty() ? "" : " <gray>Time remaining:</gray> <white>" + duration + "</white>")));
            return;
        }

        // CHAT LIMITS — length / CAPS / random-soup abuse.
        // Enforced even when the custom chat is disabled (chat.enabled: false):
        // these are protections, not formatting. CONSOLE/TERMINAL channels run
        // trusted staff command input and are left untouched.
        if (limitsEnabled
                && (limitsBypassPermission.isEmpty() || !player.hasPermission(limitsBypassPermission))
                && !isCommandChannel(player)) {

            long now = System.currentTimeMillis();

            // COOLDOWN — too frequent messages are dropped before they are
            // rendered. The timestamp is remembered only for a message that
            // passes every check, so a rejected message does not eat the slot.
            if (cooldownMs > 0) {
                Long last = lastMessageAt.get(player.getUniqueId());
                if (last != null && now - last < cooldownMs) {
                    event.setCancelled(true);
                    notifyCooldown(player, cooldownMs - (now - last), now);
                    return;
                }
            }

            // CYRILLIC — optional "no Cyrillic" mode (default off).
            if (blockCyrillic && ChatText.containsCyrillic(messageText)) {
                event.setCancelled(true);
                player.sendMessage(MessageUtil.parse(
                        MessagesManager.getString("chat_limits.cyrillic",
                                "<dark_red>❌</dark_red> <red>Cyrillic characters are not allowed in this chat.</red>")));
                ConsoleLogger.warn("[Chat] blocked " + player.getName()
                        + " — Cyrillic text: " + messageText);
                return;
            }

            // CAPS — always the SHARE of capital letters, whatever the length.
            // Only messages of at most capsIgnoreMaxChars characters skip the
            // check ("OK", "LOL", "GO" must stay allowed).
            if (maxCapsPercent > 0 && maxCapsPercent < 100
                    && ChatText.charCount(messageText.strip()) > capsIgnoreMaxChars) {
                int caps = ChatText.capsPercent(messageText);
                if (caps > maxCapsPercent) {
                    event.setCancelled(true);
                    player.sendMessage(MessageUtil.parse(
                            MessagesManager.getString("chat_limits.caps",
                                            "<dark_red>❌</dark_red> <red>Too many capital letters: %caps% of the allowed %limit%. Please write normally.</red>")
                                    .replace("%caps%", String.valueOf(caps))
                                    .replace("%limit%", String.valueOf(maxCapsPercent))));
                    ConsoleLogger.warn("[Chat] blocked " + player.getName()
                            + " — caps " + caps + "% (limit " + maxCapsPercent + "%): " + messageText);
                    return;
                }
            }

            // RANDOM SOUP: share of characters inside random-looking tokens.
            if (maxEntropyPercent > 0 && maxEntropyPercent < 100) {
                int random = ChatText.randomnessPercent(messageText);
                if (random > maxEntropyPercent) {
                    event.setCancelled(true);
                    player.sendMessage(MessageUtil.parse(
                            MessagesManager.getString("chat_limits.random",
                                    "<dark_red>❌</dark_red> <red>Your message looks like a random character set and was not sent.</red>")));
                    ConsoleLogger.warn("[Chat] blocked " + player.getName()
                            + " — random text " + random + "% (limit " + maxEntropyPercent + "%): " + messageText);
                    return;
                }
            }

            // The message passed every check — remember it for the cooldown.
            if (cooldownMs > 0) lastMessageAt.put(player.getUniqueId(), now);
        }

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
                String cmd = messageText.trim();
                if (!cmd.isEmpty()) {
                    AccessControl.Result r = consoleAccessControl.decide(cmd);
                    if (r.isAllowed()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        ConsoleLogger.info("[ConsoleChat] " + player.getName() + " executed: /" + cmd);
                    } else {
                        sendAccessDenied(player, cmd, r);
                        if (r.isDeniedByBlacklist()) {
                            ConsoleLogger.warn("[ConsoleChat] blocked " + player.getName()
                                    + ": /" + cmd + " (forbidden: " + r.forbidden() + ")");
                        } else {
                            ConsoleLogger.warn("[ConsoleChat] not allowed " + player.getName() + ": /" + cmd);
                        }
                    }
                }
                return;
            }
        }

        // TERMINAL channel — the message is executed on the host shell,
        // its output is sent back to the player, and ^C acts like Ctrl+C.
        if (channel == ChatChannel.TERMINAL) {
            if (!terminalEnabled || !isTerminalAllowed(player)) {
                // Channel disabled or access revoked while the mode was active
                boolean disabled = !terminalEnabled;
                PlayerChannelManager.setChannel(player, ChatChannel.GLOBAL);
                player.sendMessage(MessageUtil.parse(disabled
                        ? "<red>\u274c Terminal channel is disabled — chat restored.</red>"
                        : "<red>\u274c Terminal channel access revoked — chat restored.</red>"));
                channel = ChatChannel.GLOBAL;
                format = channelFormats.get(channel);
                if (format == null || format.isEmpty()) format = staticFormat;
            } else {
                event.setCancelled(true);
                String cmd = messageText.trim();
                if (cmd.isEmpty()) return;
                if (cmd.equalsIgnoreCase("^C")) {
                    HostTerminal.interrupt(player);
                    return;
                }
                AccessControl.Result r = terminalAccessControl.decide(cmd);
                if (r.isAllowed()) {
                    HostTerminal.execute(player, cmd);
                    ConsoleLogger.info("[TerminalChat] " + player.getName() + " ran: " + cmd);
                } else {
                    sendAccessDenied(player, cmd, r);
                    if (r.isDeniedByBlacklist()) {
                        ConsoleLogger.warn("[TerminalChat] blocked " + player.getName()
                                + ": " + cmd + " (forbidden: " + r.forbidden() + ")");
                    } else {
                        ConsoleLogger.warn("[TerminalChat] not allowed " + player.getName() + ": " + cmd);
                    }
                }
                return;
            }
        }

        // Chat-wide access control filters the message content in all normal
        // channels (console/terminal are handled above on their command input).
        AccessControl.Result chatResult = chatAccessControl.decide(messageText);
        if (!chatResult.isAllowed()) {
            event.setCancelled(true);
            sendAccessDenied(player, messageText, chatResult);
            return;
        }

        // PRIVATE channel renders with the same style as /msg
        // (sender sees "You » target", receiver sees "sender » You").
        if (channel == ChatChannel.PRIVATE) {
            sendPrivateStyled(player, messageText);
            event.setCancelled(true);
            return;
        }

        if (format == null || format.isEmpty()) return;

        // Build message component.
        //
        // SECURITY: the message is NEVER rendered as one blob. It is split into
        // single lines (real line separators AND newline-producing MiniMessage
        // tags) and every line goes through the FULL channel format, so each
        // rendered line carries the prefix and the sender name. Before this a
        // player could send "<newline>[Server] Restart in 5s" and the second
        // line was rendered without any prefix — an embedded fake-line abuse.
        String rawMessage = messageText;
        if (messagePlaceholders) {
            rawMessage = PlaceholderResolver.resolve(rawMessage, player);
        }

        // Total length budget across all rendered lines ("…" included).
        rawMessage = ChatText.charsToKeep(rawMessage, maxChars);

        // Resolve placeholders in format (except %message%)
        String resolved = PlaceholderResolver.resolve(format, player);

        List<String> messageLines = ChatText.splitLines(rawMessage, playerMiniMessage, maxLines);

        java.util.LinkedHashSet<Player> pingedPlayers = new java.util.LinkedHashSet<>();
        Component broadcast = Component.empty();
        for (int i = 0; i < messageLines.size(); i++) {
            // PING PROCESSING — per line; the ping style itself adds no line breaks
            ChatPingManager.PingResult pingResult =
                    ChatPingManager.processPings(messageLines.get(i), player);
            pingedPlayers.addAll(pingResult.pingedPlayers());

            if (i > 0) broadcast = broadcast.append(Component.newline());
            broadcast = broadcast.append(
                    renderMessageLine(resolved, pingResult.formattedMessage(), player));
        }

        // Cancel original event — we broadcast manually
        event.setCancelled(true);

        // Determine recipients based on channel
        java.util.Set<Player> recipients = new java.util.LinkedHashSet<>(Bukkit.getOnlinePlayers());
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
            ChatPingManager.notifyPingedPlayers(new java.util.ArrayList<>(pingedPlayers), player);
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
        msg = ChatText.charsToKeep(msg, maxChars);

        // Same multi-line hardening as the channel broadcast: every line is
        // rendered with its own "[… » …]" prefix, otherwise a <newline> in a
        // private message would produce prefix-less (fake system) lines.
        String senderPrefix = "<white>[<gray>You <yellow>» <gray>" + target.getName() + "<white>] <reset>";
        String targetPrefix = "<white>[<gray>" + sender.getName() + " <yellow>» <gray>You<white>] <reset>";
        Component senderView = Component.empty();
        Component targetView = Component.empty();
        List<String> lines = ChatText.splitLines(msg, playerMiniMessage, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            String escaped = ChatText.escapeMiniMessage(lines.get(i));
            if (i > 0) {
                senderView = senderView.append(Component.newline());
                targetView = targetView.append(Component.newline());
            }
            senderView = senderView.append(MM.deserialize(senderPrefix + escaped));
            targetView = targetView.append(MM.deserialize(targetPrefix + escaped));
        }
        sender.sendMessage(senderView);
        target.sendMessage(targetView);
        ConsoleLogger.info("[Private] " + sender.getName() + " → " + target.getName() + ": " + msg);
    }

    // ========================= HELPERS =========================

    /**
     * Tells the player their command was cancelled and shows it with the
     * forbidden part highlighted in red.
     */
    private void sendForbiddenWarning(Player player, String cmd, String forbidden) {
        String esc = escapeMini(cmd);
        String fEsc = escapeMini(forbidden);
        String highlighted = esc.replace(fEsc, "<red>" + fEsc + "</red>");
        player.sendMessage(MessageUtil.parse(
                "<red>\u274c Your command contains forbidden content and was cancelled.</red>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>  Command: </gray><white>" + highlighted + "</white>"));
    }

    /**
     * Tells the player their command was cancelled because it is not in the
     * access-control allow-list (whitelist unit present, command matched none).
     */
    private void sendNotAllowedWarning(Player player, String cmd) {
        player.sendMessage(MessageUtil.parse(
                "<red>\u274c Command is not allowed by the access control.</red>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>  Command: </gray><white>" + escapeMini(cmd) + "</white>"));
    }

    /**
     * Displays the access-control denial for the player. If the denying unit
     * defines a custom message, it is sent (with the plugin prefix); otherwise
     * the default forbidden/not-allowed warnings are used.
     */
    private void sendAccessDenied(Player player, String content, AccessControl.Result r) {
        String custom = r.customDenyMessage();
        if (custom != null && !custom.isBlank()) {
            player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX + " " + custom));
            return;
        }
        if (r.isDeniedByBlacklist()) {
            sendForbiddenWarning(player, content, r.forbidden());
        } else {
            sendNotAllowedWarning(player, content);
        }
    }

    /**
     * Tells the player to slow down. The notice itself is throttled to one per
     * second per player: a spammer would otherwise be answered with a chat
     * flood of warnings.
     */
    private void notifyCooldown(Player player, long remainingMs, long now) {
        Long lastNotice = lastCooldownNoticeAt.get(player.getUniqueId());
        if (lastNotice != null && now - lastNotice < COOLDOWN_NOTICE_INTERVAL_MS) return;
        lastCooldownNoticeAt.put(player.getUniqueId(), now);
        player.sendMessage(MessageUtil.parse(
                MessagesManager.getString("chat_limits.cooldown",
                                "<dark_red>❌</dark_red> <red>Slow down! Wait %remaining%s before the next message.</red>")
                        .replace("%remaining%", formatSeconds(remainingMs))));
    }

    /** Milliseconds to a one-decimal seconds label ({@code 850 → "0.9"}). */
    private static String formatSeconds(long millis) {
        double seconds = Math.max(0.1, millis / 1000.0);
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    /**
     * Returns true when the player is chatting in a command channel
     * (CONSOLE / TERMINAL): the text is executed, not rendered, and belongs to
     * trusted staff, so the abuse limits do not apply.
     */
    private boolean isCommandChannel(Player player) {
        if (mode != Mode.CHANNELS) return false;
        ChatChannel current = PlayerChannelManager.getChannel(player);
        return current == ChatChannel.CONSOLE || current == ChatChannel.TERMINAL;
    }

    /** Escapes MiniMessage tag characters so raw command text renders literally. */
    private static String escapeMini(String text) {
        return ChatText.escapeMiniMessage(text);
    }

    /**
     * Renders ONE message line through the channel format. Because the line is
     * guaranteed to be a single line and it is inserted AFTER the format prefix,
     * a player can never emit a prefix-less (embedded) line.
     *
     * @param resolvedFormat format with placeholders resolved (still contains
     *                       {@code %message%} unless the format omits it)
     * @param line           one single-line message part (pings already applied)
     * @param player         the sender
     */
    private Component renderMessageLine(String resolvedFormat, String line, Player player) {
        if (!resolvedFormat.contains("%message%")) {
            return MessageUtil.parse(resolvedFormat)
                    .append(Component.text(" "))
                    .append(parseMessageContent(line));
        }
        if (playerMiniMessage) {
            String serialized = MM.serialize(parseMessageContent(line));
            try {
                return MessageUtil.parse(resolvedFormat.replace("%message%", serialized));
            } catch (Exception e) {
                return MessageUtil.parse(resolvedFormat.replace("%message%", ""))
                        .append(Component.text(line));
            }
        }
        String escaped = ChatText.escapeMiniMessage(line);
        try {
            return MessageUtil.parse(resolvedFormat.replace("%message%", escaped));
        } catch (Exception e) {
            return MessageUtil.parse(resolvedFormat.replace("%message%", ""))
                    .append(Component.text(line));
        }
    }

    /**
     * Turns one message line into a component: MiniMessage when the player is
     * allowed to use it, plain text otherwise. Placeholders were already
     * resolved by the caller, so they are not resolved a second time here.
     */
    private Component parseMessageContent(String line) {
        if (!playerMiniMessage) return Component.text(line);
        try {
            return MM.deserialize(line);
        } catch (Exception e) {
            return Component.text(line);
        }
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

    /** Returns true if the terminal (host shell) channel is enabled in the config. */
    public static boolean isTerminalEnabled() { return instance != null && instance.terminalEnabled; }

    /**
     * Returns true if the player is whitelisted for the terminal channel
     * (nickname in the config whitelist, case-insensitive).
     */
    public static boolean isTerminalWhitelisted(Player player) {
        if (instance == null || player == null) return false;
        for (String name : instance.terminalWhitelist) {
            if (name != null && name.equalsIgnoreCase(player.getName())) return true;
        }
        return false;
    }

    /**
     * Returns true if the player may use the terminal channel:
     * requires the channel permission AND a whitelist entry.
     */
    public static boolean isTerminalAllowed(Player player) {
        return player != null
                && player.hasPermission(ChatChannel.TERMINAL.getPermission())
                && isTerminalWhitelisted(player);
    }
}
