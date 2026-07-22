package com.ultimateimprovements.mechanics.security.auth;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отслеживание состояния авторизации игроков.
 * <p>
 * Хранит:
 * - Кто уже авторизован (authenticated)
 * - Кто ожидает авторизации (pendingAuth)
 * - Счётчик неверных попыток ввода пароля (wrongAttempts)
 */
public class AuthPlayerState {

    private static AuthPlayerState instance;

    private final Set<UUID> authenticated = new HashSet<>();
    private final Set<UUID> pendingAuth = new HashSet<>();
    private final Map<UUID, Integer> wrongAttempts = new ConcurrentHashMap<>();

    public AuthPlayerState() {
        instance = this;
    }

    public static AuthPlayerState getInstance() {
        return instance;
    }

    // =========================
    // AUTHENTICATED
    // =========================
    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public void setAuthenticated(UUID uuid) {
        authenticated.add(uuid);
        pendingAuth.remove(uuid);
    }

    public void removeAuthenticated(UUID uuid) {
        authenticated.remove(uuid);
    }

    // =========================
    // PENDING AUTH
    // =========================
    public boolean isPendingAuth(UUID uuid) {
        return pendingAuth.contains(uuid);
    }

    public void setPendingAuth(UUID uuid) {
        pendingAuth.add(uuid);
    }

    public void removePendingAuth(UUID uuid) {
        pendingAuth.remove(uuid);
    }

    // =========================
    // WRONG ATTEMPTS
    // =========================
    public int getWrongAttempts(UUID uuid) {
        return wrongAttempts.getOrDefault(uuid, 0);
    }

    public int incrementWrongAttempts(UUID uuid) {
        int attempts = wrongAttempts.getOrDefault(uuid, 0) + 1;
        wrongAttempts.put(uuid, attempts);
        return attempts;
    }

    public void resetWrongAttempts(UUID uuid) {
        wrongAttempts.remove(uuid);
    }

    // =========================
    // FULL CLEANUP
    // =========================
    public void removePlayer(UUID uuid) {
        authenticated.remove(uuid);
        pendingAuth.remove(uuid);
        wrongAttempts.remove(uuid);
    }

    // =========================
    // PLAYER FROZEN?
    // =========================
    public boolean needsAuth(Player player) {
        if (!AuthConfig.isEnabled()) return false;
        return !authenticated.contains(player.getUniqueId());
    }
}
