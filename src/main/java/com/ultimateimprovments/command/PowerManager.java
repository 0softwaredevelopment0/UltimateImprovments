package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import com.ultimateimprovments.util.Broadcast;
import com.ultimateimprovments.util.ConsoleLogger;
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
    private String actionbarFormat = "<red>⚡</red> <white>Сервер %action% через</white> <yellow>%seconds%</yellow> <white>сек</white>";

    // Config: bossbar
    private boolean bossbarEnabled = true;
    private String bossbarColor = "RED";
    private String bossbarStyle = "SOLID";
    private String bossbarText = "<red>⚡ Сервер %action% через</red> <yellow>%seconds%</yellow> <red>сек</red>";

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
                "<red>⚡</red> <white>Server %action% in</white> <yellow>%seconds%</yellow> <white>seconds</white>");

        // BossBar
        bossbarEnabled = cfg.getBoolean("power.bossbar.enabled", true);
        bossbarColor = cfg.getString("power.bossbar.color", "RED");
        bossbarStyle = cfg.getString("power.bossbar.style", "SOLID");
        bossbarText = cfg.getString("power.bossbar.text", "<red>⚡ Server %action% in</red> <yellow>%seconds%</yellow> <red>sec</red>");
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
                cancelRequest("Request timeout");
            }
        };
        timeoutTask.runTaskLater(Main.getInstance(), 20L * requestTimeout);
    }

    /**
     * Confirms the request and starts the countdown with:
     * - BossBar (decreasing bar)
     * - ActionBar (remaining seconds)
     * - Smooth beep acceleration (rate + pitch grow continuously)
     * - Chat messages
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

        String actionMsg = type == RequestType.STOP
                ? MessagesManager.getString("power.action_stop", "shutting down")
                : MessagesManager.getString("power.action_restart", "restarting");
        String action = type == RequestType.STOP
                ? MessagesManager.getString("power.action_stop_title", "Shutdown")
                : MessagesManager.getString("power.action_restart_title", "Restart");
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
        Broadcast.send(MessagesManager.getString("power.countdown_broadcast",
                "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>%action% in</red> <white>%seconds%</white> <red>seconds!</red>")
                .replace("%action%", action)
                .replace("%seconds%", String.valueOf(duration)));
        playBeepToAll(calcPitch(0.0));

        // --- Repeating countdown task (every tick — smooth acceleration) ---
        new BukkitRunnable() {
            int tick = 0;                  // how many ticks passed (0..totalTicks)
            int beepCounter = 1;           // counter for the interval between beeps
            int lastDisplaySecond = -1;     // last second when the display was updated

            @Override
            public void run() {
                // How many seconds remain
                int currentSecond = duration - (tick / 20);

                // --- Last tick — execute ---
                if (currentSecond < 0) {
                    try {
                        Broadcast.send(MessagesManager.getString("power.executing",
                                "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server %action%...</red>")
                                .replace("%action%", actionMsg));
                        playBeepToAll(countdownSoundPitchMax);

                        // "We're shutting down!" — award to everyone online before the power action
                        com.ultimateimprovments.mechanics.features.world.ShutdownListener.grantToAllOnline();

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

                // --- Updates once per second (chat + actionbar + bossbar title) ---
                if (currentSecond != lastDisplaySecond) {
                    lastDisplaySecond = currentSecond;

                    // Chat (last 5 seconds)
                    if (currentSecond <= 5 && currentSecond > 0) {
                        String secWord;
                        if (currentSecond == 1) secWord = "second";
                        else secWord = "seconds";
                        Broadcast.send(MessagesManager.getString("power.countdown_seconds",
                                "<red>Server %action% in</red> <white>%seconds%</white> <red>%unit%...</red>")
                                .replace("%action%", actionMsg)
                                .replace("%seconds%", String.valueOf(currentSecond))
                                .replace("%unit%", secWord));
                    }

                    // ActionBar
                    if (actionbarEnabled) {
                        String barText = actionbarFormat
                                .replace("%action%", actionMsg)
                                .replace("%seconds%", String.valueOf(currentSecond));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendActionBar(MessageUtil.parse(barText));
                        }
                    }

                    // BossBar title (only on second change — no need for extra packets)
                    if (bossBar != null) {
                        String barTitle = bossbarText
                                .replace("%action%", actionMsg)
                                .replace("%seconds%", String.valueOf(Math.max(0, currentSecond)));
                        bossBar.setTitle(MessageUtil.legacy(barTitle));
                    }
                }

                // --- BossBar progress: smooth update EVERY TICK ---
                if (bossBar != null) {
                    // Smooth progress: from 1.0 to 0.0
                    double bossProgress = (double) (totalTicks - tick) / totalTicks;
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, bossProgress)));

                    // Add new players (e.g. those who joined during the countdown)
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!bossBar.getPlayers().contains(p)) {
                            bossBar.addPlayer(p);
                        }
                    }
                }

                // --- Smooth beep acceleration ---
                if (countdownSoundEnabled) {
                    // Progress from 0.0 to 1.0 at the tick level (continuous)
                    double progress = (double) tick / totalTicks;

                    // Current interval between beeps (smoothly from 20 to minInterval)
                    int interval;
                    if (beepSpeedupEnabled) {
                        int maxInterval = 20;
                        int minInterval = Math.max(2, 20 / Math.max(1, beepMaxPerSecond));
                        interval = (int) (maxInterval - (maxInterval - minInterval) * Math.min(1.0, progress));
                        interval = Math.max(minInterval, interval);
                    } else {
                        interval = 20;
                    }

                    // Beep when the counter reaches the interval
                    if (beepCounter >= interval) {
                        playBeepToAll(calcPitch(progress));
                        beepCounter = 0;
                    }
                    beepCounter++;
                }

                tick++;
            }
        }.runTaskTimer(Main.getInstance(), 0L, 1L); // every tick!

        return true;
    }

    /**
     * Computes the pitch for smooth acceleration by progress 0.0..1.0.
     */
    private float calcPitch(double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        return (float) (countdownSoundPitchBase
                + (countdownSoundPitchMax - countdownSoundPitchBase) * progress);
    }

    /**
     * Creates and returns the BossBar for the countdown.
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
        String text = bossbarText.replace("%action%", actionMsg);

        return Bukkit.createBossBar(MessageUtil.legacy(text), color, style);
    }

    /**
     * Plays the "beep" sound to all players with the given pitch.
     */
    private void playBeepToAll(float pitch) {
        if (!countdownSoundEnabled) return;

        Sound sound = SoundUtil.getSound(countdownSoundName);
        if (sound == null) {
            ConsoleLogger.warn("[POWER] Unknown sound: " + countdownSoundName);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, countdownSoundVolume, pitch);
        }
    }

    /**
     * Cancels the active request (by a player command or the console).
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

        String action = type == RequestType.STOP
                ? MessagesManager.getString("power.action_stop_title", "Shutdown")
                : MessagesManager.getString("power.action_restart_title", "Restart");

        // Notify the requesting player
        if (requesterId != null) {
            Player player = Bukkit.getPlayer(requesterId);
            if (player != null && player.isOnline()) {
                String msg = MessagesManager.getString("power.cancelled_by_player",
                        "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server %action% was cancelled</red> <white>%by_player%</white><red>.</red>")
                        .replace("%action%", action)
                        .replace("%by_player%", cancelerName != null && !cancelerName.equalsIgnoreCase(requester) ? cancelerName : "");
                player.sendMessage(MessageUtil.legacy(msg));
            }
        }

        // Notify console
        String consoleMsg = MessagesManager.getString("power.cancelled_console",
                "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server %action% cancelled%by%%from%.</red>")
                .replace("%action%", action)
                .replace("%by%", cancelerName != null ? " (" + cancelerName + ")" : "")
                .replace("%from%", requester != null ? ". Request from " + requester : "");
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(consoleMsg));

        return action;
    }

    /**
     * Automatic request cancellation (by timeout).
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

        String action = type == RequestType.STOP
                ? MessagesManager.getString("power.action_stop_title", "Shutdown")
                : MessagesManager.getString("power.action_restart_title", "Restart");

        // Try to message the requesting player
        if (requesterId != null) {
            Player player = Bukkit.getPlayer(requesterId);
            if (player != null && player.isOnline()) {
                String msg = MessagesManager.getString("power.cancelled_auto",
                        "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server %action% was automatically cancelled: %reason%</red>")
                        .replace("%action%", action)
                        .replace("%reason%", reason);
                player.sendMessage(MessageUtil.legacy(msg));
            }
        }

        String consoleMsg = MessagesManager.getString("power.cancelled_console",
                "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server %action% cancelled%by%%from%.</red>")
                .replace("%action%", action)
                .replace("%by%", "")
                .replace("%from%", requester != null ? " (request from " + requester + ")" : "");
        Bukkit.getConsoleSender().sendMessage(MessageUtil.parse(consoleMsg));
    }

    /**
     * Immediate execution (without confirmation, for the console).
     */
    public void executeDirect(boolean isRestart) {
        if (isRestart) {
            Broadcast.send(MessagesManager.getString("power.direct_restart",
                    "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server restarting (console command)...</red>"));
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                // "We're shutting down!" — award to everyone online before the power action
                com.ultimateimprovments.mechanics.features.world.ShutdownListener.grantToAllOnline();
                Bukkit.getServer().restart();
            }, 20);
        } else {
            Broadcast.send(MessagesManager.getString("power.direct_stop",
                    "<dark_gray>[<dark_red>⚠</dark_red>]</dark_gray> <red>Server shutting down (console command)...</red>"));
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                // "We're shutting down!" — award to everyone online before the power action
                com.ultimateimprovments.mechanics.features.world.ShutdownListener.grantToAllOnline();
                Bukkit.getServer().shutdown();
            }, 20);
        }
    }
}
