package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SudoManager — «sudo-режим» как на GitHub: опасные действия требуют
 * ввода sudo-пароля, после чего включается сессия (по умолчанию 15 минут),
 * в течение которой опасные команды проходят без подтверждения.
 * <p>
 * Механика:
 * <ul>
 *   <li>Игроки с правом {@code ui.sudo} подпадают под sudo.</li>
 *   <li>Опасные команды задаются в config.yml {@code features.sudo.dangerous_commands}.</li>
 *   <li>Если у игрока нет sudo-пароля — открывается «второе окно авторизации»
 *       (диалог) с просьбой задать пароль и не забывать его.</li>
 *   <li>Кулдаун между попытками — 10 секунд, число попыток не ограничено.</li>
 *   <li>При неверном пароле предлагается сброс: запрос уходит в консоль,
 *       консоль подтверждает через {@code /ui sudo confirmreset <player>}.</li>
 * </ul>
 */
public class SudoManager {

    private static SudoManager instance;

    /** Активные sudo-сессии: UUID → время истечения (ms). */
    private final Map<UUID, Long> sudoExpiry = new ConcurrentHashMap<>();
    /** Кулдаун между попытками пароля: UUID → когда можно снова (ms). */
    private final Map<UUID, Long> attemptCooldowns = new ConcurrentHashMap<>();
    /** Заблокированные команды, ожидающие sudo: UUID → полная команда. */
    private final Map<UUID, String> pendingCommands = new ConcurrentHashMap<>();
    /** Запросы на сброс пароля: UUID → время истечения (ms). */
    private final Map<UUID, Long> resetRequests = new ConcurrentHashMap<>();

    private SudoManager() {}

    public static void init() {
        if (instance == null) {
            instance = new SudoManager();
        }
        SudoDatabase.initTable();
        ConsoleLogger.info("[Sudo] SudoManager initialized (session=" + getSessionMinutes() + "min, cooldown="
                + getAttemptCooldownSeconds() + "s).");
    }

    public static SudoManager getInstance() {
        return instance;
    }

    // =========================
    // CONFIG
    // =========================
    public static boolean isEnabled() {
        return Main.getInstance().getConfig().getBoolean("features.sudo.enabled", true);
    }

    public static int getSessionMinutes() {
        return Math.max(Main.getInstance().getConfig().getInt("features.sudo.session_minutes", 15), 1);
    }

    public static int getAttemptCooldownSeconds() {
        return Math.max(Main.getInstance().getConfig().getInt("features.sudo.attempt_cooldown_seconds", 10), 1);
    }

    /** Список опасных команд (префиксы, без слэша). */
    public static List<String> getDangerousCommands() {
        return Main.getInstance().getConfig().getStringList("features.sudo.dangerous_commands");
    }

