package com.mcplugin.mechanics.security.auth;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
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

        // Сохраняем финальное состояние registered для использования в Runnable
        final boolean isRegistered = registered;

        // Freeze player and show chat prompt
        playerState.setPendingAuth(uuid);
        freezePlayer(player);
        timeoutManager.startLoginTimeout(player);

        // Show auth instructions in chat with 1-tick delay (handshake must complete)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (!playerState.needsAuth(player)) return;
                sendAuthPrompt(player, isRegistered);
            }
        }.runTaskLater(Main.getInstance(), 1L);
    }

    /**
     * Отправляет в чат инструкцию по авторизации.
     */
    private void sendAuthPrompt(Player player, boolean isRegistered) {
        player.sendMessage("");
        player.sendMessage("§6✦ §fMC-Plugin §8— §7Authorization");
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        if (isRegistered) {
            player.sendMessage("§c❌ §fYou are §lNOT§r §fauthorized!");
            player.sendMessage("§7Please log in to continue playing:");
            player.sendMessage("§e/mp auth login §7<§opassword§7>");
        } else {
            player.sendMessage("§c❌ §fYou are §lNOT§r §fregistered!");
            player.sendMessage("§7Please register to continue playing:");
            player.sendMessage("§e/mp auth register §7<§opassword§7>");
        }
        player.sendMessage("");
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
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

                        // Предлагаем настроить 2FA
                        player.sendMessage("");
                        player.sendMessage("§6✦ §fДвухфакторная аутентификация (2FA)");
                        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§eХотите защитить аккаунт через Telegram?");
                        player.sendMessage("§f1. Напишите боту @OakworldSRVbot команду §e/start§f — получите свой Chat ID");
                        player.sendMessage("§f2. Введите: §e/mp auth 2fa setup <ваш_chat_id>");
                        player.sendMessage("§7Код будет приходить только при авторизации на сервере.");
                        player.sendMessage("§7Вы можете настроить 2FA позже той же командой.");
                        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                    });
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.auth_check_error", "<red>❌ Error checking password! Please try again.</red>")));
                    }
                });
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Auth] Async auth error", e);
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

        // Если 2FA включена — запускаем challenge вместо полной аутентификации
        if (Auth2FA.isEnabled(uuid)) {
            start2FAChallenge(player);
            return;
        }

        authenticatePlayer(player, "<green>\u2705</green> <white>You have successfully logged in!</white>");
    }

    // =========================
    // 2FA CHALLENGE — подтверждение через кнопки в Telegram
    // =========================
    public void start2FAChallenge(Player player) {
        UUID uuid = player.getUniqueId();
        String chatId = Auth2FA.getChatId(uuid);
        if (chatId == null || chatId.isEmpty()) {
            // Нет chat_id — отключаем 2FA и пускаем без кода
            Auth2FA.remove(uuid);
            authenticatePlayer(player, "<green>\u2705</green> <white>Logged in (2FA reset — no Telegram linked).</white>");
            return;
        }

        String playerName = player.getName();
        String playerIp = getPlayerIp(player);

        // Отправляем запрос подтверждения боту
        String requestId = Auth2FA.getInstance().sendConfirmation(uuid, playerName, playerIp);
        if (requestId == null) {
            player.sendMessage("§c❌ Ошибка отправки запроса 2FA! Попробуйте позже.");
            return;
        }

        player.sendMessage("");
        player.sendMessage("§6✦ §f2FA §8— §7Двухфакторная аутентификация");
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§eЗапрос отправлен в Telegram!");
        player.sendMessage("§7Бот: §f@OakworldSRVbot");
        player.sendMessage("§7Откройте Telegram и подтвердите вход");
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§7Ожидание подтверждения...");
        player.sendMessage("");

        Main.getInstance().getLogger().info("[Auth2FA] Challenge started for " + playerName
                + " (chat: " + chatId + ", request: " + requestId + ")");

        // Запускаем polling — проверяем статус каждые 20 тиков (1 секунда)
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 20 * 60; // 60 секунд максимум

            @Override
            public void run() {
                if (!player.isOnline()) {
                    Auth2FA.getInstance().clearPending(uuid);
                    cancel();
                    return;
                }

                if (playerState.isAuthenticated(uuid)) {
                    cancel();
                    return;
                }

                ticks += 20;
                if (ticks > maxTicks) {
                    // Таймаут
                    Auth2FA.getInstance().clearPending(uuid);
                    player.sendMessage("§c❌ Время ожидания 2FA истекло! Используйте /mp auth login заново.");
                    cancel();
                    return;
                }

                // Проверяем статус (на async потоке, чтобы не блокировать сервер)
                Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
                    String status = Auth2FA.getInstance().checkConfirmation(uuid);

                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        if (!player.isOnline()) return;
                        if (playerState.isAuthenticated(uuid)) return;

                        switch (status) {
                            case "approved" -> {
                                Auth2FA.getInstance().clearPending(uuid);
                                authenticatePlayer(player, "<green>\u2705</green> <white>2FA подтверждена! Добро пожаловать.</white>");
                                cancel();
                            }
                            case "denied" -> {
                                Auth2FA.getInstance().clearPending(uuid);
                                player.kickPlayer("§c❌ Вход отклонён через Telegram 2FA.");
                                cancel();
                            }
                            case "timeout", "not_found" -> {
                                Auth2FA.getInstance().clearPending(uuid);
                                player.sendMessage("§c❌ Ошибка 2FA! Используйте /mp auth login заново.");
                                cancel();
                            }
                        }
                    });
                });
            }
        }.runTaskTimer(Main.getInstance(), 20L, 20L); // первый через 1 сек, потом каждую секунду
    }

    /**
     * Устаревший метод — больше не нужен (2FA через кнопки, а не коды).
     * Оставлен для обратной совместимости.
     */
    @Deprecated
    public boolean verify2FACode(Player player, String code) {
        return false;
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
        // Resend auth prompt
        boolean isRegistered = AuthDatabase.isRegistered(uuid);
        sendAuthPrompt(player, isRegistered);
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

                    unfreezePlayer(player);

                    player.sendMessage("");
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_changed", "<green>✔</green> <white>Password successfully changed!</white>")));
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
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Auth] Async change password error", e);
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

                    String kickLogout = MessagesManager.getString("auth.admin.kick_logout",
                            "<green>✔</green> You have successfully logged out!\n<gray>On next login you will need to enter your password again.</gray>");
                    player.kickPlayer(MessageUtil.legacy(kickLogout));

                    Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " logged out manually.");
                });
            } catch (Exception e) {
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Auth] Async logout error", e);
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
    // RESEND AUTH PROMPT AFTER DELAY
    // =========================
    public void reopenAfterDelay(Player player) {
        if (playerState.isAuthenticated(player.getUniqueId())) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (playerState.isAuthenticated(player.getUniqueId())) return;
                sendAuthPrompt(player, AuthDatabase.isRegistered(player.getUniqueId()));
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
