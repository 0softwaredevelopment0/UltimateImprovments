package com.mcplugin.auth;

import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
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
                            "<yellow>✦</yellow> <gray>Your IP address has changed. Please log in again.</gray>");
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

        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.opening_auth_window", "<yellow>✦</yellow> <gray>Opening authorization window...</gray>")));

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
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.auth_window_error", "<red>❌ Error opening authorization window! Report to an administrator.</red>")));
                }
            }
        }.runTaskLater(Main.getInstance(), 1L);
    }

    // =========================
    // HANDLE PASSWORD SUBMIT (login/register)
    //
    // ⚠ Argon2id (32MB memory, 2 итерации) выполняется на async thread,
    // чтобы не фризить сервер на 1-2 секунды при каждом логине.
    // =========================
    public void handlePasswordSubmit(Player player, String password) {
        UUID uuid = player.getUniqueId();

        if (playerState.isAuthenticated(uuid)) return;
        if (!rateLimiter.checkCooldown(player)) return;

        String playerIp = getPlayerIp(player);

        // Argon2id на async thread — предотвращает фриз сервера
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                boolean registered = AuthDatabase.isRegistered(uuid);

                if (registered) {
                    boolean passwordValid = AuthDatabase.checkPassword(uuid, password);
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        if (!player.isOnline()) return;
                        if (passwordValid) {
                            handleLoginSuccess(player, uuid, playerIp);
                        } else {
                            handleWrongPassword(player, uuid);
                        }
                    });
                } else {
                    // Проверки длины пароля (на async потоке — безопасно, чисто строки)
                    int minLen = AuthConfig.getMinPasswordLength();
                    int maxLen = AuthConfig.getMaxPasswordLength();
                    if (password.length() < minLen || password.length() > maxLen) {
                        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                            if (!player.isOnline()) return;
                            if (password.length() < minLen) {
                                player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_short", "<red>❌ Password must be at least </red><yellow>{min}</yellow><red> characters!</red>").replace("{min}", String.valueOf(minLen))));
                            } else {
                                player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_long", "<red>❌ Password must not exceed </red><yellow>{max}</yellow><red> characters!</red>").replace("{max}", String.valueOf(maxLen))));
                            }
                            reopenAfterDelay(player);
                        });
                        return;
                    }

                    // Проверка лимита аккаунтов на IP
                    if (!playerIp.isEmpty()) {
                        int maxAccounts = AuthConfig.getMaxAccountsPerIp();
                        if (maxAccounts > 0) {
                            int currentCount = AuthDatabase.countAccountsByIp(playerIp);
                            if (currentCount >= maxAccounts) {
                                String msg = AuthConfig.getMessage("max_accounts_per_ip",
                                        "<red>\u274c</red> <red>С вашего IP-адреса уже зарегистрировано <yellow>{count}</yellow> аккаунтов!</red>\n" +
                                        "<white>Максимум: <yellow>{limit}</yellow> аккаунтов на один IP.</white>")
                                        .replace("{count}", String.valueOf(currentCount))
                                        .replace("{limit}", String.valueOf(maxAccounts));
                                final String finalMsg = msg;
                                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                                    if (!player.isOnline()) return;
                                    player.sendMessage("");
                                    player.sendMessage(MessageUtil.parse(finalMsg));
                                    player.sendMessage("");
                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                                    reopenAfterDelay(player);
                                });
                                return;
                            }
                        }
                    }

                    // hashArgon2 на async thread (32MB memory)
                    AuthDatabase.register(uuid, password, playerIp);

                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        if (!player.isOnline()) return;
                        playerState.resetWrongAttempts(uuid);
                        authenticatePlayer(player, "<green>\u2705</green> <white>Registration successful!</white>");
                    });
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.auth_check_error", "<red>❌ Error checking password! Please try again.</red>")));
                    }
                });
                Main.getInstance().getLogger().severe("[Auth] Async auth error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // =========================
    // LOGIN SUCCESS (вызывается с main thread после async проверки пароля)
    // =========================
    private void handleLoginSuccess(Player player, UUID uuid, String playerIp) {
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
        authenticatePlayer(player, "<green>\u2705</green> <white>You have successfully logged in!</white>");
    }

    // =========================
    // REGISTER (удалён — логика перенесена в handlePasswordSubmit для async)
    // =========================


    // =========================
    // WRONG PASSWORD
    // =========================
    private void handleWrongPassword(Player player, UUID uuid) {
        int attempts = playerState.incrementWrongAttempts(uuid);
        int maxWrong = AuthConfig.getMaxWrongAttempts();
        int remaining = maxWrong - attempts;

        if (attempts >= maxWrong) {
            timeoutManager.cancelLoginTimeout(uuid);
            String kickMsg = MessagesManager.getString("auth.admin.kick_too_many_attempts",
                    "<red>❌ Too many incorrect attempts!</red>\n<gray>You entered the wrong password {attempts} times.</gray>")
                    .replace("{attempts}", String.valueOf(attempts));
            player.kickPlayer(MessageUtil.legacy(kickMsg));
            return;
        }

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.wrong_password_remaining", "<red>❌ Incorrect password! Remaining attempts: </red><yellow>{remaining}</yellow>").replace("{remaining}", String.valueOf(remaining))));
        player.sendMessage("");
    }

    // =========================
    // AUTHENTICATE PLAYER (вызывается с main thread)
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
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.session_active", "<gray>Enjoy your game! Session active for 1 hour.</gray>")));
        player.sendMessage("");

        Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " authenticated.");
    }

    // =========================
    // SELF CHANGE PASSWORD
    // hashArgon2 вызывается на async thread
    // =========================
    public void handleSelfChangePassword(Player player, String newPassword) {
        UUID uuid = player.getUniqueId();

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();

        if (newPassword.length() < minLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_short", "<red>❌ Password must be at least </red><yellow>{min}</yellow><red> characters!</red>").replace("{min}", String.valueOf(minLen))));
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_long", "<red>❌ Password must not exceed </red><yellow>{max}</yellow><red> characters!</red>").replace("{max}", String.valueOf(maxLen))));
            return;
        }

        // hashArgon2 на async thread (32MB memory)
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                AuthDatabase.changePasswordSelf(uuid, newPassword);

                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (!player.isOnline()) return;

                    playerState.setAuthenticated(uuid);
                    savePlayerIp(player);

                    AuthGUITracker.removeAuthItemsFromPlayer(player);
                    player.closeInventory();
                    unfreezePlayer(player);

                    player.sendMessage("");
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_changed", "<green>✅</green> <white>Password successfully changed!</white>")));
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.session_active", "<gray>Enjoy your game! Session active for 1 hour.</gray>")));
                    player.sendMessage("");

                    Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " changed password.");
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.change_password_error", "<red>❌ Password change error! Please try again.</red>")));
                    }
                });
                Main.getInstance().getLogger().severe("[Auth] Async change password error: " + e.getMessage());
            }
        });
    }

    // =========================
    // SELF-LOGOUT
    // Argon2 verify на async thread
    // =========================
    public boolean handleLogout(Player player, String password) {
        UUID uuid = player.getUniqueId();

        if (!playerState.isAuthenticated(uuid)) return false;
        if (!AuthDatabase.isRegistered(uuid)) return false;
        if (!rateLimiter.checkCooldown(player)) return false;

        // Argon2 verify на async thread
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                boolean valid = AuthDatabase.checkPassword(uuid, password);

                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (!player.isOnline()) return;

                    if (!valid) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.wrong_password", "<red>❌ Incorrect password! Try again.</red>")));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                        return;
                    }

                    playerState.removeAuthenticated(uuid);
                    playerState.removePendingAuth(uuid);
                    AuthDatabase.resetAuth(uuid);

                    player.closeInventory();
                    String kickLogout = MessagesManager.getString("auth.admin.kick_logout",
                            "<green>✅</green> You have successfully logged out!\n<gray>On next login you will need to enter your password again.</gray>");
                    player.kickPlayer(MessageUtil.legacy(kickLogout));

                    Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " logged out manually.");
                });
            } catch (Exception e) {
                Main.getInstance().getLogger().severe("[Auth] Async logout error: " + e.getMessage());
            }
        });

        // Возвращаем true сразу — реальная проверка асинхронная
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
