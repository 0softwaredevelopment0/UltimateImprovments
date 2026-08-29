package com.ultimateimprovments.core.api;

import org.bukkit.entity.Player;

/**
 * API bridge between UI-Core/UI-Chat and the anti-cheat check system in UI-Other.
 * <p>
 * The chat module (UI-Chat) depends only on UI-Core, but the check channel needs to
 * know the inspector of a checked player to route their messages only to the
 * moderator. UI-Other's {@code CheckManager} registers the implementation, and
 * {@code ChatManager} reads through this bridge — no reverse dependency needed.
 */
public interface CheckBridge {

    /**
     * Returns the inspector who is currently checking the given player,
     * or {@code null} if the player is not under an active check.
     */
    Player getInspector(Player suspect);

    /** Returns {@code true} if the player is currently being checked. */
    boolean isBeingChecked(Player player);

    /** Registers (or replaces) the bridge implementation. */
    static void register(CheckBridge bridge) {
        Holder.bridge = bridge;
    }

    /** Returns the registered bridge, or {@code null} if none is registered. */
    static CheckBridge get() {
        return Holder.bridge;
    }

    final class Holder {
        private static volatile CheckBridge bridge;
    }
}