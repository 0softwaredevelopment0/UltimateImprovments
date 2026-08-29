package com.ultimateimprovments.chat;

import com.ultimateimprovments.database.StateStore;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the active chat channel for each player, persisted to the DB so the
 * choice survives a server restart.
 * <p>
 * Default channel is {@link ChatChannel#GLOBAL}.
 */
public final class PlayerChannelManager {

    private static final String NS = "chat";

    private PlayerChannelManager() {}

    private static final Map<UUID, ChatChannel> activeChannels = new ConcurrentHashMap<>();
    private static final Map<UUID, String> privateTargets = new ConcurrentHashMap<>();

    private static String key(UUID uuid, String field) {
        return uuid.toString() + "|" + field;
    }

    /**
     * Returns the player's active channel (default: GLOBAL).
     */
    public static ChatChannel getChannel(Player player) {
        return activeChannels.getOrDefault(player.getUniqueId(), ChatChannel.GLOBAL);
    }

    /**
     * Sets the player's active channel (persisted to the DB).
     */
    public static void setChannel(Player player, ChatChannel channel) {
        activeChannels.put(player.getUniqueId(), channel);
        StateStore.put(NS, key(player.getUniqueId(), "channel"), channel.getConfigKey());
    }

    /**
     * Returns the private chat target name for a player (used when channel = PRIVATE).
     */
    public static String getPrivateTarget(Player player) {
        return privateTargets.get(player.getUniqueId());
    }

    /**
     * Sets the private chat target for a player (persisted to the DB).
     */
    public static void setPrivateTarget(Player player, String targetName) {
        if (targetName == null) {
            privateTargets.remove(player.getUniqueId());
            StateStore.remove(NS, key(player.getUniqueId(), "private"));
        } else {
            privateTargets.put(player.getUniqueId(), targetName);
            StateStore.put(NS, key(player.getUniqueId(), "private"), targetName);
        }
    }

    /**
     * Loads a player's channel + private target from the DB (called on join).
     */
    public static void loadFromDatabase(Player player) {
        UUID uuid = player.getUniqueId();
        String ch = StateStore.get(NS, key(uuid, "channel"));
        if (ch != null) {
            ChatChannel parsed = ChatChannel.fromConfigKey(ch);
            if (parsed != null) activeChannels.put(uuid, parsed);
        }
        String t = StateStore.get(NS, key(uuid, "private"));
        if (t != null) privateTargets.put(uuid, t);
    }

    /**
     * Removes the player's in-memory channel data on disconnect.
     * DB rows are kept so the choice survives a restart.
     */
    public static void remove(Player player) {
        activeChannels.remove(player.getUniqueId());
        privateTargets.remove(player.getUniqueId());
    }
}