    // =========================
    // SUDO SESSION
    // =========================
    public boolean isSudoActive(UUID uuid) {
        Long exp = sudoExpiry.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            sudoExpiry.remove(uuid);
            return false;
        }
        return true;
    }

    public void startSudoSession(UUID uuid) {
        sudoExpiry.put(uuid, System.currentTimeMillis() + getSessionMinutes() * 60000L);
    }

    public void endSudoSession(UUID uuid) {
        sudoExpiry.remove(uuid);
        discardPending(uuid);
    }

    public long getRemainingSeconds(UUID uuid) {
        Long exp = sudoExpiry.get(uuid);
        if (exp == null) return 0;
        return Math.max(0, (exp - System.currentTimeMillis()) / 1000);
    }

    // =========================
    // DANGEROUS COMMAND MATCHING
    // =========================

    /**
     * Проверяет, является ли команда опасной.
     * Нормализация: слэш убирается, регистр нижний, пробелы схлопываются.
     * Совпадение по префиксу (команда или её подкоманда).
     */
    public boolean isDangerous(String message) {
        String normalized = normalize(message);
        for (String prefix : getDangerousCommands()) {
            String p = normalize(prefix);
            if (p.isEmpty()) continue;
            // Префикс: /lp → lp, lp sync, lp group ...
            if (normalized.equals(p) || normalized.startsWith(p + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        String t = s.trim();
        if (t.startsWith("/")) t = t.substring(1);
        return t.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    // =========================
    // INTERCEPT (вызывается из SudoCommandInterceptor)
    // =========================

    /**
     * Блокирует опасную команду: сохраняет её, открывает диалог sudo.
     *
     * @return true — команду нужно отменить (event.setCancelled)
     */
    public boolean intercept(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (isSudoActive(uuid)) {
            return false; // sudo уже активна — пропускаем
        }

        // Сохраняем команду, чтобы перевыполнить её после успешного sudo
        pendingCommands.put(uuid, message);

        player.sendMessage(MessageUtil.parse(
                "<dark_red>⚠</dark_red> <red>Dangerous action requires sudo mode!</red>"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.9f);

        boolean registered = SudoDatabase.isRegistered(uuid);
        SudoDialogScreen.open(player, registered);
        return true;
    }

    // =========================
    // PASSWORD SUBMIT (диалог)
    // =========================
    public void handlePasswordSubmit(Player player, String password) {
        UUID uuid = player.getUniqueId();

        // Кулдаун между попытками (10 сек, попытки не ограничены)
        long now = System.currentTimeMillis();
        Long cd = attemptCooldowns.get(uuid);
        if (cd != null && now < cd) {
            long remaining = (cd - now) / 1000 + 1;
            SudoDialogScreen.close(player);
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Too fast! Try again in </red><yellow>" + remaining + "</yellow><red>s.</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            // Переоткрываем после окончания кулдауна — один диалог на 10 сек
            reopenDialogLater(player, remaining);
            return;
        }

        // Argon2id на async thread — не фризим сервер
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                boolean registered = SudoDatabase.isRegistered(uuid);
                boolean ok;
                if (registered) {
                    ok = SudoDatabase.checkPassword(uuid, password);
                } else {
                    // Регистрируем первый пароль (INSERT OR REPLACE — можно и перезадать)
                    ok = SudoDatabase.register(uuid, password);
                }

                final boolean success = ok;
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    SudoDialogScreen.close(player);
                    if (success) {
                        onSudoSuccess(player, !registered);
                    } else {
                        onWrongPassword(player);
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.parse("<red>❌ Sudo error! Please try again.</red>"));
                        reopenDialog(player);
                    }
                });
                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[Sudo] Async error", e);
            }
        });
    }

    private void onSudoSuccess(Player player, boolean createdNew) {
        UUID uuid = player.getUniqueId();
        startSudoSession(uuid);
        attemptCooldowns.remove(uuid);

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(createdNew
                ? "<green>✔</green> <white>Sudo password set! Sudo mode active for </white><yellow>"
                        + getSessionMinutes() + "</yellow><white> min.</white>"
                : "<green>✔</green> <white>Sudo mode activated for </white><yellow>"
                        + getSessionMinutes() + "</yellow><white> min.</white>"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);

        // Перевыполняем заблокированную команду
        String pending = pendingCommands.remove(uuid);
        if (pending != null && !pending.isEmpty()) {
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    player.performCommand(pending.substring(1)); // без "/"
                }
            });
        }

        ConsoleLogger.info("[Sudo] " + player.getName() + " entered sudo mode (created=" + createdNew + ").");
    }

    private void onWrongPassword(Player player) {
        UUID uuid = player.getUniqueId();
        attemptCooldowns.put(uuid, System.currentTimeMillis() + getAttemptCooldownSeconds() * 1000L);

        player.sendMessage(MessageUtil.parse(
                "<red>❌ Incorrect sudo password!</red>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Try again in </gray><yellow>" + getAttemptCooldownSeconds()
                        + "</yellow><gray>s. </gray>"
                        + "<click:run_command:'/ui sudo reset'><hover:show_text:'<gray>Reset sudo password</gray>'><yellow>[Reset password]</yellow></hover></click>"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.7f);
        ConsoleLogger.info("[Sudo] Wrong sudo password attempt by " + player.getName());

        // Автоматически открываем диалог снова после кулдауна — «ещё одна попытка через 10 сек»
        reopenDialogLater(player, getAttemptCooldownSeconds());
    }

    private void reopenDialog(Player player) {
        reopenDialogLater(player, 1);
    }

    private void reopenDialogLater(Player player, long seconds) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline() && !isSudoActive(player.getUniqueId())) {
                SudoDialogScreen.open(player, SudoDatabase.isRegistered(player.getUniqueId()));
            }
        }, seconds * 20L);
    }

    /**
     * Отменяет ожидающую sudo-команду (при отмене диалога или выходе из sudo).
     */
    public void discardPending(UUID uuid) {
        pendingCommands.remove(uuid);
    }

    // =========================
    // RESET FLOW (сброс пароля через консоль)
    // =========================

    /**
     * Игрок запрашивает сброс sudo-пароля.
     * В консоль уходит запрос на подтверждение: /ui sudo confirmreset <player>.
     */
    public void requestReset(Player player) {
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long existing = resetRequests.get(uuid);
        if (existing != null && now < existing) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ A reset request is already pending. Wait for console confirmation.</red>"));
            return;
        }
        if (!SudoDatabase.isRegistered(uuid)) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have a sudo password set.</red>"));
            return;
        }

        resetRequests.put(uuid, now + 60_000L); // запрос валиден 60 сек
        player.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Reset request sent to console. Wait for confirmation.</white>"));

        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(
                "<dark_red>⚠</dark_red> <red>Player </red><yellow>" + player.getName()
                        + "</yellow><red> requests a sudo password reset.</red>"));
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(
                "<gray>Confirm: </gray><white>/ui sudo confirmreset " + player.getName() + "</white>"));
        ConsoleLogger.warn("[Sudo] Reset requested by " + player.getName()
                + " — confirm via /ui sudo confirmreset " + player.getName());
    }

    /**
     * Консоль подтверждает сброс пароля. Игрок задаст новый при следующем sudo.
     */
    public boolean confirmReset(String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        UUID uuid = target != null ? target.getUniqueId() : getOfflineUuid(playerName);

        if (!SudoDatabase.isRegistered(uuid)) {
            Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(
                    "<red>❌ Player </red><yellow>" + playerName + "</yellow><red> has no sudo password.</red>"));
            return true;
        }

        SudoDatabase.deleteRegistration(uuid);
        resetRequests.remove(uuid);
        sudoExpiry.remove(uuid);
        pendingCommands.remove(uuid);

        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Sudo password for </white><yellow>" + playerName
                        + "</yellow><white> has been reset.</white>"));
        ConsoleLogger.info("[Sudo] Password reset for " + playerName + " (confirmed by console).");

        if (target != null && target.isOnline()) {
            target.sendMessage(MessageUtil.parse(
                    "<yellow>✦</yellow> <white>Your sudo password was reset by console. Set a new one next time.</white>"));
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private static UUID getOfflineUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    // =========================
    // CLEANUP
    // =========================
    public void removePlayer(UUID uuid) {
        sudoExpiry.remove(uuid);
        attemptCooldowns.remove(uuid);
        pendingCommands.remove(uuid);
        resetRequests.remove(uuid);
    }
}
