package com.mcplugin.cp;

import com.mcplugin.Main;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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

        if (isLocked(player)) {
            long left = (CodePanelSession.getLockEnd(player.getUniqueId()) - System.currentTimeMillis()) / 1000;

            player.sendMessage(
                    msg("codepanel.messages.locked")
                            .replace("{time}", String.valueOf(Math.max(0, left)))
            );
            return true;
        }

        switch (value) {

            case "R" -> {
                CodePanelSession.reset(player.getUniqueId());
                CodePanelSession.resetAttempts(player.getUniqueId());
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
    // LOCK CHECK
    // =========================
    private boolean isLocked(Player player) {

        long lockEnd = CodePanelSession.getLockEnd(player.getUniqueId());

        if (lockEnd <= 0) return false;

        if (System.currentTimeMillis() > lockEnd) {
            CodePanelSession.resetAttempts(player.getUniqueId());
            CodePanelSession.setLockEnd(player.getUniqueId(), 0);
            return false;
        }

        return true;
    }

    // =========================
    // LOADING (CONFIG)
    // =========================
    private void startLoading(Player player) {

        if (isLocked(player)) return;

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

                    player.sendMessage(
                            msg("codepanel.messages.progress")
                                    .replace("{progress}", String.valueOf(progress))
                    );

                    play(player, "step");

                    progress += step;
                    return;
                }

                player.sendMessage(
                        msg("codepanel.messages.progress")
                                .replace("{progress}", "100")
                );

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
    // CHECK LOGIC
    // =========================
    private void check(Player player) {

        if (!isSafe()) return;

        String input = CodePanelSession.getCode(player.getUniqueId());

        ConfigurationSection section = Main.getInstance()
                .getConfig()
                .getConfigurationSection("validcodes");

        if (section == null) {
            player.sendMessage(msg("codepanel.messages.no_config"));
            return;
        }

        for (String key : section.getKeys(false)) {

            String code = section.getString(key + ".code");
            String command = section.getString(key + ".command");

            if (code != null && code.equals(input)) {

                player.sendMessage(msg("codepanel.messages.success"));
                play(player, "success");

                CodePanelSession.resetAttempts(player.getUniqueId());
                CodePanelSession.setLockEnd(player.getUniqueId(), 0);

                if (command != null) {
                    command = command.replace("$entity", player.getName());

                    Main.getInstance().getServer().dispatchCommand(
                            Main.getInstance().getServer().getConsoleSender(),
                            command
                    );
                }

                return;
            }
        }

        // =========================
        // WRONG CODE (CONFIG CONTROLLED)
        // =========================
        int maxAttempts = cfgInt("codepanel.attempts.max", 3);
        long lockTimeMs = cfgInt("codepanel.attempts.lock_time_seconds", 10) * 1000L;

        int attempts = CodePanelSession.addAttempt(player.getUniqueId());

        if (attempts >= maxAttempts) {

            CodePanelSession.setLockEnd(
                    player.getUniqueId(),
                    System.currentTimeMillis() + lockTimeMs
            );

            player.sendMessage(
                    msg("codepanel.messages.locked_now")
                            .replace("{time}", String.valueOf(lockTimeMs / 1000))
            );

            play(player, "fail");

        } else {

            player.sendMessage(
                    msg("codepanel.messages.error")
                            .replace("{attempts}", String.valueOf(attempts))
                            .replace("{max}", String.valueOf(maxAttempts))
            );

            play(player, "fail");
        }
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

            Sound sound = Sound.valueOf(name);

            player.playSound(player.getLocation(), sound, 1f, 1f);

        } catch (Exception ignored) {}
    }

    // =========================
    // CONFIG HELPERS
    // =========================
    private String msg(String path) {
        if (!isSafe()) return path;
        return Main.getInstance().getConfig().getString(path, path);
    }

    private int cfgInt(String path, int def) {
        if (!isSafe()) return def;
        return Main.getInstance().getConfig().getInt(path, def);
    }

    private boolean isSafe() {
        return Main.getInstance() != null;
    }
}