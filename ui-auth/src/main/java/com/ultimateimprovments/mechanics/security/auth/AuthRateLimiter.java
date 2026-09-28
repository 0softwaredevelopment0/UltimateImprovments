package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting for authentication DB requests.
 * Protection against spamming the password confirm button.
 */
public class AuthRateLimiter {

    private static AuthRateLimiter instance;
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        instance = this;
    }

    public static AuthRateLimiter getInstance() {
        return instance;
    }

    /**
     * Checks whether the player exceeded the request limit.
     * If exceeded — sends a message and returns false.
     *
     * @param player the player
     * @return true if the request is allowed, false if it should wait
     */
    public boolean checkCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(uuid);
        long cooldownMs = AuthConfig.getRequestCooldownMs();

        if (lastRequest != null && (now - lastRequest) < cooldownMs) {
            long remaining = ((cooldownMs - (now - lastRequest)) / 1000) + 1;
            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("auth.messages.rate_limit", "<red>❌ Please wait </red><yellow>%seconds%</yellow> <red>seconds before the next request!</red>")
                            .replace("%seconds%", String.valueOf(remaining))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return false;
        }

        requestCooldowns.put(uuid, now);
        return true;
    }

    /**
     * Clears the cooldown for a player (on successful login/logout).
     */
    public void removePlayer(UUID uuid) {
        requestCooldowns.remove(uuid);
    }
}
