package com.ultimateimprovments.mechanics.security.anticheat.nms;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.PlayerData;
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
 * PacketHandler — NMS packet interceptor for the anti-cheat.
 * <p>
 * Injects a {@link ChannelDuplexHandler} into each player's Netty pipeline
 * and intercepts inbound packets BEFORE they are processed by Bukkit/Paper.
 * <p>
 * Intercepted packets:
 * <ul>
 *   <li>{@code ServerboundMovePlayerPacket} — movement (position, rotation, onGround)</li>
 *   <li>{@code ServerboundInteractPacket} — attacks/interactions</li>
 *   <li>{@code ServerboundSwingPacket} — arm swing (CPS)</li>
 *   <li>{@code ServerboundPlayerCommandPacket} — jumps, sneak</li>
 *   <li>{@code ServerboundUseItemOnPacket} — using an item on a block</li>
 * </ul>
 * <p>
 * Packet data is written to {@link PlayerData} and used by the anti-cheat
 * checks for more accurate detection.
 */
public class PacketHandler implements Listener {

    private static PacketHandler instance;

    private static final String HANDLER_NAME = ":ui_anticheat_packet";
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
        // validateReflection() returns true OR throws — no extra check needed
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
     * Checks whether the NMS classes are available via reflection.
     * If not — throws an exception and the anti-cheat does not start.
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
