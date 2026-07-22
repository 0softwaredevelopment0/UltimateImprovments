package com.ultimateimprovements.mechanics.security.anticheat.nms;

import com.ultimateimprovements.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovements.mechanics.security.anticheat.core.PlayerData;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * AntiCheatPacketInterceptor — кастомный ChannelDuplexHandler.
 * <p>
 * Перехватывает входящие пакеты от клиента ДО обработки их игровой логикой.
 * Извлекает данные движения, атак и взаимодействий, записывая их в PlayerData.
 * <p>
 * Работает через рефлексию — не требует compile-time зависимостей от NMS.
 * Поддерживает Paper 1.21.4 (Mojang mappings).
 */
public class AntiCheatPacketInterceptor extends ChannelDuplexHandler {

    private final UUID playerUuid;
    private Player cachedPlayer;

    // Packet class names (Mojang mappings — Paper 1.21.4)
    private static final String PKG_GAME = "net.minecraft.network.protocol.game";
    private static final String MOVE_PACKET = PKG_GAME + ".ServerboundMovePlayerPacket";
    private static final String INTERACT_PACKET = PKG_GAME + ".ServerboundInteractPacket";
    private static final String SWING_PACKET = PKG_GAME + ".ServerboundSwingPacket";
    private static final String COMMAND_PACKET = PKG_GAME + ".ServerboundPlayerCommandPacket";
    private static final String USE_ITEM_ON_PACKET = PKG_GAME + ".ServerboundUseItemOnPacket";

    // Cached packet class objects
    private static Class<?> clazzMovePlayer;
    private static Class<?> clazzMovePos;
    private static Class<?> clazzMovePosRot;
    private static Class<?> clazzMoveRot;
    private static Class<?> clazzInteract;
    private static Class<?> clazzSwing;
    private static Class<?> clazzCommand;
    private static Class<?> clazzUseItemOn;

    // Reflection methods — cached for performance
    private static Method moveGetX, moveGetY, moveGetZ, moveGetYaw, moveGetPitch, moveHasPos, moveHasRot;
    private static Method interactAction;

    static {
        initReflection();
    }

    private static void initReflection() {
        try {
            clazzMovePlayer = Class.forName(MOVE_PACKET);
            clazzMovePos = Class.forName(MOVE_PACKET + "$Pos");
            clazzMovePosRot = Class.forName(MOVE_PACKET + "$PosRot");
            clazzMoveRot = Class.forName(MOVE_PACKET + "$Rot");
            clazzInteract = Class.forName(INTERACT_PACKET);
            clazzSwing = Class.forName(SWING_PACKET);
            clazzCommand = Class.forName(COMMAND_PACKET);
            clazzUseItemOn = Class.forName(USE_ITEM_ON_PACKET);

            // ServerboundMovePlayerPacket methods
            moveGetX = clazzMovePlayer.getMethod("getX");
            moveGetY = clazzMovePlayer.getMethod("getY");
            moveGetZ = clazzMovePlayer.getMethod("getZ");
            moveGetYaw = clazzMovePlayer.getMethod("getYaw");
            moveGetPitch = clazzMovePlayer.getMethod("getPitch");
            moveHasPos = clazzMovePlayer.getMethod("hasPosition");
            moveHasRot = clazzMovePlayer.getMethod("hasRotation");

            // ServerboundInteractPacket — getAction() returns enum (ATTACK, INTERACT, INTERACT_AT)
            interactAction = clazzInteract.getMethod("getAction");

        } catch (Exception e) {
            // If reflection fails, packet interception will gracefully skip unknown packets
        }
    }

    public AntiCheatPacketInterceptor(Player player) {
        this.playerUuid = player.getUniqueId();
        this.cachedPlayer = player;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Process packet BEFORE the server sees it — это и есть "перехват СРАЗУ"
        if (msg != null) {
            processPacket(msg);
        }
        // Always pass through — мы только ЧИТАЕМ пакеты, никогда не блокируем
        super.channelRead(ctx, msg);
    }

    // =========================
    // PACKET PROCESSING
    // =========================

