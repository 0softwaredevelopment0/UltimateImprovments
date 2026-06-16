package com.mcplugin.server;

import com.mcplugin.Main;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🛡 PacketGuard — защита от пакетов, превышающих допустимый размер.
 * <p>
 * Перехватывает {@code PacketEncoder$PacketTooLargeException}, которая возникает,
 * когда сервер пытается отправить клиенту пакет размером больше лимита протокола (8 МБ).
 * <p>
 * Вместо стандартного сообщения "Internal Exception: PacketEncoder$PacketTooLargeException"
 * игрок получает читаемый кик с понятной причиной.
 * <p>
 * Причина возникновения: игрок создал/получил предмет с огромным объёмом NBT-данных
 * (например, книгу с миллионами страниц, сундук с краш-предметами), и при
 * отправке этого предмета в контейнере пакет превышает лимит.
 */
public class PacketGuard implements Listener {

    private static PacketGuard instance;

    // =========================
    // CONFIG FIELDS
    // =========================
    private static boolean enabled = true;
    private static boolean log = true;
    private static boolean logInject = false;
    private static String kickMessage = "§4❌ §cСлишком большой пакет данных!\n§7Пожалуйста, перезайдите на сервер.";
    private static String bypassPermission = "mcplugin.packetguard.bypass";

    // =========================
    // STATE
    // =========================
    private static final String HANDLER_NAME = "mcplugin_packet_guard";
    private final Map<UUID, Boolean> injected = new ConcurrentHashMap<>();

    // =========================
    // INIT / RELOAD
    // =========================
    public static void init(Main plugin) {
        instance = new PacketGuard();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        // Inject for already online players (e.g., after /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            instance.injectPlayer(player);
        }

        plugin.getLogger().info("[PacketGuard] Initialized. enabled=" + enabled);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("packet_guard");
        if (cfg == null) return;

        enabled = cfg.getBoolean("enabled", true);
        log = cfg.getBoolean("log", true);
        logInject = cfg.getBoolean("log_inject", false);
        kickMessage = cfg.getString("kick_message",
                "§4❌ §cСлишком большой пакет данных!\n§7Пожалуйста, перезайдите на сервер.");
        bypassPermission = cfg.getString("bypass_permission", "mcplugin.packetguard.bypass");
    }

    public static PacketGuard getInstance() {
        return instance;
    }

    // =========================
    // EVENTS
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }

    // =========================
    // NETTY PIPELINE INJECTION
    // =========================
    private void injectPlayer(Player player) {
        if (!enabled) return;
        if (player.hasPermission(bypassPermission)) return;
        if (injected.putIfAbsent(player.getUniqueId(), true) != null) return;

        try {
            Channel channel = getNettyChannel(player);
            if (channel == null) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;

            // Find the encoder to inject before it (to catch encoder exceptions)
            String encoderName = findHandlerName(channel, "PacketEncoder");

            ChannelDuplexHandler handler = new ChannelDuplexHandler() {

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    // Pass through — encoder will handle encoding and size validation
                    super.write(ctx, msg, promise);
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    // Check if this is a PacketTooLargeException
                    if (isPacketTooLargeException(cause)) {
                        handlePacketTooLarge(ctx, player, cause);
                        return;
                    }

                    // Not our exception — pass it through
                    ctx.fireExceptionCaught(cause);
                }
            };

            if (encoderName != null) {
                channel.pipeline().addBefore(encoderName, HANDLER_NAME, handler);
            } else {
                // Fallback: add first in the pipeline
                channel.pipeline().addFirst(HANDLER_NAME, handler);
            }

            if (logInject) {
                Main.getInstance().getLogger().info("[PacketGuard] Injected handler for " + player.getName());
            }

        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[PacketGuard] Failed to inject " + player.getName() + ": " + e.getMessage());
        }
    }

    private void removePlayer(Player player) {
        injected.remove(player.getUniqueId());
        try {
            Channel channel = getNettyChannel(player);
            if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // =========================
    // EXCEPTION CATCHING
    // =========================

    /**
     * Checks if the throwable (or its cause chain) is a PacketTooLargeException.
     */
    private boolean isPacketTooLargeException(Throwable cause) {
        // Check the exception itself
        if (cause.getClass().getName().contains("PacketTooLarge")) {
            return true;
        }

        // Check message
        String msg = cause.getMessage();
        if (msg != null && msg.contains("PacketTooLarge")) {
            return true;
        }

        // Check cause chain
        Throwable inner = cause.getCause();
        if (inner != null) {
            if (inner.getClass().getName().contains("PacketTooLarge")) {
                return true;
            }
            String innerMsg = inner.getMessage();
            if (innerMsg != null && innerMsg.contains("PacketTooLarge")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Cleanly handles the PacketTooLargeException by disconnecting the player
     * with a clear message instead of the generic "Internal Exception".
     */
    private void handlePacketTooLarge(ChannelHandlerContext ctx, Player player, Throwable cause) {
        String errorMsg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        if (cause.getCause() != null) {
            errorMsg = cause.getCause().getMessage() != null
                    ? cause.getCause().getMessage()
                    : cause.getCause().getClass().getSimpleName();
        }

        if (log) {
            Main.getInstance().getLogger().warning(
                    "[PacketGuard] § Caught PacketTooLargeException for " + player.getName()
                            + ": " + errorMsg
            );
        }

        // НЕ закрываем канал здесь — это бы вызвало дефолтный дисконнект
        // с сообщением "Connection closed". Вместо этого потребляем исключение
        // (не вызываем fireExceptionCaught) и кикаем игрока на главном потоке.
        // player.kickPlayer() сам закроет канал с нашим кастомным сообщением.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isOnline()) {
                player.kickPlayer(kickMessage);
            }
        });
    }

    // =========================
    // REFLECTION HELPERS
    // =========================

    /**
     * Gets the Netty channel for a player using reflection.
     * <p>
     * Chain: CraftPlayer → ServerPlayer (getHandle) →
     * ServerGamePacketListenerImpl (field "connection") →
     * Connection (field "connection") →
     * Channel (field "channel" or method "channel")
     */
    private Channel getNettyChannel(Player player) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Object serverPlayer = craftPlayerClass.getMethod("getHandle").invoke(player);
            Object serverGamePacketListener = serverPlayer.getClass().getField("connection").get(serverPlayer);
            Object connection = serverGamePacketListener.getClass().getField("connection").get(serverGamePacketListener);

            // Connection has a 'channel' field or 'channel()' method
            try {
                java.lang.reflect.Field channelField = connection.getClass().getField("channel");
                return (Channel) channelField.get(connection);
            } catch (NoSuchFieldException nsfe) {
                try {
                    return (Channel) connection.getClass().getMethod("channel").invoke(connection);
                } catch (NoSuchMethodException nsme) {
                    return (Channel) connection.getClass().getMethod("getChannel").invoke(connection);
                }
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[PacketGuard] Cannot get channel for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Finds a handler name in the pipeline by class name substring.
     */
    private String findHandlerName(Channel channel, String classNameSubstring) {
        for (String name : channel.pipeline().names()) {
            try {
                Object handler = channel.pipeline().get(name);
                if (handler != null && handler.getClass().getSimpleName().contains(classNameSubstring)) {
                    return name;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
