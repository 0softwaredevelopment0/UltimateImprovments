package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelDatabase;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class CodePanelClick implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) return true;
        return handleClick(player, args[0]);
    }

    /**
     * Static entry point for /mp pane_click subcommand.
     */
    public static boolean handleClick(Player player, String value) {
        CodePanelClick instance = new CodePanelClick();
        return instance.handleClickInternal(player, value);
    }

    private boolean handleClickInternal(Player player, String value) {

        if (!isSafe()) {
            player.sendMessage("§cSystem is reloading...");
            return true;
        }

        // Enter cooldown check
        if ("E".equals(value) && CodePanelSession.isEnterOnCooldown(player.getUniqueId())) {
            long left = CodePanelSession.getRemainingCooldown(player.getUniqueId()) / 1000;
            player.sendMessage("§cPlease wait §e" + Math.max(0, left) + "§c sec before entering again");
            return true;
        }

        switch (value) {

            case "R" -> {
                CodePanelSession.reset(player.getUniqueId());
                play(player, "reset");
            }

            case "E" -> startLoading(player);

            default -> {
                CodePanelSession.add(player.getUniqueId(), value);
                play(player, "click");
            }
        }

        CodePanelCommand.sendPanel(player);
        return true;
    }

    // =========================
    // LOADING (CONFIG)
    // =========================
    private void startLoading(Player player) {

        // Set Enter cooldown before loading
        int cooldown = cfgInt("codepanel.enter_cooldown", 3);
        CodePanelSession.setEnterCooldown(player.getUniqueId(), cooldown * 1000L);

        play(player, "click");

        int stepTicks = cfgInt("codepanel.loading.step_ticks", 5);
        int step = cfgInt("codepanel.loading.step", 10);
        int delay = cfgInt("codepanel.loading.delay_after", 20);

        new BukkitRunnable() {

            int progress = step;

            @Override
            public void run() {

                if (!player.isOnline() || !isSafe()) {
                    cancel();
                    return;
                }

                if (progress < 100) {

                    player.sendMessage(MessageUtil.parse(
                            msg("codepanel.messages.progress")
                                    .replace("%progress%", String.valueOf(progress))
                    ));

                    play(player, "step");

                    progress += step;
                    return;
                }

                player.sendMessage(MessageUtil.parse(
                        msg("codepanel.messages.progress")
                                .replace("%progress%", "100")
                ));

                play(player, "finish");

                cancel();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline() || !isSafe()) return;
                        check(player);
                    }
                }.runTaskLater(Main.getInstance(), delay);
            }

        }.runTaskTimer(Main.getInstance(), 0L, stepTicks);
    }

    // =========================
    // CHECK LOGIC (БД вместо config.yml)
    // =========================
    private void check(Player player) {

        if (!isSafe()) return;

        String input = CodePanelSession.getCode(player.getUniqueId());

        // Чистим просроченные ключи в БД
        CodePanelDatabase.cleanupExpiredKeys();

        // Ищем код в БД
        List<CodePanelDatabase.CodePanelKey> keys = CodePanelDatabase.getAllKeys();

        if (keys.isEmpty()) {
            player.sendMessage(MessageUtil.parse(msg("codepanel.messages.no_config")));
            return;
        }

        for (CodePanelDatabase.CodePanelKey key : keys) {

            if (!key.code.equals(input)) continue;

            // Проверка whitelist / blacklist
            if (!key.isPlayerAllowed(player.getName())) {
                player.sendMessage("§4❌ §cYou don't have access to this code!");
                play(player, "fail");
                return;
            }

            // max_attempts — увеличиваем счётчик
            if (key.maxAttempts > 0) {
                CodePanelDatabase.incrementAttemptsUsed(key.keyName);
                int newUsed = key.attemptsUsed + 1;

                if (newUsed >= key.maxAttempts) {
                    CodePanelDatabase.removeKey(key.keyName);
                    ConsoleLogger.info(
                            "[CodePanel] Key '" + key.keyName + "' removed after "
                                    + newUsed + "/" + key.maxAttempts + " uses."
                    );
                }
            }

            player.sendMessage(MessageUtil.parse(msg("codepanel.messages.success")));
            play(player, "success");

            if (key.command != null && !key.command.isEmpty()) {
                // Поддержка нескольких команд через запятую
                String[] commands = key.command.split(",");
                for (String rawCmd : commands) {
                    String trimmedCmd = rawCmd.trim();
                    if (trimmedCmd.isEmpty()) continue;

                    // Поддержка обоих плейсхолдеров: $entity и %entity%
                    String cmd = trimmedCmd
                            .replace("$entity", player.getName())
                            .replace("%entity%", player.getName());

                    Main.getInstance().getServer().dispatchCommand(
                            Main.getInstance().getServer().getConsoleSender(),
                            cmd
                    );
                }
            }

            return;
        }

        // НЕВЕРНЫЙ КОД — просто сообщение, без блокировки
        player.sendMessage("§c❌ Wrong code!");
        play(player, "fail");
    }

    // =========================
    // SOUND SYSTEM
    // =========================
    private void play(Player player, String path) {

        if (!isSafe()) return;

        try {
            String name = Main.getInstance()
                    .getConfig()
                    .getString("codepanel.sounds." + path);

            if (name == null) return;

            Sound sound = SoundUtil.getSound(name);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }

        } catch (Exception e) {
            ConsoleLogger.warn("[CodePanel] Sound error: " + e.getMessage());
        }
    }

    // =========================
    // CONFIG HELPERS
    // =========================
    private String msg(String path) {
        if (!isSafe()) return path;
        return MessagesManager.getString(path, path);
    }

    private int cfgInt(String path, int def) {
        if (!isSafe()) return def;
        return Main.getInstance().getConfig().getInt(path, def);
    }

    private boolean isSafe() {
        return Main.getInstance() != null;
    }
}