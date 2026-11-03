package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting for password attempts.
 * <p>
 * A wrong password locks BOTH the account (UUID) and the source IP for
 * {@code auth.request_cooldown_seconds} (default 30 s). Successful attempts are
 * never delayed — the lock is applied only after an error, so a player who types
 * the right password first time in is not punished by the limiter.
 * <p>
 * State is in-memory and is deliberately NOT cleared on quit: dropping the lock
 * in {@code PlayerQuitEvent} would let a brute-forcer reconnect after every guess.
 * Expired IP entries are pruned so a botnet cannot grow the map without bound.
 */
public class AuthRateLimiter {

    private static final int MAX_IP_ENTRIES = 4096;

    private static AuthRateLimiter instance;

    private final Map<UUID, Long> accountLockedUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> ipLockedUntil = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        instance = this;
    }

    public static AuthRateLimiter getInstance() {
        return instance;
    }

    /**
     * Checks whether the account or its IP is currently locked.
     * When locked, sends the "wait N seconds" message and returns false.
     *
     * @param player the player attempting to submit a password
     * @return true when an attempt is allowed right now
     */
    public boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        long until = Math.max(
                accountLockedUntil.getOrDefault(player.getUniqueId(), 0L),
                ipLockedUntil.getOrDefault(ipOf(player), 0L));

        if (until <= now) return true;

        long remaining = ((until - now) / 1000) + 1;
        player.sendMessage(MessageUtil.parse(
                MessagesManager.getString("auth.messages.rate_limit",
                        "<red>❌ Please wait </red><yellow>%seconds%</yellow> <red>seconds before the next request!</red>")
                        .replace("%seconds%", String.valueOf(remaining))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        return false;
    }

    /**
     * Locks the account and the player's IP after a wrong password.
     * The IP lock also covers other accounts sharing that address.
     */
    public void applyPenalty(Player player) {
        long until = System.currentTimeMillis() + AuthConfig.getRequestCooldownMs();
        accountLockedUntil.put(player.getUniqueId(), until);

        String ip = ipOf(player);
        if (!ip.isEmpty()) {
            if (ipLockedUntil.size() >= MAX_IP_ENTRIES) {
                pruneExpired(until);
            }
            ipLockedUntil.put(ip, until);
        }
    }

    /**
     * Clears the account lock. Not called on quit — the lock must survive a
     * reconnect, otherwise the 30-second limit is trivially bypassed.
     */
    public void removePlayer(UUID uuid) {
        accountLockedUntil.remove(uuid);
    }

    private void pruneExpired(long now) {
        ipLockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static String ipOf(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) return "";
        return address.getAddress().getHostAddress();
    }
}
