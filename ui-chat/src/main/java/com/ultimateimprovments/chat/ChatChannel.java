package com.ultimateimprovments.chat;

/**
 * Available chat channels.
 * <p>
 * Each channel has its own format in config and permission.
 */
public enum ChatChannel {

    /** Local chat — visible only within a configurable radius. */
    LOCAL("local", "ui.chat.channel.local"),

    /** Global chat — visible to all online players. */
    GLOBAL("global", "ui.chat.channel.global"),

    /** World chat — visible to all players in the same world. */
    WORLD("world", "ui.chat.channel.world"),

    /** Private chat — visible only to the target player. */
    PRIVATE("private", "ui.chat.channel.private"),

    /** Admin chat — visible only to players with ui.chat.channel.admin permission. */
    ADMIN("admin", "ui.chat.channel.admin"),

    /**
     * Check channel — for a player under an anti-cheat check: messages go ONLY to
     * the inspector (moderator). Toggled via {@code /ui chatchnl check} and only
     * allowed while the player is actually being checked.
     */
    CHECK("check", "ui.chat.channel.check");

    private final String configKey;
    private final String permission;

    ChatChannel(String configKey, String permission) {
        this.configKey = configKey;
        this.permission = permission;
    }

    /** Returns the config key (e.g. "local", "global"). */
    public String getConfigKey() { return configKey; }

    /** Returns the permission required to use this channel. */
    public String getPermission() { return permission; }

    /**
     * Finds a channel by config key (case-insensitive).
     * @return the channel or null if not found
     */
    public static ChatChannel fromConfigKey(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase();
        for (ChatChannel ch : values()) {
            if (ch.configKey.equals(lower)) return ch;
        }
        return null;
    }
}
