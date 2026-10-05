package com.ultimateimprovments.command;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Freezes the target's client for the {@code /crash} troll command.
 * <p>
 * Sends a single bogus position (teleport) packet with NaN coordinates DIRECTLY
 * to the target's connection — the server-side position is never touched, so the
 * server stays fully safe and unaware. The client applies the NaN to its local
 * player: position/camera math breaks and the game hard-freezes — exactly like
 * during a real server crash. No floods, no memory pressure: one packet, and a
 * frozen client also stops sending any packets (movement, chat, interactions),
 * which is precisely how a dead server looks from the cheater's side.
 */
final class TrollFreeze {

    private TrollFreeze() {
        // Utility class — no instances
    }

    /**
     * Sends the NaN-poisoned teleport packet to the target's client only.
     * Safe: if the player left or the connection is closed — silently skip.
     */
    static void sendNaNPosition(Player target) {
        if (target == null || !target.isOnline()) return;
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) target).getHandle();
            if (serverPlayer.connection == null) return;
            serverPlayer.connection.send(new ClientboundPlayerPositionPacket(
                    0, // bogus teleport id; the confirm echo mismatches and is ignored
                    new PositionMoveRotation(
                            new Vec3(Double.NaN, Double.NaN, Double.NaN), // poisoned position
                            Vec3.ZERO,
                            Float.NaN, // poisoned yaw — camera breaks too
                            Float.NaN  // poisoned pitch
                    ),
                    Set.of() // absolute teleport — no relative axes
            ));
        } catch (Exception ignored) {
            // The player left / the connection broke mid-send — not critical.
        }
    }
}
