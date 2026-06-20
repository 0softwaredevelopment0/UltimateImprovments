package com.mcplugin.auth;

import com.mcplugin.Main;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Логика аутентификации: вход, регистрация, смена пароля, выход.
 * <p>
 * Оркестрирует взаимодействие между AuthDatabase, AuthPlayerState,
 * AuthRateLimiter, AuthTimeoutManager и AuthConfig.
 */
public class AuthAuthenticator {

    private static AuthAuthenticator instance;
    private final AuthPlayerState playerState;
    private final AuthRateLimiter rateLimiter;
    private final AuthTimeoutManager timeoutManager;

    public AuthAuthenticator(AuthPlayerState playerState, AuthRateLimiter rateLimiter, AuthTimeoutManager timeoutManager) {
        this.playerState = playerState;
        this.rateLimiter = rateLimiter;
        this.timeoutManager = timeoutManager;
        instance = this;
    }

    public static AuthAuthenticator getInstance() {
        return instance;
    }

    // =========================
    // HANDLE JOIN
    // =========================
    public void handleJoin(Player player) {
        if (!AuthConfig.isEnabled()) return;

        UUID uuid = player.getUniqueId();

        if (playerState.isAuthenticated(uuid)) return;

        if (!AuthDatabase.isTableReady()) {
            Main.getInstance().getLogger().warning("[Auth] DB not ready — skipping auth for " + player.getName());
            return;
        }

        boolean registered = AuthDatabase.isRegistered(uuid);

        if (registered) {
            if (AuthConfig.isIpCheckEnabled()) {
                String lastIp = AuthDatabase.getLastIp(uuid);
                String currentIp = getPlayerIp(player);

                if (!lastIp.isEmpty() && !lastIp.equals(currentIp)) {
                    Main.getInstance().getLogger().info(
                            "[Auth] Player " + player.getName() + " IP changed: " + lastIp + " → " + currentIp + " — session reset.");
                    String ipMsg = AuthConfig.getMessage("ip_changed",
                            "<yellow>✦</yellow> <gray>Ваш IP-адрес изменился. Пожалуйста, войдите заново.</gray>");
                    player.sendMessage(MessageUtil.parse(ipMsg));
                    AuthDatabase.resetAuth(uuid);
                    registered = true;
                } else if (AuthDatabase.hasValidSession(uuid, AuthConfig.getSessionDurationMs())) {
                    savePlayerIp(player);
                    playerState.setAuthenticated(uuid);
                    Main.getInstance().getLogger().info(
                            "[Auth] Player " + player.getName() + " auto-authenticated (session + IP match).");
                    return;
                }
            } else if (AuthDatabase.hasValidSession(uuid, AuthConfig.getSessionDurationMs())) {
                savePlayerIp(player);
                playerState.setAuthenticated(uuid);
                Main.getInstance().getLogger().info(
                        "[Auth] Player " + player.getName() + " auto-authenticated (session, IP check disabled).");
                return;
            }
        }

        // No valid session — freeze and show GUI on next tick
        // MUST delay by 1 tick: opening any inventory inside PlayerJoinEvent
        // can fail because the client connection is not yet fully ready.
        playerState.setPendingAuth(uuid);

        freezePlayer(player);

        player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <gray>Открываем окно авторизации...</gray>"));

        // Start login timeout kick timer
        timeoutManager.startLoginTimeout(player);

        // ⏱ 1-tick delay — client must finish join handshake before receiving inventory packets
        final boolean isRegistered = registered;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (!playerState.needsAuth(player)) return;

                try {
                    if (isRegistered) {
                        AuthGUI.openLogin(player);
                    } else {
                        AuthGUI.openRegister(player);
                    }
                } catch (Exception e) {
                    Main.getInstance().getLogger().severe("[Auth] Failed to open auth GUI for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    player.sendMessage(MessageUtil.parse("<red>❌ Ошибка открытия окна авторизации! Сообщите администратору.</red>"));
                }
            }
        }.runTaskLater(Main.getInstance(), 1L);
    }

    // =========================
    // HANDLE PASSWORD SUBMIT (login/register)
    // =========================
    public void handlePasswordSubmit(Player player, String password) {
        UUID uuid = player.getUniqueId();

        if (playerState.isAuthenticated(uuid)) return;
        if (!rateLimiter.checkCooldown(player)) return;

        String playerIp = getPlayerIp(player);

        if (AuthDatabase.isRegistered(uuid)) {
            handleLogin(player, password, uuid, playerIp);
        } else {
            handleRegister(player, password, uuid, playerIp);
        }
    }

    // =========================
    // LOGIN
    // =========================
    private void handleLogin(Player player, String password, UUID uuid, String playerIp) {
        if (AuthDatabase.checkPassword(uuid, password)) {
            if (AuthConfig.isIpCheckEnabled()) {
                String storedIp = AuthDatabase.getLastIp(uuid);
                if (!storedIp.isEmpty() && !storedIp.equals(playerIp)) {
                    Main.getInstance().getLogger().info(
                            "[Auth] Player " + player.getName() + " login IP changed: " + storedIp + " → " + playerIp + " — updating IP.");
                    AuthDatabase.updateLastIp(uuid, playerIp);
                }
            }

            AuthDatabase.updateLastLogin(uuid);
            playerState.resetWrongAttempts(uuid);
            authenticatePlayer(player, "<green>✅</green> <white>Вы успешно вошли на сервер!</white>");
        } else {
            handleWrongPassword(player, uuid);
        }
    }

    // =========================
    // REGISTER
    // =========================
    private void handleRegister(Player player, String password, UUID uuid, String playerIp) {
        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();

        if (password.length() < minLen) {
            player.sendMessage(MessageUtil.parse("<red>❌ Пароль должен быть не менее </red><yellow>" + minLen + "</yellow><red> символов!</red>"));
            reopenAfterDelay(player);
            return;
        }
        if (password.length() > maxLen) {
            player.sendMessage(MessageUtil.parse("<red>❌ Пароль не должен превышать </red><yellow>" + maxLen + "</yellow><red> символов!</red>"));
            reopenAfterDelay(player);
            return;
        }

        // Check per-IP account limit
        if (!playerIp.isEmpty()) {
            int maxAccounts = AuthConfig.getMaxAccountsPerIp();
            if (maxAccounts > 0) {
                int currentCount = AuthDatabase.countAccountsByIp(playerIp);
                if (currentCount >= maxAccounts) {
                    String msg = AuthConfig.getMessage("max_accounts_per_ip",
                            "<red>❌ С вашего IP-адреса уже зарегистрировано <yellow>{count}</yellow> аккаунтов!</red>\n" +
                            "<white>Максимум: <yellow>{limit}</yellow> аккаунтов на один IP.</white>")
                            .replace("{count}", String.valueOf(currentCount))
                            .replace("{limit}", String.valueOf(maxAccounts));
                    player.sendMessage("");
                    player.sendMessage(MessageUtil.parse(msg));
                    player.sendMessage("");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                    reopenAfterDelay(player);
                    return;
                }
            }
        }

        AuthDatabase.register(uuid, password, playerIp);
        playerState.resetWrongAttempts(uuid);
        authenticatePlayer(player, "<green>✅</green> <white>Регистрация прошла успешно!</white>");
    }

    // =========================
    // WRONG PASSWORD
    // =========================
    private void handleWrongPassword(Player player, UUID uuid) {
        int attempts = playerState.incrementWrongAttempts(uuid);
        int maxWrong = AuthConfig.getMaxWrongAttempts();
        int remaining = maxWrong - attempts;

        if (attempts >= maxWrong) {
            timeoutManager.cancelLoginTimeout(uuid);
            player.kickPlayer(
                    "§6✦ MC-Plugin\n" +
                    "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "§c❌ Слишком много неверных попыток!\n" +
                    "§7Вы ввели неверный пароль §c" + attempts + "§7 раз.\n\n" +
                    "§7━━━━━━━━━━━━━━━━━━━━━"
            );
            return;
        }

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<red>❌ Неверный пароль! Осталось попыток: </red><yellow>" + remaining + "</yellow>"));
        player.sendMessage("");
    }

    // =========================
    // AUTHENTICATE PLAYER
    // =========================
    private void authenticatePlayer(Player player, String message) {
        UUID uuid = player.getUniqueId();
        playerState.setAuthenticated(uuid);
        savePlayerIp(player);

        timeoutManager.cancelLoginTimeout(uuid);
        playerState.resetWrongAttempts(uuid);

        AuthGUITracker.removeAuthItemsFromPlayer(player);
        player.closeInventory();

        unfreezePlayer(player);

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(message));
        player.sendMessage(MessageUtil.parse("<gray>Приятной игры! Сессия активна 1 час.</gray>"));
        player.sendMessage("");

        Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " authenticated.");
    }

    // =========================
    // SELF CHANGE PASSWORD
    // =========================
    public void handleSelfChangePassword(Player player, String newPassword) {
        UUID uuid = player.getUniqueId();

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();

        if (newPassword.length() < minLen) {
            player.sendMessage("§c❌ Пароль должен быть не менее " + minLen + " символов!");
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage("§c❌ Пароль не должен превышать " + maxLen + " символов!");
            return;
        }

        AuthDatabase.changePasswordSelf(uuid, newPassword);

        playerState.setAuthenticated(uuid);
        savePlayerIp(player);

        AuthGUITracker.removeAuthItemsFromPlayer(player);
        player.closeInventory();
        unfreezePlayer(player);

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<green>✅</green> <white>Пароль успешно изменён!</white>"));
        player.sendMessage("§7Приятной игры! Сессия активна 1 час.");
        player.sendMessage("");

        Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " changed password.");
    }

    // =========================
    // SELF-LOGOUT
    // =========================
    public boolean handleLogout(Player player, String password) {
        UUID uuid = player.getUniqueId();

        if (!playerState.isAuthenticated(uuid)) return false;
        if (!AuthDatabase.isRegistered(uuid)) return false;
        if (!rateLimiter.checkCooldown(player)) return false;
        if (!AuthDatabase.checkPassword(uuid, password)) return false;

        playerState.removeAuthenticated(uuid);
        playerState.removePendingAuth(uuid);
        AuthDatabase.resetAuth(uuid);

        player.closeInventory();
        player.kickPlayer(
                "§6✦ MC-Plugin\n" +
                "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "§a✅ Вы успешно вышли из аккаунта!\n" +
                "§7При следующем входе нужно будет\n" +
                "§7снова ввести пароль для входа.\n\n" +
                "§7━━━━━━━━━━━━━━━━━━━━━"
        );

        Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " logged out manually.");
        return true;
    }

    // =========================
    // FREEZE / UNFREEZE PLAYER
    // =========================
    private void freezePlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
    }

    private void unfreezePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
    }

    // =========================
    // RE-OPEN GUI AFTER DELAY
    // =========================
    public void reopenAfterDelay(Player player) {
        if (playerState.isAuthenticated(player.getUniqueId())) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (playerState.isAuthenticated(player.getUniqueId())) return;

                if (AuthDatabase.isRegistered(player.getUniqueId())) {
                    AuthGUI.openLogin(player);
                } else {
                    AuthGUI.openRegister(player);
                }
            }
        }.runTaskLater(Main.getInstance(), 5L);
    }

    // =========================
    // GET PLAYER IP
    // =========================
    private String getPlayerIp(Player player) {
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}
        return "";
    }

    // =========================
    // SAVE PLAYER IP TO DB
    // =========================
    private void savePlayerIp(Player player) {
        String ip = getPlayerIp(player);
        if (!ip.isEmpty()) {
            AuthDatabase.updateLastIp(player.getUniqueId(), ip);
        }
    }
}
