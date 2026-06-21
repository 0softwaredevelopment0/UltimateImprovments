package com.mcplugin.auth;

import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Фасад для системы авторизации.
 * <p>
 * Все вызовы делегируются специализированным классам:
 * <ul>
 *   <li>{@link AuthPlayerState} — отслеживание состояния игроков</li>
 *   <li>{@link AuthAuthenticator} — логика логина/регистрации/смены пароля</li>
 *   <li>{@link AuthRateLimiter} — ограничение частоты запросов</li>
 *   <li>{@link AuthTimeoutManager} — таймаут на авторизацию</li>
 *   <li>{@link AuthConfig} — чтение конфига</li>
 *   <li>{@link AuthGUI} — открытие GUI</li>
 *   <li>{@link AuthGUITracker} — трекинг GUI состояний</li>
 * </ul>
 */
public class AuthManager {

    private static AuthManager instance;

    private final AuthPlayerState playerState;
    private final AuthRateLimiter rateLimiter;
    private final AuthTimeoutManager timeoutManager;
    private final AuthAuthenticator authenticator;

    private AuthManager() {
        this.playerState = new AuthPlayerState();
        this.rateLimiter = new AuthRateLimiter();
        this.timeoutManager = new AuthTimeoutManager();
        this.authenticator = new AuthAuthenticator(playerState, rateLimiter, timeoutManager);
    }

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new AuthManager();

        boolean enabled = true;
        try {
            enabled = Main.getInstance().getConfig().getBoolean("auth.enabled", true);
        } catch (Exception ignored) {}

        if (!enabled) {
            Main.getInstance().getLogger().info("[Auth] System is disabled in config.yml (auth.enabled: false).");
            return;
        }

        AuthDatabase.initTable();
        Main.getInstance().getLogger().info(
                "[Auth] Initialized. Session: " + AuthConfig.getSessionDurationMinutes() + "min"
                + ", IP check: " + AuthConfig.isIpCheckEnabled()
                + ", Dup name check: " + AuthConfig.isDupNameCheckEnabled()
                + ", Cooldown: " + AuthConfig.getRequestCooldownSeconds() + "s"
                + ", Login timeout: " + AuthConfig.getLoginTimeoutSeconds() + "s"
                + ", Max wrong attempts: " + AuthConfig.getMaxWrongAttempts());
    }

    public static AuthManager getInstance() {
        return instance;
    }

    // =========================
    // STATE DELEGATION
    // =========================
    public boolean isAuthenticated(UUID uuid) {
        return playerState.isAuthenticated(uuid);
    }

    public boolean isPendingAuth(UUID uuid) {
        return playerState.isPendingAuth(uuid);
    }

    // =========================
    // HANDLE JOIN
    // =========================
    public void handleJoin(Player player) {
        authenticator.handleJoin(player);
    }

    // =========================
    // HANDLE PASSWORD SUBMIT
    // =========================
    public void handlePasswordSubmit(Player player, String password) {
        authenticator.handlePasswordSubmit(player, password);
    }

    // =========================
    // RATE LIMIT CHECK
    // =========================
    public boolean checkRequestCooldown(Player player) {
        return rateLimiter.checkCooldown(player);
    }

    // =========================
    // REMOVE PLAYER
    // =========================
    public void removePlayer(UUID uuid) {
        playerState.removePlayer(uuid);
        rateLimiter.removePlayer(uuid);
        timeoutManager.removePlayer(uuid);
    }

    // =========================
    // FORCE LOGIN (admin command)
    // =========================
    public boolean forceLogin(UUID uuid) {
        if (!AuthDatabase.isRegistered(uuid)) return false;

        playerState.setAuthenticated(uuid);
        AuthDatabase.updateLastLogin(uuid);

        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.closeInventory();
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setInvulnerable(false);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.force_login_message", "<green>✅</green> <white>You have been force authorized by an administrator!</white>")));
        }
        return true;
    }

    // =========================
    // RESET AUTH (admin command)
    // =========================
    public boolean resetAuth(UUID uuid) {
        if (!AuthDatabase.isRegistered(uuid)) return false;

        playerState.removePlayer(uuid);
        AuthDatabase.deleteRegistration(uuid);

        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            String kickMsg = MessagesManager.getString("auth.admin.kick_resetauth",
                    "<yellow>✦</yellow> MC-Plugin\n\n<red>❌ Your registration has been deleted by an administrator!</red>\n<gray>On next login you will need to register again.</gray>");
            player.kickPlayer(MessageUtil.legacy(kickMsg));
        }
        return true;
    }

    // =========================
    // CHANGE PASSWORD (admin)
    // =========================
    public boolean changePassword(UUID uuid, String newPassword) {
        if (!AuthDatabase.isRegistered(uuid)) return false;
        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();
        if (newPassword.length() < minLen || newPassword.length() > maxLen) return false;

        boolean updated = AuthDatabase.changePassword(uuid, newPassword);
        if (updated) {
            playerState.removePlayer(uuid);
        }
        return updated;
    }

    // =========================
    // DELETE SESSION (admin)
    // =========================
    public boolean deleteSession(UUID uuid) {
        if (!AuthDatabase.isRegistered(uuid)) return false;

        playerState.removePlayer(uuid);
        AuthDatabase.resetAuth(uuid);

        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            String kickMsg = MessagesManager.getString("auth.admin.kick_delsession",
                    "<yellow>✦</yellow> MC-Plugin\n\n<red>❌ Your session has been reset by an administrator!</red>\n<gray>On next login you will need to enter your password again.</gray>");
            player.kickPlayer(MessageUtil.legacy(kickMsg));
        }
        return true;
    }

    // =========================
    // SELF CHANGE PASSWORD
    // =========================
    public void handleSelfChangePassword(Player player, String newPassword) {
        authenticator.handleSelfChangePassword(player, newPassword);
    }

    // =========================
    // SELF-LOGOUT
    // =========================
    public boolean handleLogout(Player player, String password) {
        return authenticator.handleLogout(player, password);
    }

    // =========================
    // RE-OPEN GUI AFTER DELAY
    // =========================
    public void reopenAfterDelay(Player player) {
        authenticator.reopenAfterDelay(player);
    }
}
