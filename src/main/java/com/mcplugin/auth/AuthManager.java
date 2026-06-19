package com.mcplugin.auth;

import com.mcplugin.Main;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Sound;

public class AuthManager {

    private static AuthManager instance;

    private final Set<UUID> authenticated = new HashSet<>();
    private final Set<UUID> pendingAuth = new HashSet<>();

    // ⏱ Rate limit: 1 DB запрос в N секунд на игрока (берётся из config.yml)
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();

    // ⏱ Таймаут на авторизацию: кикнуть если не залогинился за N секунд
    private final Map<UUID, BukkitRunnable> loginTimeoutTasks = new ConcurrentHashMap<>();

    // 🔢 Счётчик неверных попыток пароля (сбрасывается при успешном входе/выходе)
    private final Map<UUID, Integer> wrongAttempts = new ConcurrentHashMap<>();

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
        int sessionMin = getConfigInt("auth.session_duration_minutes", 60);
        boolean ipCheck = getConfigBool("auth.check_ip.enabled", true);
        boolean dupNameCheck = getConfigBool("auth.check_duplicate_name.enabled", true);
        int cooldownSec = getConfigInt("auth.request_cooldown_seconds", 5);
        int timeoutSec = getConfigInt("auth.login_timeout_seconds", 60);
        int maxWrong = getConfigInt("auth.max_wrong_attempts", 5);
        Main.getInstance().getLogger().info(
                "[Auth] Initialized. Session: " + sessionMin + "min, IP check: " + ipCheck
                + ", Dup name check: " + dupNameCheck + ", Cooldown: " + cooldownSec + "s"
                + ", Login timeout: " + timeoutSec + "s, Max wrong attempts: " + maxWrong);
    }

    // =========================
    // CONFIG HELPERS
    // =========================
    private static long getSessionDurationMs() {
        return getConfigInt("auth.session_duration_minutes", 60) * 60000L;
    }

    private static boolean isIpCheckEnabled() {
        return getConfigBool("auth.check_ip.enabled", true);
    }

    private static int getMaxAccountsPerIp() {
        int max = getConfigInt("auth.max_accounts_per_ip", 3);
        return Math.max(max, 0);
    }

    private static String getConfigMessage(String path, String def) {
        try {
            return Main.getInstance().getConfig().getString("auth.messages." + path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static int getConfigInt(String path, int def) {
        try {
            return Main.getInstance().getConfig().getInt(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean getConfigBool(String path, boolean def) {
        try {
            return Main.getInstance().getConfig().getBoolean(path, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static AuthManager getInstance() {
        return instance;
    }

    // =========================
    // STATE CHECKS
    // =========================
    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public boolean isPendingAuth(UUID uuid) {
        return pendingAuth.contains(uuid);
    }

    // =========================
    // IS AUTH ENABLED IN CONFIG
    // =========================
    private boolean isEnabled() {
        try {
            return Main.getInstance() != null
                    && Main.getInstance().getConfig() != null
                    && Main.getInstance().getConfig().getBoolean("auth.enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    // =========================
    // HANDLE JOIN
    // =========================
    public void handleJoin(Player player) {
        if (!isEnabled()) return;

        UUID uuid = player.getUniqueId();

        if (authenticated.contains(uuid)) return;

        if (!AuthDatabase.isTableReady()) {
            Main.getInstance().getLogger().warning("[Auth] DB not ready — skipping auth for " + player.getName());
            return;
        }

        boolean registered = AuthDatabase.isRegistered(uuid);

        if (registered) {
            if (isIpCheckEnabled()) {
                String lastIp = AuthDatabase.getLastIp(uuid);
                String currentIp = getPlayerIp(player);

                if (!lastIp.isEmpty() && !lastIp.equals(currentIp)) {
                    Main.getInstance().getLogger().info(
                            "[Auth] Player " + player.getName() + " IP changed: " + lastIp + " → " + currentIp + " — session reset.");
                    String ipMsg = getConfigMessage("ip_changed", "<yellow>✦</yellow> <gray>Ваш IP-адрес изменился. Пожалуйста, войдите заново.</gray>");
                    player.sendMessage(MessageUtil.parse(ipMsg));
                    AuthDatabase.resetAuth(uuid);
                    registered = true;
                } else if (AuthDatabase.hasValidSession(uuid, getSessionDurationMs())) {
                    savePlayerIp(player);
                    authenticated.add(uuid);
                    Main.getInstance().getLogger().info(
                            "[Auth] Player " + player.getName() + " auto-authenticated (session + IP match).");
                    return;
                }
            } else if (AuthDatabase.hasValidSession(uuid, getSessionDurationMs())) {
                savePlayerIp(player);
                authenticated.add(uuid);
                Main.getInstance().getLogger().info(
                        "[Auth] Player " + player.getName() + " auto-authenticated (session, IP check disabled).");
                return;
            }
        }

        // No valid session — freeze and show GUI immediately
        pendingAuth.add(uuid);

        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);

        player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <gray>Открываем окно авторизации...</gray>"));
        if (registered) {
            AuthGUI.openLogin(player);
        } else {
            AuthGUI.openRegister(player);
        }

        // ⏱ Start login timeout kick timer
        startLoginTimeout(player);
    }

    // =========================
    // ⏱ COOLDOWN DURATION
    // =========================
    private long getRequestCooldownMs() {
        return getConfigInt("auth.request_cooldown_seconds", 5) * 1000L;
    }

    // =========================
    // ⏱ CHECK RATE LIMIT
    // =========================
    public boolean checkRequestCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(uuid);
        long cooldownMs = getRequestCooldownMs();

        if (lastRequest != null && (now - lastRequest) < cooldownMs) {
            long remaining = ((cooldownMs - (now - lastRequest)) / 1000) + 1;
            player.sendMessage(MessageUtil.parse("<red>❌ Подождите </red><yellow>" + remaining + "</yellow> <red>сек. перед следующим запросом!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return false;
        }

        requestCooldowns.put(uuid, now);
        return true;
    }

    // =========================
    // HANDLE PASSWORD SUBMIT
    // =========================
    public void handlePasswordSubmit(Player player, String password) {
        UUID uuid = player.getUniqueId();

        if (authenticated.contains(uuid)) return;

        if (!checkRequestCooldown(player)) return;

        String playerIp = getPlayerIp(player);

        if (AuthDatabase.isRegistered(uuid)) {
            // LOGIN
            if (AuthDatabase.checkPassword(uuid, password)) {
                if (isIpCheckEnabled()) {
                    String storedIp = AuthDatabase.getLastIp(uuid);
                    if (!storedIp.isEmpty() && !storedIp.equals(playerIp)) {
                        Main.getInstance().getLogger().info(
                                "[Auth] Player " + player.getName() + " login IP changed: " + storedIp + " → " + playerIp + " — updating IP.");
                        AuthDatabase.updateLastIp(uuid, playerIp);
                    }
                }

                AuthDatabase.updateLastLogin(uuid);
                wrongAttempts.remove(uuid);
                authenticatePlayer(player, "<green>✅</green> <white>Вы успешно вошли на сервер!</white>");
            } else {
                // 🔢 Неверный пароль — увеличиваем счётчик
                int attempts = wrongAttempts.getOrDefault(uuid, 0) + 1;
                wrongAttempts.put(uuid, attempts);
                int maxWrong = getConfigInt("auth.max_wrong_attempts", 5);
                int remaining = maxWrong - attempts;

                if (attempts >= maxWrong) {
                    cancelLoginTimeout(uuid);
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

        } else {
            // REGISTER
            int minLen = getConfigInt("auth.min_password_length", 8);
            int maxLen = getConfigInt("auth.max_password_length", 32);
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

            if (!playerIp.isEmpty()) {
                int maxAccounts = getMaxAccountsPerIp();
                if (maxAccounts > 0) {
                    int currentCount = AuthDatabase.countAccountsByIp(playerIp);
                    if (currentCount >= maxAccounts) {
                        String msg = getConfigMessage("max_accounts_per_ip",
                                "<red>❌ С вашего IP-адреса уже зарегистрировано <yellow>{count}</yellow> аккаунтов!</red>\n<white>Максимум: <yellow>{limit}</yellow> аккаунтов на один IP.</white>")
                                .replace("{count}", String.valueOf(currentCount))
                                .replace("{limit}", String.valueOf(maxAccounts));
                        player.sendMessage("");
                        player.sendMessage(MessageUtil.parse(msg));
                        player.sendMessage("");
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
                        reopenAfterDelay(player);
                        return;
                    }
                }
            }

            AuthDatabase.register(uuid, password, playerIp);
            wrongAttempts.remove(uuid);
            authenticatePlayer(player, "<green>✅</green> <white>Регистрация прошла успешно!</white>");
        }
    }

    // =========================
    // AUTHENTICATE PLAYER
    // =========================
    private void authenticatePlayer(Player player, String message) {
        UUID uuid = player.getUniqueId();
        authenticated.add(uuid);
        pendingAuth.remove(uuid);

        savePlayerIp(player);

        // ⏱ Cancel login timeout + reset wrong attempts
        cancelLoginTimeout(uuid);
        wrongAttempts.remove(uuid);

        AuthGUI.removeAuthItemsFromPlayer(player);
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(message));
        player.sendMessage(MessageUtil.parse("<gray>Приятной игры! Сессия активна 1 час.</gray>"));
        player.sendMessage("");

        Main.getInstance().getLogger().info("[Auth] Player " + player.getName() + " authenticated.");
    }

    // =========================
    // REMOVE PLAYER
    // =========================
    public void removePlayer(UUID uuid) {
        authenticated.remove(uuid);
        pendingAuth.remove(uuid);
        requestCooldowns.remove(uuid);
        cancelLoginTimeout(uuid);
        wrongAttempts.remove(uuid);
    }

    // =========================
    // FORCE LOGIN (admin command)
    // =========================
    public boolean forceLogin(UUID uuid) {
        if (!AuthDatabase.isRegistered(uuid)) return false;

        authenticated.add(uuid);
        pendingAuth.remove(uuid);
        AuthDatabase.updateLastLogin(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.closeInventory();
            player.setGameMode(GameMode.SURVIVAL);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setInvulnerable(false);
            player.sendMessage(MessageUtil.parse("<green>✅</green> <white>Вы были принудительно авторизованы администратором!</white>"));
        }

        return true;
    }

    // =========================
    // RESET AUTH (admin command)
    // =========================
    public boolean resetAuth(UUID uuid) {
        if (!AuthDatabase.isRegistered(uuid)) return false;

        authenticated.remove(uuid);
        pendingAuth.remove(uuid);
        AuthDatabase.deleteRegistration(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.kickPlayer(
                    "§6✦ MC-Plugin\n" +
                    "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "§c❌ Ваша регистрация была удалена администратором!\n" +
                    "§7При следующем входе нужно будет\n" +
                    "§7зарегистрироваться заново.\n\n" +
                    "§7━━━━━━━━━━━━━━━━━━━━━"
            );
        }

        return true;
    }

    // =========================
    // CHANGE PASSWORD (admin command)
    // =========================
    public boolean changePassword(UUID uuid, String newPassword) {
        if (!AuthDatabase.isRegistered(uuid)) return false;
        int minLen = getConfigInt("auth.min_password_length", 8);
        int maxLen = getConfigInt("auth.max_password_length", 32);
        if (newPassword.length() < minLen || newPassword.length() > maxLen) return false;

        boolean updated = AuthDatabase.changePassword(uuid, newPassword);
        if (updated) {
            authenticated.remove(uuid);
            pendingAuth.remove(uuid);
        }
        return updated;
    }

    // =========================
    // DELETE SESSION (admin command)
    // =========================
    public boolean deleteSession(UUID uuid) {
        if (!AuthDatabase.isRegistered(uuid)) return false;

        authenticated.remove(uuid);
        pendingAuth.remove(uuid);
        AuthDatabase.resetAuth(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.kickPlayer(
                    "§6✦ MC-Plugin\n" +
                    "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "§c❌ Ваша сессия была сброшена администратором!\n" +
                    "§7При следующем входе нужно будет\n" +
                    "§7снова ввести пароль для входа.\n\n" +
                    "§7━━━━━━━━━━━━━━━━━━━━━"
            );
        }

        return true;
    }

    // =========================
    // SELF CHANGE PASSWORD
    // =========================
    public void handleSelfChangePassword(Player player, String newPassword) {
        UUID uuid = player.getUniqueId();

        int minLen = getConfigInt("auth.min_password_length", 8);
        int maxLen = getConfigInt("auth.max_password_length", 32);
        if (newPassword.length() < minLen) {
            player.sendMessage("§c❌ Пароль должен быть не менее " + minLen + " символов!");
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage("§c❌ Пароль не должен превышать " + maxLen + " символов!");
            return;
        }

        AuthDatabase.changePasswordSelf(uuid, newPassword);

        authenticated.add(uuid);
        pendingAuth.remove(uuid);
        savePlayerIp(player);

        AuthGUI.removeAuthItemsFromPlayer(player);
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);

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

        if (!authenticated.contains(uuid)) return false;
        if (!AuthDatabase.isRegistered(uuid)) return false;

        if (!checkRequestCooldown(player)) return false;

        if (!AuthDatabase.checkPassword(uuid, password)) return false;

        authenticated.remove(uuid);
        pendingAuth.remove(uuid);
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
    // GET PLAYER IP ADDRESS
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

    // =========================
    // ⏱ LOGIN TIMEOUT KICK
    // =========================
    private void startLoginTimeout(Player player) {
        UUID uuid = player.getUniqueId();
        cancelLoginTimeout(uuid);

        int timeoutSec = getConfigInt("auth.login_timeout_seconds", 60);
        long timeoutTicks = timeoutSec * 20L;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (authenticated.contains(uuid)) return;

                player.kickPlayer(
                        "§6✦ MC-Plugin\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "§c⏱ Время на авторизацию истекло!\n" +
                        "§7Вы не вошли в аккаунт за §c" + timeoutSec + "§7 сек.\n\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━"
                );
            }
        };

        task.runTaskLater(Main.getInstance(), timeoutTicks);
        loginTimeoutTasks.put(uuid, task);
    }

    private void cancelLoginTimeout(UUID uuid) {
        BukkitRunnable task = loginTimeoutTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================
    // RE-OPEN GUI AFTER DELAY
    // =========================
    public void reopenAfterDelay(Player player) {
        if (authenticated.contains(player.getUniqueId())) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (authenticated.contains(player.getUniqueId())) return;

                if (AuthDatabase.isRegistered(player.getUniqueId())) {
                    AuthGUI.openLogin(player);
                } else {
                    AuthGUI.openRegister(player);
                }
            }
        }.runTaskLater(Main.getInstance(), 5L);
    }
}
