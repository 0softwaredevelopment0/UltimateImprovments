package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ui flyspeed <игрок> <скорость 0x–10x> — устанавливает скорость полёта игроку.
 * <p>
 *   /ui flyspeed <игрок> 5x      — 5x скорость полёта
 *   /ui flyspeed <игрок> 0x      — полёт заблокирован (нулевая скорость)
 *   /ui flyspeed <игрок> -reset  — сброс на дефолт (1x)
 * <p>
 * Масштаб: 1x = ванильный дефолт (flySpeed 0.1), 10x = 1.0 (максимум Bukkit).
 * Право: ui.command.flyspeed
 */
public final class FlySpeedSubcommand {

    private FlySpeedSubcommand() {}

    private static final String PERMISSION = "ui.command.flyspeed";
    /** Ванильная скорость полёта по умолчанию (Bukkit default). */
    private static final float DEFAULT_FLY_SPEED = 0.1f;
    private static final int MAX_MULTIPLIER = 10;

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to use this command!</red>"
            ));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui flyspeed <player> <0x–10x | -reset></white>"
            ));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player</red> <yellow>" + args[1] + "</yellow> <red>not found or offline!</red>"
            ));
            return true;
        }

        String speedArg = args[2].toLowerCase();

        // ─── -reset → дефолт (1x) ───
        if (speedArg.equals("-reset")) {
            target.setFlySpeed(DEFAULT_FLY_SPEED);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Fly speed of</white> <yellow>" + target.getName()
                            + "</yellow> <white>reset to default </white><aqua>1x</aqua><white>.</white>"
            ));
            if (!sender.equals(target)) {
                target.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Your fly speed was reset to default </white><aqua>1x</aqua><white> by</white> <yellow>"
                                + sender.getName() + "</yellow><white>.</white>"
                ));
            }
            return true;
        }

        // ─── Парсим множитель "Nx" (0x..10x) ───
        if (!speedArg.endsWith("x")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid speed: </red><yellow>" + args[2]
                            + "</yellow> <gray>Use format </gray><white>Nx</white><gray> (e.g. 2x, 5x, 0x) or </gray><white>-reset</white><gray>.</gray>"
            ));
            return true;
        }

        String numPart = speedArg.substring(0, speedArg.length() - 1);
        int multiplier;
        try {
            multiplier = Integer.parseInt(numPart);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid speed: </red><yellow>" + args[2] + "</yellow>"
            ));
            return true;
        }

        if (multiplier < 0 || multiplier > MAX_MULTIPLIER) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Speed must be between </red><white>0x</white> <red>and </red><white>" + MAX_MULTIPLIER + "x</white><red>!</red>"
            ));
            return true;
        }

        float flySpeed = DEFAULT_FLY_SPEED * multiplier; // 10x → 1.0 (максимум Bukkit)
        target.setFlySpeed(Math.min(1.0f, Math.max(0.0f, flySpeed)));

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Fly speed of</white> <yellow>" + target.getName()
                        + "</yellow> <white>set to </white><aqua>" + multiplier + "x</aqua><white>.</white>"
        ));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Your fly speed was set to </white><aqua>" + multiplier
                            + "x</aqua> <white>by</white> <yellow>" + sender.getName() + "</yellow><white>.</white>"
            ));
        }
        return true;
    }

    // =========================
    // TAB COMPLETION
    // =========================

    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            completions.add("-reset");
            for (int i = 0; i <= MAX_MULTIPLIER; i++) {
                completions.add(i + "x");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
