package com.ultimateimprovements.server;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.MessageUtil;
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
 * 🛡 ProxyServerListener — проверка права mcplugin.proxy.server на уровне пакетов.
 * <p>
 * Перехватывает входящие {@code PacketPlayInCustomPayload} на канале "BungeeCord"
 * (или других прокси-каналах) и проверяет, есть ли у игрока право mcplugin.proxy.server.
 * <p>
 * Зачем: Velocity/BungeeCord не могут нормально работать с правами на backend-сервере.
 * Этот перехватчик блокирует пакеты прокси (Connect, ConnectOther и т.д.) на уровне
 * Netty pipeline — до того, как они будут обработаны сервером.
 * <p>
 * Пакет от прокси выглядит как обычный входящий пакет от клиента.
 * Мы перехватываем его в channelRead(), проверяем permission,
 * и если его нет — потребляем пакет (не передаём дальше) и отправляем игроку сообщение.
 */
public class ProxyServerListener implements Listener {

    private static ProxyServerListener instance;

    /** Конфигурация */
    private static boolean enabled = true;
    private static boolean logCommands = true;
    private static String noPermissionMessage = "<red>❌ You don't have permission to switch servers!</red>";

    /** Состояние */
    private static final String HANDLER_NAME = "mcplugin_proxy_server";
    private static final String PERMISSION = "mcplugin.proxy.server";
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
    // NETTY PIPELINE INJECTION (входящие пакеты)
    // =========================
    private void injectPlayer(Player player) {
        if (!enabled) return;
        if (injected.putIfAbsent(player.getUniqueId(), true) != null) return;

        try {
            Channel channel = getNettyChannel(player);
            if (channel == null) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;

            // Вставляем handler ДО packet_handler, чтобы перехватывать входящие пакеты
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    // Проверяем, является ли пакет кастомным payload'ом от прокси
                    if (enabled && isCustomPayloadPacket(msg)) {
                        String channelName = getPacketChannel(msg);
                        if (channelName != null && isProxyChannel(channelName)) {
                            if (!player.hasPermission(PERMISSION)) {
                                // Парсим BungeeCord subchannel ТОЛЬКО когда блокируем пакет,
                                // чтобы не коррумпировать буфер для downstream-обработчиков
                                String subchannel = parseBungeeSubchannel(msg);

                                if (logCommands) {
                                    ConsoleLogger.info("[ProxyServer] Blocked " + channelName
                                            + (subchannel != null ? "/" + subchannel : "")
                                            + " for " + player.getName()
                                            + " (lacks " + PERMISSION + ")");
                                }

                                // Отправляем сообщение на главном потоке (как в PacketGuard)
                                final Player p = player;
                                final String msgText = noPermissionMessage;
                                Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                                    p.sendMessage(MessageUtil.parse(msgText))
                                );

                                return; // Потребляем пакет — не передаём дальше
                            }
                        }
                    }

                    // Пропускаем пакет дальше по pipeline
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
     * Проверяет, является ли объект пакетом кастомного payload (PacketPlayInCustomPayload).
     * Используем имя класса — универсально для разных маппингов (Mojang/Spigot).
     */
    private static boolean isCustomPayloadPacket(Object msg) {
        String className = msg.getClass().getName();
        return className.contains("PacketPlayInCustomPayload")
                || className.contains("ServerboundCustomPayload");
    }

    /**
     * Извлекает название канала из пакета.
     * Пробует разные варианты через рефлексию.
     */
    private static String getPacketChannel(Object msg) {
        try {
            Class<?> clazz = msg.getClass();

            // Пробуем стандартные геттеры
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

            // Пробуем поля напрямую
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
     * Проверяет, является ли канал прокси-каналом (BungeeCord, Velocity и т.д.).
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
     * Пытается извлечь BungeeCord subchannel (Connect, ConnectOther, ServerIP и т.д.)
     * из payload пакета для логирования. Если не удалось — возвращает null.
     */
    private static String parseBungeeSubchannel(Object msg) {
        try {
            // Ищем метод, возвращающий ByteBuf/FriendlyByteBuf/PacketDataSerializer
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

                    // Пытаемся прочитать первую UTF-строку из буфера
                    String sub = readUtfString(data);
                    if (sub != null && (sub.startsWith("Connect") || sub.startsWith("Server")
                            || sub.startsWith("IP") || sub.startsWith("PlayerCount")
                            || sub.startsWith("GetServer") || sub.startsWith("Forward"))) {
                        return sub;
                    }
                }
            }

            // Fallback: проверяем поля
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
     * Пытается прочитать UTF-строку из ByteBuf, FriendlyByteBuf или byte[].
     */
    private static String readUtfString(Object data) {
        try {
            if (data instanceof byte[] bytes) {
                // Первые байты — UTF-8 строка
                // В BungeeCord: subchannel UTF-8 string (length-prefixed)
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
    // NETTY CHANNEL (рефлексия)
    // =========================

    /**
     * Получает Netty channel игрока через рефлексию.
     * Копия метода из PacketGuard для консистентности.
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
