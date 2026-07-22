package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Логика аутентификации: вход, регистрация, смена пароля, выход.
 * <p>
 * Все взаимодействие с игроком осуществляется через чат-команды:
 * <ul>
 *   <li>{@code /mp auth register <password>}</li>
 *   <li>{@code /mp auth login <password>}</li>
 *   <li>{@code /mp auth logout <password>}</li>
 *   <li>{@code /mp auth chgpass <old_password> <new_password>}</li>
 * </ul>
 * <p>
 * Оркестрирует взаимодействие между AuthDatabase, AuthPlayerState,
 * AuthRateLimiter, AuthTimeoutManager и AuthConfig.
 */
public class AuthAuthenticator {

    private static AuthAuthenticator instance;
    private final AuthPlayerState playerState;
    private final AuthRateLimiter rateLimiter;
    private final AuthTimeoutManager timeoutManager;

    /**
     * Сохраняет состояние игрока ДО фриза, чтобы восстановить после авторизации.
     * Без этого операторы теряют CREATIVE и allowFlight при каждом входе.
     */
    private final Map<UUID, SavedPlayerState> savedStates = new ConcurrentHashMap<>();

    private record SavedPlayerState(GameMode gameMode, float walkSpeed, float flySpeed, boolean allowFlight, boolean flying) {}

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
            ConsoleLogger.warn("[Auth] DB not ready — skipping auth for " + player.getName());
            return;
        }

        boolean registered = AuthDatabase.isRegistered(uuid);

        if (registered) {
            if (AuthConfig.isIpCheckEnabled()) {
                String lastIp = AuthDatabase.getLastIp(uuid);
                String currentIp = getPlayerIp(player);

                if (!lastIp.isEmpty() && !lastIp.equals(currentIp)) {
                    ConsoleLogger.info(
                            "[Auth] Player " + player.getName() + " IP changed: " + lastIp + " → " + currentIp + " — session reset.");
                    String ipMsg = AuthConfig.getMessage("ip_changed",
                            "<yellow>✦</yellow> <gray>Your IP address has changed. Please log in again.</gray>");
                    player.sendMessage(MessageUtil.parse(ipMsg));
                    AuthDatabase.resetAuth(uuid);
                } else if (AuthDatabase.hasValidSession(uuid, AuthConfig.getSessionDurationMs())) {
                    savePlayerIp(player);
                    playerState.setAuthenticated(uuid);
                    ConsoleLogger.info(
                            "[Auth] Player " + player.getName() + " auto-authenticated (session + IP match).");
                    return;
                }
            } else if (AuthDatabase.hasValidSession(uuid, AuthConfig.getSessionDurationMs())) {
                savePlayerIp(player);
                playerState.setAuthenticated(uuid);
                ConsoleLogger.info(
                        "[Auth] Player " + player.getName() + " auto-authenticated (session, IP check disabled).");
                return;
            }
        }

        // Сохраняем финальное состояние registered для использования в Runnable
        final boolean isRegistered = registered;

        // Freeze player (лёгкий фриз: нельзя двигаться/взаимодействовать до логина)
        playerState.setPendingAuth(uuid);
        freezePlayer(player);
        timeoutManager.startLoginTimeout(player);

        // Chat-based prompt с задержкой 5 тиков (Paper 1.21.11: клиент должен
        // полностью закончить handshake до отправки чат-сообщений)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (playerState.isAuthenticated(uuid)) return;
                sendChatPrompt(player, isRegistered);
            }
        }.runTaskLater(Main.getInstance(), 5L);
    }

    /**
     * Отправляет в чат приглашение войти/зарегистрироваться.
     * <p>
     * MiniMessage header + footer визуально обрамляют инструкцию.
     *
     * @param player       игрок
     * @param isRegistered true если зарегистрирован (login), false если регистрация
     */
    private void sendChatPrompt(Player player, boolean isRegistered) {
        // All lines go through MessageUtil.parse() so MiniMessage tags render.
        // Earlier version mixed legacy §-codes with bare <white>/<gold> tags in
        // player.sendMessage(String), which made the tags render as literal text.
        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<dark_gray>╔ <gold>✦ <bold>Авторизация</bold> <dark_gray>╗"));
        player.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");

        if (isRegistered) {
            player.sendMessage(MessageUtil.parse(AuthConfig.getMessage("login_title",
                    "<gold>✦</gold> <white>Please log in to continue.</white>")));
            player.sendMessage("");
            player.sendMessage(MessageUtil.parse("<gray>▸ <white>Команда: <gold>/mp auth login <password></gold></white>"));
        } else {
            player.sendMessage(MessageUtil.parse(AuthConfig.getMessage("register_title",
                    "<gold>✦</gold> <white>Choose a password to register.</white>")));
            player.sendMessage("");
            player.sendMessage(MessageUtil.parse("<gray>▸ <white>Команда: <gold>/mp auth register <password></gold></white>"));
            player.sendMessage(MessageUtil.parse("<gray>   <dark_gray>Min " + AuthConfig.getMinPasswordLength()
                    + ", max " + AuthConfig.getMaxPasswordLength() + " characters.</dark_gray>"));
        }

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<gray>▸ <white>Другие команды недоступны пока вы не авторизованы.</white>"));
        player.sendMessage(MessageUtil.parse("<gray>  <dark_gray>/mp help works only after login.</dark_gray>"));
        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");
    }

    // =========================
    // HANDLE PASSWORD SUBMIT (login/register via /mp auth login <pass> | register <pass>)
    //
    // ⚠ Argon2id (32MB memory, 2 итерации) выполняется на async thread,
    // чтобы не фризить сервер на 1-2 секунды при каждом логине.
    // =========================
    public void handlePasswordSubmit(Player player, String password) {
        UUID uuid = player.getUniqueId();

        if (playerState.isAuthenticated(uuid)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.messages.already_authenticated",
                    "<gold>✦</gold> <white>You are already logged in!</white>")));
            return;
        }
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
                                player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                        "auth.messages.password_too_short",
                                        "<red>❌ Password must be at least </red><yellow>%min%</yellow><red> characters!</red>")
                                        .replace("%min%", String.valueOf(minLen))));
                            } else {
                                player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                        "auth.messages.password_too_long",
                                        "<red>❌ Password must not exceed </red><yellow>%max%</yellow><red> characters!</red>")
                                        .replace("%max%", String.valueOf(maxLen))));
                            }
                            sendChatPromptAfterError(player, registered);
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
                                        "<red>❌</red> <red>С вашего IP-адреса уже зарегистрировано <yellow>%count%</yellow> аккаунтов!</red>\n" +
                                        "<white>Максимум: <yellow>%limit%</yellow> аккаунтов на один IP.</white>")
                                        .replace("%count%", String.valueOf(currentCount))
                                        .replace("%limit%", String.valueOf(maxAccounts));
                                final String finalMsg = msg;
                                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                                    if (!player.isOnline()) return;
                                    player.sendMessage("");
                                    player.sendMessage(MessageUtil.parse(finalMsg));
                                    player.sendMessage("");
                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                                    sendChatPromptAfterError(player, registered);
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
                        authenticatePlayer(player, "<green>✅</green> <white>Registration successful!</white>");

                        // Suggest 2FA setup
                        player.sendMessage("");
                        player.sendMessage("§6✦ §fTwo-Factor Authentication (2FA)");
                        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§eWant to secure your account via Telegram?");
                        player.sendMessage("§f1. Send §e/start §fto §e@OakworldSRVbot §fto get your Chat ID");
                        player.sendMessage("§f2. Enter: §e/mp auth 2fa setup <your_chat_id>");
                        player.sendMessage("§7You'll receive a confirmation request when logging into the server.");
                        player.sendMessage("§7You can set up 2FA later with the same command.");
                        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                    });
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "auth.messages.auth_check_error",
                                "<red>❌ Error checking password! Please try again.</red>")));
                    }
                });
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Auth] Async auth error", e);
            }
        });
    }

    /**
     * Отправляет игроку repeat-prompt после неверного пароля или иной ошибки.
     */
    private void sendChatPromptAfterError(Player player, boolean isRegistered) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (playerState.isAuthenticated(player.getUniqueId())) return;
                sendChatPrompt(player, isRegistered);
            }
        }.runTaskLater(Main.getInstance(), 20L);
    }

    // =========================
    // LOGIN SUCCESS (вызывается с main thread после async проверки пароля)
    // =========================
    private void handleLoginSuccess(Player player, UUID uuid, String playerIp) {
        if (AuthConfig.isIpCheckEnabled()) {
            String storedIp = AuthDatabase.getLastIp(uuid);
            if (!storedIp.isEmpty() && !storedIp.equals(playerIp)) {
                ConsoleLogger.info(
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

        authenticatePlayer(player, "<green>✅</green> <white>You have successfully logged in!</white>");
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
            authenticatePlayer(player, "<green>✅</green> <white>Logged in (2FA reset — no Telegram linked).</white>");
            return;
        }

        String playerName = player.getName();
        String playerIp = getPlayerIp(player);

        // Отправляем запрос подтверждения боту
        String requestId = Auth2FA.getInstance().sendConfirmation(uuid, playerName, playerIp);
        if (requestId == null) {
            player.sendMessage("§c❌ Failed to send 2FA request! Try again later.");
            unfreezePlayer(player);
            return;
        }

        player.sendMessage("");
        player.sendMessage("§6✦ §f2FA §8— §7Two-Factor Authentication");
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§eRequest sent via Telegram!");
        player.sendMessage("§7Bot: §f@OakworldSRVbot");
        player.sendMessage("§7Open Telegram and confirm the login");
        player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§7Awaiting confirmation...");
        player.sendMessage("");

        ConsoleLogger.info("[Auth2FA] Challenge started for " + playerName
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
                    player.sendMessage("§c❌ 2FA timeout! Use /mp auth login again.");
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
                                authenticatePlayer(player, "<green>✅</green> <white>2FA confirmed! Welcome.</white>");
                                cancel();
                            }
                            case "denied" -> {
                                Auth2FA.getInstance().clearPending(uuid);
                                player.kickPlayer("§c❌ Login rejected via Telegram 2FA.");
                                cancel();
                            }
                            case "bot_down" -> {
                                // 2FA bot down — log the player in with a warning
                                Auth2FA.getInstance().clearPending(uuid);
                                player.sendMessage("§c⚠ 2FA bot is temporarily unavailable! Skipping 2FA for this login.");
                                ConsoleLogger.warn(
                                        "[Auth2FA] Bot unreachable — logging in " + player.getName() + " without 2FA");
                                authenticatePlayer(player, "<green>✅</green> <white>Logged in without 2FA (bot unavailable).</white>");
                                cancel();
                            }
                            case "error" -> {
                                Auth2FA.getInstance().clearPending(uuid);
                                player.sendMessage("§c❌ 2FA error! Use /mp auth login again.");
                                cancel();
                            }
                            case "timeout", "not_found" -> {
                                Auth2FA.getInstance().clearPending(uuid);
                                player.sendMessage("§c❌ 2FA error! Use /mp auth login again.");
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
    // WRONG PASSWORD
    // =========================
    private void handleWrongPassword(Player player, UUID uuid) {
        int attempts = playerState.incrementWrongAttempts(uuid);
        int maxWrong = AuthConfig.getMaxWrongAttempts();
        int remaining = maxWrong - attempts;

        if (attempts >= maxWrong) {
            timeoutManager.cancelLoginTimeout(uuid);
            String kickMsg = MessagesManager.getString("auth.admin.kick_too_many_attempts",
                    "<red>❌ Too many incorrect attempts!</red>\n<gray>You entered the wrong password %attempts% times.</gray>")
                    .replace("%attempts%", String.valueOf(attempts));
            player.kickPlayer(MessageUtil.legacy(kickMsg));
            return;
        }

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                "auth.messages.wrong_password_remaining",
                "<red>❌ Incorrect password! Remaining attempts: </red><yellow>%remaining%</yellow>")
                .replace("%remaining%", String.valueOf(remaining))));
        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                "auth.messages.try_again_hint",
                "<gray>Try again: </gray><gold>/mp auth login <password></gold>")));
        player.sendMessage("");
        // Re-send prompt with delay so player has time to read
        sendChatPromptAfterError(player, true);
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
        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                "auth.messages.session_active",
                "<gray>Enjoy your game! Session active for 1 hour.</gray>")));
        player.sendMessage("");

        ConsoleLogger.info("[Auth] Player " + player.getName() + " authenticated.");
    }

    // =========================
    // SELF CHANGE PASSWORD via /mp auth chgpass <old> <new>
    // hashArgon2 вызывается на async thread
    //
    // Note: для chat-интерфейса требуется <old_password>. Если вызывается без аргументов,
    // вызывающий код должен передать пустую строку или сообщить usage.
    // =========================
    public void handleSelfChangePassword(Player player, String newPassword) {
        UUID uuid = player.getUniqueId();

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();

        if (newPassword.length() < minLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.messages.password_too_short",
                    "<red>❌ Password must be at least </red><yellow>%min%</yellow><red> characters!</red>")
                    .replace("%min%", String.valueOf(minLen))));
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.messages.password_too_long",
                    "<red>❌ Password must not exceed </red><yellow>%max%</yellow><red> characters!</red>")
                    .replace("%max%", String.valueOf(maxLen))));
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
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "auth.messages.password_changed",
                            "<green>✔</green> <white>Password successfully changed!</white>")));
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "auth.messages.session_active",
                            "<gray>Enjoy your game! Session active for 1 hour.</gray>")));
                    player.sendMessage("");

                    ConsoleLogger.info("[Auth] Player " + player.getName() + " changed password.");
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "auth.messages.change_password_error",
                                "<red>❌ Password change error! Please try again.</red>")));
                    }
                });
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Auth] Async change password error", e);
            }
        });
    }

    /**
     * Change password via chat command `/mp auth chgpass <old> <new>`.
     * Verifies old password asynchronously, then re-hashes new password.
     */
    public void handleSelfChangePassword(Player player, String oldPassword, String newPassword) {
        UUID uuid = player.getUniqueId();

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();

        if (newPassword.length() < minLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.messages.password_too_short",
                    "<red>❌ Password must be at least </red><yellow>%min%</yellow><red> characters!</red>")
                    .replace("%min%", String.valueOf(minLen))));
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.messages.password_too_long",
                    "<red>❌ Password must not exceed </red><yellow>%max%</yellow><red> characters!</red>")
                    .replace("%max%", String.valueOf(maxLen))));
            return;
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp auth chgpass <old> <new></white>"));
            return;
        }

        if (!rateLimiter.checkCooldown(player)) return;

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                boolean validOld = AuthDatabase.checkPassword(uuid, oldPassword);
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    if (!validOld) {
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "auth.messages.wrong_password",
                                "<red>❌ Current password is incorrect!</red>")));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                        return;
                    }
                    // Re-use the existing async hash+update flow
                    handleSelfChangePassword(player, newPassword);
                });
            } catch (Exception e) {
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Auth] Async chgpass verify error", e);
            }
        });
    }

    // =========================
    // SELF-LOGOUT via /mp auth logout <password>
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
                        player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                                "auth.messages.wrong_password",
                                "<red>❌ Incorrect password! Try again.</red>")));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                        return;
                    }

                    playerState.removeAuthenticated(uuid);
                    playerState.removePendingAuth(uuid);
                    AuthDatabase.resetAuth(uuid);

                    String kickLogout = MessagesManager.getString("auth.admin.kick_logout",
                            "<green>✔</green> You have successfully logged out!\n<gray>On next login you will need to enter your password again.</gray>");
                    player.kickPlayer(MessageUtil.legacy(kickLogout));

                    ConsoleLogger.info("[Auth] Player " + player.getName() + " logged out manually.");
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
        UUID uuid = player.getUniqueId();
        // Defensive: discard any leaked savedStates entry from a previous session/disconnect.
        // If a player disconnected mid-auth, the leaked entry would otherwise be RE-FROZEN on
        // next join — and on successful auth, unfreezePlayer would restore the frozen state
        // (GM=ADVENTURE, walkSpeed=0) instead of the real pre-freeze state, leaving the player
        // unable to move or fly even after authentication.
        SavedPlayerState leaked = savedStates.remove(uuid);
        if (leaked != null) {
            ConsoleLogger.warn("[Auth] Discarding leaked savedStates for " + player.getName()
                    + " (was " + leaked.gameMode + "/walk=" + leaked.walkSpeed + ")");
            // Best-effort restore so the live Player object isn't visibly broken either
            try {
                player.setGameMode(leaked.gameMode);
                player.setWalkSpeed(leaked.walkSpeed);
                player.setFlySpeed(leaked.flySpeed);
                player.setAllowFlight(leaked.allowFlight);
                player.setFlying(leaked.flying);
                player.setInvulnerable(false);
            } catch (Throwable ignored) {}
        }
        // Сохраняем состояние ДО фриза
        savedStates.put(uuid, new SavedPlayerState(
                player.getGameMode(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getAllowFlight(),
                player.isFlying()
        ));
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
    }

    private void unfreezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        SavedPlayerState saved = savedStates.remove(uuid);
        if (saved != null) {
            player.setGameMode(saved.gameMode);
            player.setWalkSpeed(saved.walkSpeed);
            player.setFlySpeed(saved.flySpeed);
            player.setAllowFlight(saved.allowFlight);
            player.setFlying(saved.flying);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
        player.setInvulnerable(false);
    }

    /**
     * Восстанавливает сохранённое состояние игрока (используется forceLogin из AuthManager).
     */
    public void restorePlayerState(Player player) {
        unfreezePlayer(player);
    }

    /**
     * Per-player cleanup on PlayerQuitEvent. Restores the saved pre-freeze state (if any)
     * and persists it to player.dat so the next login doesn't start from the frozen state
     * (GM=ADVENTURE, walkSpeed=0, flySpeed=0, invulnerable=true).
     * <p>
     * Without this, a player who disconnects mid-auth would:
     *   1. Have a leaked savedStates entry, AND
     *   2. Have frozen state stored in player.dat
     * → on next join, freezePlayer captures the frozen state as "original"
     * → unfreeze restores frozen state → player stuck (can't move, can't fly).
     * <p>
     * playerState + timeout cleanup is handled separately by {@link AuthManager#removePlayer}.
     */
    public void handleQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        SavedPlayerState saved = savedStates.remove(uuid);
        if (saved != null) {
            try {
                player.setGameMode(saved.gameMode);
                player.setWalkSpeed(saved.walkSpeed);
                player.setFlySpeed(saved.flySpeed);
                player.setAllowFlight(saved.allowFlight);
                player.setFlying(saved.flying);
                player.setInvulnerable(false);
                player.saveData(); // persist restore to player.dat for next login
            } catch (Throwable t) {
                ConsoleLogger.warn("[Auth] handleQuit restore failed for " + player.getName()
                        + ": " + t.getMessage());
            }
        }
    }

    // =========================
    // GET PLAYER IP
    // =========================
    private String getPlayerIp(Player player) {
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Auth] Failed to get player IP: " + e.getMessage());
        }
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
