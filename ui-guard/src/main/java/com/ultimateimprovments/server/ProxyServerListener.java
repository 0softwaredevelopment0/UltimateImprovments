package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🛡 ProxyServerListener — checks the ui.proxy.server permission at the packet level.
 * <p>
 * Intercepts incoming {@code PacketPlayInCustomPayload} on the "BungeeCord" channel
 * (or other proxy channels) and checks whether the player has the ui.proxy.server permission.
 * <p>
 * Why: Velocity/BungeeCord cannot work properly with backend-server permissions.
 * This interceptor blocks proxy packets (Connect, ConnectOther, etc.) at the
 * Netty pipeline level — before they are processed by the server.
 * <p>
 * A packet from the proxy looks like a regular incoming packet from the client.
 * We intercept it in channelRead(), check the permission,
 * and if it is missing — consume the packet (don't pass it further) and send the player a message.
 */
public class ProxyServerListener implements Listener {

    private static ProxyServerListener instance;

    /** Configuration */
    private static boolean enabled = true;
    private static boolean logCommands = true;
    private static String noPermissionMessage = "<red>❌ You don't have permission to switch servers!</red>";

    /** State */
    private static final String HANDLER_NAME = ":ui_proxy_server";
    private static final String PERMISSION = "ui.proxy.server";
    private final Map<UUID, Boolean> injected = new ConcurrentHashMap<>();

    // =========================
    // INIT / RELOAD
    // =========================
    public static void init(Main plugin) {
        instance = new ProxyServerListener();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        // Inject for already online players (e.g., after /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            instance.injectPlayer(player);
        }

        ConsoleLogger.info("[ProxyServer] Initialized. enabled=" + enabled);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("proxy_server");
        if (cfg == null) return;

        enabled = cfg.getBoolean("enabled", true);
        logCommands = cfg.getBoolean("log_commands", true);
        noPermissionMessage = cfg.getString("no_permission_message",
                "<red>❌ You don't have permission to switch servers!</red>");
    }

    public static ProxyServerListener getInstance() {
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
    // NETTY PIPELINE INJECTION (incoming packets)
    // =========================
    private void injectPlayer(Player player) {
        if (!enabled) return;
        if (injected.putIfAbsent(player.getUniqueId(), true) != null) return;

        try {
            Channel channel = getNettyChannel(player);
            if (channel == null) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;

            // Insert the handler BEFORE packet_handler to intercept incoming packets
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    // Check whether the packet is a custom payload from the proxy
                    if (enabled && isCustomPayloadPacket(msg)) {
                        String channelName = getPacketChannel(msg);
                        if (channelName != null && isProxyChannel(channelName)) {
                            if (!player.hasPermission(PERMISSION)) {
                                // Parse the BungeeCord subchannel ONLY when blocking the packet,
                                // so the buffer is not corrupted for downstream handlers
                                String subchannel = parseBungeeSubchannel(msg);

                                if (logCommands) {
                                    ConsoleLogger.info("[ProxyServer] Blocked " + channelName
                                            + (subchannel != null ? "/" + subchannel : "")
                                            + " for " + player.getName()
                                            + " (lacks " + PERMISSION + ")");
                                }

                                // Send the message on the main thread (like in PacketGuard)
                                final Player p = player;
                                final String msgText = noPermissionMessage;
                                Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                                    p.sendMessage(MessageUtil.parse(msgText))
                                );

                                return; // Consume the packet — don't pass it further
                            }
                        }
                    }

                    // Pass the packet further down the pipeline
                    super.channelRead(ctx, msg);
                }
            });

            ConsoleLogger.info("[ProxyServer] Injected handler for " + player.getName());

        } catch (Exception e) {
            Main.getInstance().getLogger().fine("[ProxyServer] Failed to inject " + player.getName() + ": " + e.getMessage());
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
    // PACKET DETECTION & PARSING
    // =========================

    /**
     * Checks whether the object is a custom payload packet (PacketPlayInCustomPayload).
     * Uses the class name — universal for different mappings (Mojang/Spigot).
     */
    private static boolean isCustomPayloadPacket(Object msg) {
        String className = msg.getClass().getName();
        return className.contains("PacketPlayInCustomPayload")
                || className.contains("ServerboundCustomPayload");
    }

    /**
     * Extracts the channel name from the packet.
     * Tries different variants via reflection.
     */
    private static String getPacketChannel(Object msg) {
        try {
            Class<?> clazz = msg.getClass();

            // Try the standard getters
            for (Method method : clazz.getMethods()) {
                String name = method.getName();
                if (method.getParameterCount() != 0) continue;
                if (method.getReturnType() == Void.TYPE) continue;

                if (name.equals("getName") || name.equals("getIdentifier")
                        || name.equals("b") || name.equals("getType") || name.equals("a")) {
                    Object result = method.invoke(msg);
                    if (result != null) {
                        String str = result.toString();
                        // ResourceLocation → "minecraft:brand", "BungeeCord", "velocity:player_info"
                        if (str.contains(":") || str.equalsIgnoreCase("BungeeCord") || str.equalsIgnoreCase("MC|BungeeCord")) {
                            return str;
                        }
                    }
                }
            }

            // Try fields directly
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().contains("ResourceLocation")
                        || field.getType().getName().contains("MinecraftKey")
                        || field.getType() == String.class) {
                    field.setAccessible(true);
                    Object val = field.get(msg);
                    if (val != null) {
                        String str = val.toString();
                        if (str.contains(":") || str.contains("BungeeCord") || str.contains("velocity")) {
                            return str;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Checks whether the channel is a proxy channel (BungeeCord, Velocity, etc.).
     */
    private static boolean isProxyChannel(String channel) {
        String lower = channel.toLowerCase();
        return lower.equals("bungeecord")
                || lower.equals("mc|bungeecord")
                || lower.equals("legacy:bungeecord")
                || lower.contains("velocity")
                || lower.contains("proxy")
                || lower.equals("bungeecord:main");
    }

    /**
     * Tries to extract the BungeeCord subchannel (Connect, ConnectOther, ServerIP, etc.)
     * from the packet payload for logging. If it fails — returns null.
     */
    private static String parseBungeeSubchannel(Object msg) {
        try {
            // Look for a method returning ByteBuf/FriendlyByteBuf/PacketDataSerializer
            Class<?> clazz = msg.getClass();
            for (Method method : clazz.getMethods()) {
                if (method.getParameterCount() != 0) continue;
                Class<?> retType = method.getReturnType();
                String retName = retType.getName();

                if (retName.contains("ByteBuf") || retName.contains("FriendlyByteBuf")
                        || retName.contains("PacketDataSerializer")
                        || retType == byte[].class) {

                    method.setAccessible(true);
                    Object data = method.invoke(msg);
                    if (data == null) continue;

            // Try to read the first UTF string from the buffer
            String sub = readUtfString(data);
                    if (sub != null && (sub.startsWith("Connect") || sub.startsWith("Server")
                            || sub.startsWith("IP") || sub.startsWith("PlayerCount")
                            || sub.startsWith("GetServer") || sub.startsWith("Forward"))) {
                        return sub;
                    }
                }
            }

            // Fallback: check the fields
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.getType() == byte[].class || field.getType().getName().contains("ByteBuf")
                        || field.getType().getName().contains("PacketData")) {
                    field.setAccessible(true);
                    Object data = field.get(msg);
                    if (data == null) continue;

                    String sub = readUtfString(data);
                    if (sub != null) return sub;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Tries to read a UTF string from a ByteBuf, FriendlyByteBuf or byte[].
     */
    private static String readUtfString(Object data) {
        try {
            if (data instanceof byte[] bytes) {
                // First bytes — a UTF-8 string
                // In BungeeCord: subchannel UTF-8 string (length-prefixed)
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
                java.io.DataInputStream dis = new java.io.DataInputStream(bais);
                return dis.readUTF();
            }

            // FriendlyByteBuf/PacketDataSerializer has readUtf() or readUTF() methods
            for (Method m : data.getClass().getMethods()) {
                String name = m.getName();
                if ((name.equals("readUtf") || name.equals("readUTF"))
                        && m.getParameterCount() <= 1) {
                    m.setAccessible(true);
                    if (m.getParameterCount() == 0) {
                        return (String) m.invoke(data);
                    } else {
                        // readUtf(int maxLength)
                        return (String) m.invoke(data, Short.MAX_VALUE);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    // =========================
    // NETTY CHANNEL (reflection)
    // =========================

    /**
     * Gets the player's Netty channel via reflection.
     * A copy of the method from PacketGuard for consistency.
     */
    private static Channel getNettyChannel(Player player) {
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
            Main.getInstance().getLogger().fine("[ProxyServer] Cannot get channel for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
