package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PowerManager {

    private static PowerManager instance;

    public enum RequestType {
        STOP,
        RESTART
    }

    private RequestType currentRequest;
    private UUID requesterUuid;
    private String requesterName;
    private BukkitRunnable timeoutTask;

    // Config: timeouts
    private int requestTimeout = 30;
    private int countdownDuration = 10;

    // Config: sound
    private boolean countdownSoundEnabled = true;
    private String countdownSoundName = "BLOCK_NOTE_BLOCK_PLING";
    private float countdownSoundVolume = 1.0f;
    private float countdownSoundPitchBase = 1.2f;
    private float countdownSoundPitchMax = 2.0f;

    // Config: beep rate acceleration
    private boolean beepSpeedupEnabled = true;
    private int beepMinPerSecond = 1;
    private int beepMaxPerSecond = 6;

    // Config: actionbar
    private boolean actionbarEnabled = true;
    private String actionbarFormat = "§c⚡ §fСервер {action} через §e{seconds}§f сек";

    // Config: bossbar
    private boolean bossbarEnabled = true;
    private String bossbarColor = "RED";
    private String bossbarStyle = "SOLID";
    private String bossbarText = "§c⚡ Сервер {action} через §e{seconds}§c сек";

    public static void init() {
        instance = new PowerManager();
        instance.loadConfig();
    }

    public static PowerManager getInstance() {
        return instance;
    }

    public static void reloadConfig() {
        if (instance != null) {
            instance.loadConfig();
        }
    }

    public void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        requestTimeout = cfg.getInt("power.request_timeout", 30);
        countdownDuration = Math.max(3, cfg.getInt("power.countdown_duration", 10));

        // Sound
        countdownSoundEnabled = cfg.getBoolean("power.countdown_sound.enabled", true);
        countdownSoundName = cfg.getString("power.countdown_sound.name", "BLOCK_NOTE_BLOCK_PLING");
        countdownSoundVolume = (float) cfg.getDouble("power.countdown_sound.volume", 1.0);
        countdownSoundPitchBase = (float) cfg.getDouble("power.countdown_sound.pitch_base", 1.2);
        countdownSoundPitchMax = (float) cfg.getDouble("power.countdown_sound.pitch_max", 2.0);

        // Beep rate acceleration
        beepSpeedupEnabled = cfg.getBoolean("power.countdown_sound.beep_speedup.enabled", true);
        beepMinPerSecond = Math.max(1, cfg.getInt("power.countdown_sound.beep_speedup.min_per_second", 1));
        beepMaxPerSecond = Math.max(beepMinPerSecond, cfg.getInt("power.countdown_sound.beep_speedup.max_per_second", 6));

        // ActionBar
        actionbarEnabled = cfg.getBoolean("power.actionbar.enabled", true);
        actionbarFormat = cfg.getString("power.actionbar.format",
                "§c⚡ §fСервер {action} через §e{seconds}§f сек");

        // BossBar
        bossbarEnabled = cfg.getBoolean("power.bossbar.enabled", true);
        bossbarColor = cfg.getString("power.bossbar.color", "RED");
        bossbarStyle = cfg.getString("power.bossbar.style", "SOLID");
        bossbarText = cfg.getString("power.bossbar.text", "§c⚡ Сервер {action}...");
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getCountdownDuration() {
        return countdownDuration;
    }

    public boolean hasPendingRequest() {
        return currentRequest != null;
    }

    public RequestType getCurrentRequestType() {
        return currentRequest;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public UUID getRequesterUuid() {
        return requesterUuid;
    }

    public void requestStop(String playerName, UUID playerUuid) {
        this.currentRequest = RequestType.STOP;
        this.requesterName = playerName;
        this.requesterUuid = playerUuid;
        startTimeout();
    }

    public void requestRestart(String playerName, UUID playerUuid) {
        this.currentRequest = RequestType.RESTART;
        this.requesterName = playerName;
        this.requesterUuid = playerUuid;
        startTimeout();
    }

    private void startTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                cancelRequest("Превышено время ожидания");
            }
        };
        timeoutTask.runTaskLater(Main.getInstance(), 20L * requestTimeout);
    }

    /**
     * Подтверждает запрос и запускает обратный отсчёт с:
     * - BossBar (убывающая полоса)
     * - ActionBar (оставшиеся секунды)
     * - Плавное ускорение пиканья (частота + питч растут непрерывно)
     * - Сообщения в чат
     */
    public boolean confirmRequest() {
        if (currentRequest == null) return false;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        RequestType type = currentRequest;
        currentRequest = null;
        requesterName = null;
        requesterUuid = null;

        String actionMsg = type == RequestType.STOP ? "выключается" : "перезагружается";
        String action = type == RequestType.STOP ? "Выключение" : "Перезапуск";
        int duration = countdownDuration;
        int totalTicks = duration * 20;

        // --- BossBar ---
        BossBar bossBar = createBossBar(actionMsg);
        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                bossBar.addPlayer(p);
            }
        }

        // --- Initial broadcast ---
        Bukkit.broadcastMessage("§8[§4⚠§8] §c" + action + " сервера через §f" + duration + " §cсекунд!");
        playBeepToAll(calcPitch(0.0));

        // --- Repeating countdown task (каждый тик — плавное ускорение) ---
        new BukkitRunnable() {
            int tick = 0;                  // сколько тиков прошло (0..totalTicks)
            int beepCounter = 1;           // счётчик для интервала между пиками
            int lastDisplaySecond = -1;     // последняя секунда, когда обновили дисплей

            @Override
            public void run() {
                // Сколько секунд осталось
                int currentSecond = duration - (tick / 20);

                // --- Последний тик — выполнить ---
                if (currentSecond < 0) {
                    try {
                        Bukkit.broadcastMessage("§8[§4⚠§8] §cСервер " + actionMsg + "...");
                        playBeepToAll(countdownSoundPitchMax);

                        if (type == RequestType.STOP) {
                            Bukkit.getServer().shutdown();
                        } else {
                            Bukkit.getServer().restart();
                        }
                    } finally {
                        if (bossBar != null) {
                            bossBar.removeAll();
                        }
                        cancel();
                    }
                    return;
                }

                // --- Обновления раз в секунду (чат + actionbar + bossbar title) ---
                if (currentSecond != lastDisplaySecond) {
                    lastDisplaySecond = currentSecond;

                    // Chat (последние 5 секунд)
                    if (currentSecond <= 5 && currentSecond > 0) {
                        String secWord;
                        if (currentSecond == 1) secWord = "секунду";
                        else if (currentSecond >= 2 && currentSecond <= 4) secWord = "секунды";
                        else secWord = "секунд";
                        Bukkit.broadcastMessage("§8[§4⚠§8] §cСервер " + actionMsg
                                + " через §f" + currentSecond + " §c" + secWord + "...");
                    }

                    // ActionBar
                    if (actionbarEnabled) {
                        String barText = actionbarFormat
                                .replace("{action}", actionMsg)
                                .replace("{seconds}", String.valueOf(currentSecond));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendActionBar(barText);
                        }
                    }

                    // BossBar title (только при смене секунды — лишние пакеты ни к чему)
                    if (bossBar != null) {
                        String barTitle = bossbarText
                                .replace("{action}", actionMsg)
                                .replace("{seconds}", String.valueOf(Math.max(0, currentSecond)));
                        bossBar.setTitle(barTitle);
                    }
                }

                // --- BossBar progress: плавное обновление КАЖДЫЙ ТИК ---
                if (bossBar != null) {
                    // Плавный прогресс: от 1.0 до 0.0
                    double bossProgress = (double) (totalTicks - tick) / totalTicks;
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, bossProgress)));

                    // Добавляем новых игроков (например, зашедших во время отсчёта)
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!bossBar.getPlayers().contains(p)) {
                            bossBar.addPlayer(p);
                        }
                    }
                }

                // --- Плавное ускорение пиканья ---
                if (countdownSoundEnabled) {
                    // Прогресс от 0.0 до 1.0 на уровне тиков (непрерывно)
                    double progress = (double) tick / totalTicks;

                    // Текущий интервал между пиками (плавно от 20 до minInterval)
                    int interval;
                    if (beepSpeedupEnabled) {
                        int maxInterval = 20;
                        int minInterval = Math.max(2, 20 / Math.max(1, beepMaxPerSecond));
                        interval = (int) (maxInterval - (maxInterval - minInterval) * Math.min(1.0, progress));
                        interval = Math.max(minInterval, interval);
                    } else {
                        interval = 20;
                    }

                    // Пик, когда счётчик дорос до интервала
                    if (beepCounter >= interval) {
                        playBeepToAll(calcPitch(progress));
                        beepCounter = 0;
                    }
                    beepCounter++;
                }

                tick++;
            }
        }.runTaskTimer(Main.getInstance(), 0L, 1L); // каждый тик!

        return true;
    }

    /**
     * Вычисляет питч для плавного ускорения по прогрессу 0.0..1.0.
     */
    private float calcPitch(double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        return (float) (countdownSoundPitchBase
                + (countdownSoundPitchMax - countdownSoundPitchBase) * progress);
    }

    /**
     * Создаёт и возвращает BossBar для обратного отсчёта.
     */
    private BossBar createBossBar(String actionMsg) {
        if (!bossbarEnabled) return null;

        BarColor color;
        try {
            color = BarColor.valueOf(bossbarColor);
        } catch (IllegalArgumentException e) {
            color = BarColor.RED;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(bossbarStyle);
        } catch (IllegalArgumentException e) {
            style = BarStyle.SOLID;
        }

        String text = bossbarText.replace("{action}", actionMsg);

        return Bukkit.createBossBar(text, color, style);
    }

    /**
     * Проигрывает звук «пиканья» всем игрокам с указанным питчем.
     */
    private void playBeepToAll(float pitch) {
        if (!countdownSoundEnabled) return;

        Sound sound = SoundUtil.getSound(countdownSoundName);
        if (sound == null) {
            Main.getInstance().getLogger().warning("[POWER] Unknown sound: " + countdownSoundName);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, countdownSoundVolume, pitch);
        }
    }

    /**
     * Отменяет активный запрос (по команде игрока или консоли).
     */
    public String undoRequest(String cancelerName) {
        if (currentRequest == null) return null;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        RequestType type = currentRequest;
        String requester = requesterName;
        UUID requesterId = requesterUuid;

        currentRequest = null;
        requesterName = null;
        requesterUuid = null;

        String action = type == RequestType.STOP ? "Выключение" : "Перезапуск";

        // Notify the requesting player
        if (requesterId != null) {
            Player player = Bukkit.getPlayer(requesterId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§8[§4⚠§8] §c" + action + " сервера был отменён"
                        + (cancelerName != null && !cancelerName.equalsIgnoreCase(requester)
                            ? " игроком §f" + cancelerName
                            : "")
                        + ".");
            }
        }

        // Notify console
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §c" + action + " сервера отменён"
                + (cancelerName != null ? " (" + cancelerName + ")" : "")
                + (requester != null ? ". Запрос был от " + requester : "") + ".");

        return action;
    }

    /**
     * Автоматическая отмена запроса (по таймауту).
     */
    public void cancelRequest(String reason) {
        if (currentRequest == null) return;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        RequestType type = currentRequest;
        String requester = requesterName;
        UUID requesterId = requesterUuid;

        currentRequest = null;
        requesterName = null;
        requesterUuid = null;

        String action = type == RequestType.STOP ? "Выключение" : "Перезапуск";

        // Try to message the requesting player
        if (requesterId != null) {
            Player player = Bukkit.getPlayer(requesterId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§8[§4⚠§8] §c" + action + " сервера был автоматически отменёно: " + reason);
            }
        }

        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §c" + action + " сервера отменёно: " + reason
                + (requester != null ? " (запрос от " + requester + ")" : ""));
    }

    /**
     * Немедленное выполнение (без подтверждения, для консоли).
     */
    public void executeDirect(boolean isRestart) {
        if (isRestart) {
            Bukkit.broadcastMessage("§8[§4⚠§8] §cСервер перезагружается (команда из консоли)...");
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                Bukkit.getServer().restart();
            }, 20);
        } else {
            Bukkit.broadcastMessage("§8[§4⚠§8] §cСервер выключается (команда из консоли)...");
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                Bukkit.getServer().shutdown();
            }, 20);
        }
    }
}