    private void processPacket(Object packet) {
        // This runs on the Netty IO thread. PlayerData is thread-safe
        // (uses ConcurrentLinkedDeque, AtomicInteger, volatile, ConcurrentHashMap).
        Player player = getPlayer();
        if (player == null || !player.isOnline()) return;

        AntiCheatManager acm = AntiCheatManager.getInstance();
        if (acm == null) return;
        PlayerData data = acm.getOrCreatePlayerData(player);

        // Check all packet types
        if (isMovePacket(packet)) {
            handleMovePacket(packet, player, data);
        } else if (isSwingPacket(packet)) {
            handleSwingPacket(data);
        } else if (isInteractAttackPacket(packet)) {
            handleInteractAttackPacket(player, data);
        } else if (isCommandPacket(packet)) {
            handleCommandPacket(packet, player, data);
        }
    }

    // =========================
    // MOVEMENT PACKET
    // =========================

    private boolean isMovePacket(Object packet) {
        return clazzMovePlayer != null && clazzMovePlayer.isInstance(packet);
    }

    private void handleMovePacket(Object packet, Player player, PlayerData data) {
        try {
            // Position — always use doubles, convert from packet's internal type
            double x = 0, y = 0, z = 0;
            boolean hasPos = false;

            if (clazzMovePos.isInstance(packet) || clazzMovePosRot.isInstance(packet)) {
                x = (double) moveGetX.invoke(packet);
                y = (double) moveGetY.invoke(packet);
                z = (double) moveGetZ.invoke(packet);
                hasPos = true;
            }

            // Rotation
            float yaw = 0, pitch = 0;
            boolean hasRot = false;

            if (clazzMovePosRot.isInstance(packet) || clazzMoveRot.isInstance(packet)) {
                yaw = (float) moveGetYaw.invoke(packet);
                pitch = (float) moveGetPitch.invoke(packet);
                hasRot = true;
            }

            // onGround — directly from the packet (client-reported)
            boolean onGround;
            try {
                Method getOnGround = packet.getClass().getMethod("isOnGround");
                onGround = (boolean) getOnGround.invoke(packet);
            } catch (Exception e) {
                onGround = player.isOnGround();
            }

            if (hasPos) {
                Location loc = new Location(player.getWorld(), x, y, z, yaw, pitch);
                data.updatePosition(loc, onGround);
            }

            if (hasRot) {
                data.updateRotation(yaw, pitch);
            }

        } catch (Exception e) {
            // Silently skip malformed packets
        }
    }

    // =========================
    // SWING PACKET (CPS)
    // =========================

    private boolean isSwingPacket(Object packet) {
        return clazzSwing != null && clazzSwing.isInstance(packet);
    }

    private void handleSwingPacket(PlayerData data) {
        data.registerClick();
    }

    // =========================
    // INTERACT PACKET (ATTACK)
    // =========================

    private boolean isInteractAttackPacket(Object packet) {
        if (clazzInteract == null || !clazzInteract.isInstance(packet)) return false;
        try {
            Object action = interactAction.invoke(packet);
            return action != null && action.toString().equals("ATTACK");
        } catch (Exception e) {
            return false;
        }
    }

    private void handleInteractAttackPacket(Player player, PlayerData data) {
        data.registerAttack(0);
    }

    // =========================
    // COMMAND PACKET (Press Shift/Space)
    // =========================

    private boolean isCommandPacket(Object packet) {
        return clazzCommand != null && clazzCommand.isInstance(packet);
    }

    private void handleCommandPacket(Object packet, Player player, PlayerData data) {
        try {
            Method getAction = packet.getClass().getMethod("getAction");
            Object action = getAction.invoke(packet);
            String actionName = action != null ? action.toString() : "";

            if (actionName.equals("PRESS_SHIFT_KEY")) {
                // Player pressed sneak — used for entity detection
            } else if (actionName.equals("RELEASE_SHIFT_KEY")) {
                // Player released sneak
            } else if (actionName.equals("START_JUMPING")) {
                // Player started jumping — used for AllJumps check
            }

        } catch (Exception ignored) {}
    }

    // =========================
    // UTILITY
    // =========================

    private Player getPlayer() {
        if (cachedPlayer != null && cachedPlayer.isOnline()) return cachedPlayer;
        cachedPlayer = Bukkit.getPlayer(playerUuid);
        return cachedPlayer;
    }
}
