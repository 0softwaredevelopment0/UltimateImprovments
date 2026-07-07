package com.mcplugin.mechanics.security.anticheat.nms;

import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.PlayerData;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PacketHandler — NMS packet-перехватчик для античита.
 * <p>
 * Инжектирует {@link ChannelDuplexHandler} в Netty pipeline каждого игрока
 * и перехватывает входящие пакеты ДО их обработки Bukkit/Paper.
 * <p>
 * Перехваченные пакеты:
 * <ul>
 *   <li>{@code ServerboundMovePlayerPacket} — движение (позиция, ротация, onGround)</li>
 *   <li>{@code ServerboundInteractPacket} — атаки/взаимодействия</li>
 *   <li>{@code ServerboundSwingPacket} — взмах рукой (CPS)</li>
 *   <li>{@code ServerboundPlayerCommandPacket} — прыжки, сник</li>
 *   <li>{@code ServerboundUseItemOnPacket} — использование предмета на блоке</li>
 * </ul>
 * <p>
 * Данные из пакетов записываются в {@link PlayerData} и используются
 * проверками античита для более точной детекции.
 */
public class PacketHandler implements Listener {

    private static PacketHandler instance;

    private static final String HANDLER_NAME = "mcplugin_anticheat_packet";
    private static boolean enabled = true;
    private static boolean logInject = false;
    // Injected players
    private final Map<UUID, Boolean> injected = new ConcurrentHashMap<>();

    private PacketHandler() {}

    public static void init() {
        if (instance != null) return;
        instance = new PacketHandler();

        try {
            var cfg = Main.getInstance().getConfig().getConfigurationSection("anticheat.packet");
            enabled = cfg != null && cfg.getBoolean("enabled", true);
            logInject = cfg != null && cfg.getBoolean("log_inject", false);
        } catch (Exception e) {
            ConsoleLogger.warn("[AntiCheat-Packet] Failed to read config: " + e.getMessage());
            enabled = true;
        }

        if (!enabled) {
            ConsoleLogger.info("[AntiCheat-Packet] Packet interception disabled in config.");
            return;
        }

        // Verify NMS reflection works before proceeding
        // validateReflection() returns true OR throws — проверка не нужна
        validateReflection();

        try {
            // Register join/quit events
            Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());

            // Inject for already online players
            Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : online) {
                try {
                    instance.injectPlayer(player);
                } catch (Exception e) {
                    ConsoleLogger.warn("[AntiCheat-Packet] Failed to inject " + player.getName() + ": " + e.getMessage());
                }
            }

            ConsoleLogger.info("[AntiCheat-Packet] Initialized. Intercepting packets at Netty level.");
        } catch (Exception e) {
            ConsoleLogger.warn("[AntiCheat-Packet] Initialization failed: " + e.getMessage());
        }
    }

    /**
     * Проверяет, доступны ли NMS классы через рефлексию.
     * Если нет — бросает исключение, античит не запускается.
     */
    private static boolean validateReflection() {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> serverPacketClass = Class.forName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket");
            if (craftPlayerClass != null && serverPacketClass != null) return true;
            return false;
        } catch (Exception e) {
            throw new RuntimeException("NMS classes not found for PacketHandler: " + e.getMessage());
        }
    }

    // =========================
    // EVENTS
    // =========================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        injectPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removePlayer(e.getPlayer());
    }

    // =========================
    // INJECTION / REMOVAL
    // =========================

    private void injectPlayer(Player player) {
        if (!enabled) return;
        if (injected.putIfAbsent(player.getUniqueId(), true) != null) return;

        try {
            Channel channel = getNettyChannel(player);
            if (channel == null) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) return;

            ChannelDuplexHandler handler = new AntiCheatPacketInterceptor(player);

            // Inject AFTER PacketDecoder but BEFORE the main handler
            // This catches ALL decoded inbound packets before game logic
            String decoderName = findHandlerName(channel, "PacketDecoder");
            if (decoderName != null) {
                channel.pipeline().addAfter(decoderName, HANDLER_NAME, handler);
            } else {
                // Fallback: add before encoder
                String encoderName = findHandlerName(channel, "PacketEncoder");
                if (encoderName != null) {
                    channel.pipeline().addBefore(encoderName, HANDLER_NAME, handler);
                } else {
                    channel.pipeline().addFirst(HANDLER_NAME, handler);
                }
            }

            if (logInject) {
                ConsoleLogger.info("[AntiCheat-Packet] Injected for " + player.getName());
            }

        } catch (Exception e) {
            ConsoleLogger.warn("[AntiCheat-Packet] Failed to inject " + player.getName() + ": " + e.getMessage());
        }
    }

    private void removePlayer(Player player) {
        injected.remove(player.getUniqueId());
        try {
            Channel channel = getNettyChannel(player);
            if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {}
    }

    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removePlayer(player);
        }
        injected.clear();
    }

    // =========================
    // NETTY CHANNEL ACCESS (reflection)
    // =========================

    private Channel getNettyChannel(Player player) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Object serverPlayer = craftPlayerClass.getMethod("getHandle").invoke(player);
            Object serverGamePacketListener = serverPlayer.getClass().getField("connection").get(serverPlayer);
            Object connection = serverGamePacketListener.getClass().getField("connection").get(serverGamePacketListener);

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
            return null;
        }
    }

    private String findHandlerName(Channel channel, String classNameSubstring) {
        for (String name : channel.pipeline().names()) {
            try {
                Object handler = channel.pipeline().get(name);
                if (handler != null && handler.getClass().getSimpleName().contains(classNameSubstring)) {
                    return name;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static PacketHandler getInstance() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.removeAll();
            instance = null;
        }
        ConsoleLogger.info("[AntiCheat-Packet] Shut down.");
    }
}
