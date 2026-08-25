package com.ultimateimprovments.chat;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the active chat channel for each player (in-memory only — resets on restart).
 * <p>
 * Default channel is {@link ChatChannel#GLOBAL}.
 */
public final class PlayerChannelManager {

    private PlayerChannelManager() {}

    private static final Map<UUID, ChatChannel> activeChannels = new ConcurrentHashMap<>();
    private static final Map<UUID, String> privateTargets = new ConcurrentHashMap<>();

    /**
     * Returns the player's active channel (default: GLOBAL).
     */
    public static ChatChannel getChannel(Player player) {
        return activeChannels.getOrDefault(player.getUniqueId(), ChatChannel.GLOBAL);
    }

    /**
     * Sets the player's active channel.
     */
    public static void setChannel(Player player, ChatChannel channel) {
        activeChannels.put(player.getUniqueId(), channel);
    }

    /**
     * Returns the private chat target name for a player (used when channel = PRIVATE).
     * @return target name or null
     */
    public static String getPrivateTarget(Player player) {
        return privateTargets.get(player.getUniqueId());
    }

    /**
     * Sets the private chat target for a player.
     */
    public static void setPrivateTarget(Player player, String targetName) {
        privateTargets.put(player.getUniqueId(), targetName);
    }

    /**
     * Removes all data for a player (called on disconnect).
     */
    public static void remove(Player player) {
        activeChannels.remove(player.getUniqueId());
        privateTargets.remove(player.getUniqueId());
    }
}
