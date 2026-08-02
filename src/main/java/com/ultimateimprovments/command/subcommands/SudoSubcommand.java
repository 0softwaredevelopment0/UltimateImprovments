package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.mechanics.security.sudo.SudoDatabase;
import com.ultimateimprovments.mechanics.security.sudo.SudoDialogScreen;
import com.ultimateimprovments.mechanics.security.sudo.SudoManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /ui sudo — управление sudo-режимом (GitHub-style).
 * <ul>
 *   <li>{@code /ui sudo status} — статус сессии</li>
 *   <li>{@code /ui sudo on} — открыть диалог ввода sudo-пароля</li>
 *   <li>{@code /ui sudo off} — выйти из sudo-режима</li>
 *   <li>{@code /ui sudo reset} — запросить сброс sudo-пароля (подтверждает консоль)</li>
 *   <li>{@code /ui sudo confirmreset <nick>} — консоль подтверждает сброс</li>
 * </ul>
 */
public final class SudoSubcommand {

    private SudoSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!SudoManager.isEnabled()) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Sudo mode is disabled in config.</red>"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_red>❌</dark_red> <red>Usage: </red><white>/ui sudo status|on|off|reset</white>"));
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "on" -> handleOn(sender);
            case "off" -> handleOff(sender);
            case "reset" -> handleReset(sender);
            case "confirmreset" -> handleConfirmReset(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse(
                        "<dark_red>❌</dark_red> <red>Unknown: </red><white>" + args[1]
                                + "</white><red>. Usage: </red><white>/ui sudo status|on|off|reset</white>"));
                yield true;
            }
        };
    }

    private static boolean handleStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<gray>Sudo is console-confirmed. Use /ui sudo confirmreset &lt;nick&gt;.</gray>"));
            return true;
        }
        SudoManager manager = SudoManager.getInstance();
        boolean active = manager != null && manager.isSudoActive(player.getUniqueId());
        boolean hasPassword = SudoDatabase.isRegistered(player.getUniqueId());
        long remaining = manager != null ? manager.getRemainingSeconds(player.getUniqueId()) : 0;

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse("<gold>══════ Sudo Mode ══════</gold>"));
        player.sendMessage(MessageUtil.parse(active
                ? "<green>✔ Active</green> <gray>(</gray><yellow>" + remaining + "</yellow><gray>s left)</gray>"
                : "<red>✖ Inactive</red>"));
        player.sendMessage(MessageUtil.parse(hasPassword
                ? "<gray>Password: </gray><green>✔ set</green>"
                : "<gray>Password: </gray><red>✖ not set</red>"));
        player.sendMessage(MessageUtil.parse("<gray>Commands: </gray><white>/ui sudo on|off|reset</white>"));
        player.sendMessage("");
        return true;
    }

    private static boolean handleOn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can enter sudo mode.</red>"));
            return true;
        }
        SudoManager manager = SudoManager.getInstance();
        if (manager != null && manager.isSudoActive(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Sudo mode is already active.</white>"));
            return true;
        }
        boolean registered = SudoDatabase.isRegistered(player.getUniqueId());
        SudoDialogScreen.open(player, registered);
        return true;
    }

    private static boolean handleOff(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can exit sudo mode.</red>"));
            return true;
        }
        SudoManager manager = SudoManager.getInstance();
        if (manager != null) {
            manager.endSudoSession(player.getUniqueId());
        }
        player.sendMessage(MessageUtil.parse("<gray>✖ Sudo mode deactivated.</gray>"));
        return true;
    }

    private static boolean handleReset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can request a reset.</red>"));
            return true;
        }
        SudoManager manager = SudoManager.getInstance();
        if (manager != null) {
            manager.requestReset(player);
        }
        return true;
    }

    private static boolean handleConfirmReset(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only console can confirm a sudo reset.</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui sudo confirmreset <nick></white>"));
            return true;
        }
        SudoManager manager = SudoManager.getInstance();
        if (manager != null) {
            manager.confirmReset(args[2]);
        }
        return true;
    }

    public static List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 2) {
            result.add("status");
            result.add("on");
            result.add("off");
            result.add("reset");
            result.add("confirmreset");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("confirmreset")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        }
        return result;
    }
}
