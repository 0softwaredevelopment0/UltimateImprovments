package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.SoundUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

/**
 * Обрабатывает команду /mp suicide — двухэтапное подтверждение с таймером.
 */
public class SuicideCommand {

    // Игроки, которые подтвердили суицид (ждут таймера)
    private static final HashMap<UUID, Boolean> suicideConfirmed = new HashMap<>();
    private static final HashMap<UUID, BukkitRunnable> suicideTasks = new HashMap<>();
    private static final HashMap<UUID, Long> suicideCooldowns = new HashMap<>();

    public static boolean execute(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration cfg = Main.getInstance().getConfig();

        // =========================
        // НАСТРОЙКИ ИЗ КОНФИГА
        // =========================
        int countdownDuration = cfg.getInt("suicide.countdown_duration", 10);
        int cooldownSeconds = cfg.getInt("suicide.cooldown_seconds", 10);
        int confirmTimeout = cfg.getInt("suicide.confirm_timeout", 30);

        // =========================
        // КУЛДАУН
        // =========================
        if (suicideCooldowns.containsKey(uuid)) {
            long remaining = (suicideCooldowns.get(uuid) - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
            String msg = MessagesManager.getString("suicide.messages.cooldown_message",
                    "<dark_red>❌</dark_red> <red>Please wait</red> <yellow>{seconds}</yellow> <red>seconds before using this again!</red>");
                player.sendMessage(MessageUtil.parse(msg.replace("{seconds}", String.valueOf(remaining))));
                return true;
            } else {
                suicideCooldowns.remove(uuid);
            }
        }

        // =========================
        // ПРОВЕРКА: уже есть активный таймер
        // =========================
        if (suicideTasks.containsKey(uuid)) {
            String msg = MessagesManager.getString("suicide.messages.already_running",
                    "<dark_red>❌</dark_red> <red>You already have a countdown running!</red>");
            player.sendMessage(MessageUtil.parse(msg));
            return true;
        }

        // =========================
        // ЭТАП 1: ПОДТВЕРЖДЕНИЕ
        // =========================
        if (!suicideConfirmed.getOrDefault(uuid, false)) {
            suicideConfirmed.put(uuid, true);

            String warningTitle = MessagesManager.getString("suicide.messages.warning_title", "<dark_red>☠</dark_red> <red>WARNING!</red>");
            String warningText = MessagesManager.getString("suicide.messages.warning_text", "<white>You are about to commit suicide!</white>");
            String warningNoCancel = MessagesManager.getString("suicide.messages.warning_no_cancel", "<red>⚠ This cannot be undone after confirmation!</red>");
            String warningConfirmHint = MessagesManager.getString("suicide.messages.warning_confirm_hint", "<yellow>Type</yellow> <white>/mp suicide</white> <yellow>again to confirm and start the countdown.</yellow>");
            String warningCancelHint = MessagesManager.getString("suicide.messages.warning_cancel_hint", "<gray>If you change your mind — just wait</gray> <yellow>{timeout}</yellow><gray> seconds and the request will expire.</gray>")
                    .replace("{timeout}", String.valueOf(confirmTimeout));

            player.sendMessage("");
            player.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
            player.sendMessage("§8┃ " + MessageUtil.legacy(warningTitle));
            player.sendMessage("§8┃");
            player.sendMessage("§8┃ " + MessageUtil.legacy(warningText));
            player.sendMessage("§8┃");
            player.sendMessage("§8┃ " + MessageUtil.legacy(warningNoCancel));
            player.sendMessage("§8┃");

            TextComponent confirmButton = new TextComponent("§8┃     §2[§a✔ Подтвердить суицид§2]");
            confirmButton.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/mp suicide"
            ));
            confirmButton.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§aНажмите чтобы подтвердить и запустить отсчёт\n")
                            .append("§c⚠ Отмена после нажатия невозможна!")
                            .create()
            ));
            player.spigot().sendMessage(confirmButton);

            player.sendMessage("§8┃   §7или введите §f/mp suicide§7 снова");
            player.sendMessage("§8┃");
            player.sendMessage("§8┃ " + MessageUtil.legacy(warningCancelHint));
            player.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
            player.sendMessage("");

            String warningSound = cfg.getString("suicide.sounds.warning", "BLOCK_NOTE_BLOCK_PLING");
            Sound warningSnd = SoundUtil.getSound(warningSound);
            if (warningSnd != null) {
                player.playSound(player.getLocation(), warningSnd, 1.0f, 0.5f);
            }

            // Автосброс подтверждения
            long confirmTimeoutTicks = confirmTimeout * 20L;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (suicideConfirmed.remove(uuid) != null) {
                        String timeoutMsg = MessagesManager.getString("suicide.messages.timeout_message",
                                "<yellow>ℹ</yellow> <white>Suicide request cancelled (timed out).</white>");
                        player.sendMessage(MessageUtil.parse(timeoutMsg));
                    }
                }
            }.runTaskLater(Main.getInstance(), confirmTimeoutTicks);

            return true;
        }

        // =========================
        // ЭТАП 2: ЗАПУСК ТАЙМЕРА (подтверждено, без отмены)
        // =========================
        suicideConfirmed.remove(uuid);
        startCountdown(player, countdownDuration, cooldownSeconds);
        return true;
    }

    // =========================
    // FORCE SUICIDE (by another player)
    // =========================
    public static void forceExecute(Player target, Player sender) {
        FileConfiguration cfg = Main.getInstance().getConfig();
        int countdownDuration = cfg.getInt("suicide.countdown_duration", 10);
        int cooldownSeconds = cfg.getInt("suicide.cooldown_seconds", 10);

        // Cancel any active suicide countdown on the target
        cleanup(target.getUniqueId());

        // Send forced-suicide message to the target
        target.sendMessage("");
        target.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        target.sendMessage("§8┃   " + MessageUtil.legacy(MessagesManager.getString("suicide.messages.forced_title", "<dark_red>☠</dark_red> <red>FORCE SUICIDE!</red>")));
        target.sendMessage("§8┃");
        target.sendMessage("§8┃   " + MessageUtil.legacy(MessagesManager.getString("suicide.messages.forced_body", "<gray>Player </gray><white>{sender}</white><gray> is force-suiciding you.</gray>").replace("{sender}", sender.getName())));
        target.sendMessage("§8┃   " + MessageUtil.legacy(MessagesManager.getString("suicide.messages.forced_nocancel", "<red>Cannot be cancelled!</red>")));
        target.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        target.sendMessage("");

        startCountdown(target, countdownDuration, cooldownSeconds);
    }

    // =========================
    // START COUNTDOWN (shared by self-suicide and forced suicide)
    // =========================
    private static void startCountdown(Player player, int countdownDuration, int cooldownSeconds) {
        UUID uuid = player.getUniqueId();
        FileConfiguration cfg = Main.getInstance().getConfig();

        // =========================
        // BOSSBAR
        // =========================
        String bossColorStr = cfg.getString("suicide.bossbar.color", "RED");
        String bossStyleStr = cfg.getString("suicide.bossbar.style", "SOLID");
        String bossTitle = MessagesManager.getString("suicide.bossbar.title", "<dark_red>☠</dark_red> <red>Suicide in</red> <yellow>{seconds}</yellow> <red>seconds</red>");

        BarColor bossColor;
        try {
            bossColor = BarColor.valueOf(bossColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            bossColor = BarColor.RED;
        }
        BarStyle bossStyle;
        try {
            bossStyle = BarStyle.valueOf(bossStyleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            bossStyle = BarStyle.SOLID;
        }

        BossBar bossBar = Bukkit.createBossBar(
                MessageUtil.legacy(bossTitle.replace("{seconds}", String.valueOf(countdownDuration))),
                bossColor,
                bossStyle
        );
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);

        // =========================
        // ЧАТ: начальное сообщение
        // =========================
        String confirmedTitle = MessagesManager.getString("suicide.messages.confirmed_title", "<dark_red>☠</dark_red> <red>Countdown started!</red>");
        String confirmedNoCancel = MessagesManager.getString("suicide.messages.confirmed_no_cancel", "<red>Cannot be cancelled!</red>");

        player.sendMessage("");
        player.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        player.sendMessage("§8┃ " + MessageUtil.legacy(confirmedTitle));
        player.sendMessage("§8┃");
        player.sendMessage("§8┃ " + MessageUtil.legacy(confirmedNoCancel));
        player.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        player.sendMessage("");

        playSuicideBeep(player, 1.2f);

        // =========================
        // ТАЙМЕР ОБРАТНОГО ОТСЧЁТА (каждый тик)
        // =========================
        int duration = countdownDuration;
        int totalTicks = duration * 20;
        String tickSoundName = cfg.getString("suicide.sounds.tick", "BLOCK_NOTE_BLOCK_PLING");
        String finishSoundName = cfg.getString("suicide.sounds.finish", "ENTITY_LIGHTNING_BOLT_THUNDER");
        String timerActionbar = MessagesManager.getString("suicide.messages.timer_actionbar", "<red><bold>☠</bold></red> <white>Suicide in</white> <yellow><bold>{seconds}</bold></yellow> <white>seconds</white>");
        String timerChat = MessagesManager.getString("suicide.messages.timer_chat", "<dark_gray>[<dark_red>☠</dark_red>]</dark_gray> <red>Suicide in</red> <yellow>{seconds}</yellow> <red>seconds...</red>");
        String deathMsg = MessagesManager.getString("suicide.messages.death_message", "<dark_gray>[<dark_red>☠</dark_red>]</dark_gray> <red>You have committed suicide...</red>");

        final Sound tickSound = parseSound(tickSoundName, Sound.BLOCK_NOTE_BLOCK_PLING);
        final Sound finishSound = parseSound(finishSoundName, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);

        BukkitRunnable task = new BukkitRunnable() {
            int tick = 0;
            int beepCounter = 0;
            int lastDisplaySecond = -1;

            @Override
            public void run() {
                int currentSecond = duration - (tick / 20);

                if (currentSecond < 0) {
                    bossBar.removeAll();
                    suicideTasks.remove(uuid);
                    suicideCooldowns.put(uuid, System.currentTimeMillis() + cooldownSeconds * 1000L);

                    player.sendMessage(MessageUtil.parse(deathMsg));
                    player.playSound(player.getLocation(), finishSound, 1.0f, 1.0f);
                    player.setHealth(0);
                    cancel();
                    return;
                }

                if (currentSecond != lastDisplaySecond) {
                    lastDisplaySecond = currentSecond;

                    if (currentSecond <= 5 && currentSecond > 0) {
                        player.sendMessage(MessageUtil.parse(timerChat.replace("{seconds}", String.valueOf(currentSecond))));
                    }

                    player.sendActionBar(MessageUtil.parse(timerActionbar.replace("{seconds}", String.valueOf(currentSecond))));

                    bossBar.setTitle(MessageUtil.legacy(bossTitle.replace("{seconds}", String.valueOf(currentSecond))));
                }

                double progress = (double) (totalTicks - tick) / totalTicks;
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                int interval = Math.max(4, 20 - (tick * 16 / totalTicks));
                if (beepCounter >= interval) {
                    double p = (double) tick / totalTicks;
                    float pitch = (float) (1.2 + 1.3 * Math.min(1.0, p));
                    player.playSound(player.getLocation(), tickSound, 1.0f, pitch);
                    beepCounter = 0;
                }
                beepCounter++;

                tick++;
            }
        };

        task.runTaskTimer(Main.getInstance(), 0L, 1L);
        suicideTasks.put(uuid, task);
    }

    public static void cleanup(UUID uuid) {
        suicideConfirmed.remove(uuid);
        BukkitRunnable task = suicideTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private static void playSuicideBeep(Player player, float pitch) {
        try {
            String soundName = Main.getInstance().getConfig().getString("suicide.sounds.tick", "BLOCK_NOTE_BLOCK_PLING");
            Sound sound = SoundUtil.getSound(soundName);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, pitch);
            }
        } catch (Exception ignored) {}
    }

    private static Sound parseSound(String name, Sound fallback) {
        Sound sound = SoundUtil.getSound(name);
        return sound != null ? sound : fallback;
    }
}
