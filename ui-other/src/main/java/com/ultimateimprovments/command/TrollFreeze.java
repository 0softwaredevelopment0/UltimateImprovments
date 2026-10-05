package com.ultimateimprovments.command;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

/**
 * Freezes the target's client for the {@code /crash} troll command by cutting the
 * server → client packet flow.
 * <p>
 * {@link #startBlackout(Player)} inserts a discarding outbound handler at the head
 * of the target's netty pipeline: EVERY packet the server tries to send to that
 * client (movement of other players, time, block changes, keepalives — everything)
 * is silently dropped. The client itself stays untouched — no poisoned packets, no
 * camera tricks — yet the world around the player simply stops updating, which is
 * indistinguishable from a real server crash. The client keeps its connection open
 * and keeps sending packets, so nothing times out on our side either.
 * <p>
 * {@link #stopBlackout(Player)} removes the handler — it MUST be called right
 * before the kick so the disconnect packet actually reaches the client.
 * The server-side player state is never modified.
 */
final class TrollFreeze {

    /** Unique pipeline handler name (idempotent installs / removals). */
    private static final String HANDLER_NAME = "ui_crash_blackout";

    /** {@code Connection#channel} — private in NMS, resolved once (Mojang mappings). */
    private static final Field CHANNEL_FIELD;
    static {
        try {
            CHANNEL_FIELD = Connection.class.getDeclaredField("channel");
            CHANNEL_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TrollFreeze() {
        // Utility class — no instances
    }

    /**
     * Starts the blackout: all server → client packets for the target are dropped.
     * Idempotent: calling twice keeps a single handler.
     */
    static void startBlackout(Player target) {
        Channel channel = nmsChannel(target);
        if (channel == null) return;
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) return;
            channel.pipeline().addFirst(HANDLER_NAME, new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    ReferenceCountUtil.release(msg);
                    promise.trySuccess();
                }
            });
        });
    }

    /**
     * Lifts the blackout so real packets (the disconnect) reach the client again.
     * Safe to call even if no blackout was installed or the player already left.
     */
    static void stopBlackout(Player target) {
        Channel channel = nmsChannel(target);
        if (channel == null) return;
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        });
    }

    /** The target's netty channel (via NMS {@code Connection#channel}), or null if offline/broken. */
    private static Channel nmsChannel(Player target) {
        if (target == null || !target.isOnline()) return null;
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) target).getHandle();
            if (serverPlayer.connection == null || serverPlayer.connection.connection == null) return null;
            return (Channel) CHANNEL_FIELD.get(serverPlayer.connection.connection);
        } catch (Exception ignored) {
            // The player left / the connection broke — not critical.
            return null;
        }
    }
}
