package com.mcplugin.commands.subcommands;

import com.mcplugin.commands.PowerManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PowerSubcommand {

    private PowerSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§4❌ §cError: §7Usage: /mp power off|reboot|confirm|undo");
            return true;
        }

        PowerManager pm = PowerManager.getInstance();

        return switch (args[1].toLowerCase()) {
            case "off" -> handleOff(sender, pm);
            case "reboot" -> handleReboot(sender, pm);
            case "confirm" -> handleConfirm(sender, pm);
            case "undo" -> handleUndo(sender, pm);
            default -> {
                sender.sendMessage("§4❌ §cError: §7Usage: /mp power off|reboot|confirm|undo");
                yield true;
            }
        };
    }

    private static boolean handleOff(CommandSender sender, PowerManager pm) {
        if (!(sender instanceof Player)) { pm.executeDirect(false); return true; }
        Player player = (Player) sender;
        if (!player.hasPermission("mcplugin.command.power.off")) {
            player.sendMessage("§4❌ §cУ вас нет прав на выключение сервера!"); return true;
        }
        if (pm.hasPendingRequest()) { sender.sendMessage("§8[§4⚠§8] §cУже есть активный запрос на управление питанием сервера."); return true; }
        pm.requestStop(player.getName(), player.getUniqueId());
        player.sendMessage("§8[§4⚠§8] §eВыключение сервера инициировано, ожидание подтверждения консоли.");
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eВыключение сервера запрошено игроком §f" + player.getName() + "§e. Подтвердите командой: §f/mp power confirm");
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eОтменить: §f/mp power undo");
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eЗапрос будет автоматически отменён через §f30 §eсекунд.");
        return true;
    }

    private static boolean handleReboot(CommandSender sender, PowerManager pm) {
        if (!(sender instanceof Player)) { pm.executeDirect(true); return true; }
        Player player = (Player) sender;
        if (!player.hasPermission("mcplugin.command.power.reboot")) {
            player.sendMessage("§4❌ §cУ вас нет прав на перезагрузку сервера!"); return true;
        }
        if (pm.hasPendingRequest()) { sender.sendMessage("§8[§4⚠§8] §cУже есть активный запрос на управление питанием сервера."); return true; }
        pm.requestRestart(player.getName(), player.getUniqueId());
        player.sendMessage("§8[§4⚠§8] §eПерезапуск сервера инициирован, ожидание подтверждения консоли.");
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eПерезапуск сервера запрошен игроком §f" + player.getName() + "§e. Подтвердите командой: §f/mp power confirm");
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eОтменить: §f/mp power undo");
        Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eЗапрос будет автоматически отменён через §f30 §eсекунд.");
        return true;
    }

    private static boolean handleConfirm(CommandSender sender, PowerManager pm) {
        if (sender instanceof Player) { sender.sendMessage("§4❌ §cError: §7Только консоль может подтвердить запрос."); return true; }
        if (!pm.hasPendingRequest()) { sender.sendMessage("§8[§4⚠§8] §cНет активных запросов на выключение/перезапуск."); return true; }
        String action = pm.getCurrentRequestType() == PowerManager.RequestType.STOP ? "Выключение" : "Перезапуск";
        String requester = pm.getRequesterName();
        if (pm.confirmRequest()) {
            sender.sendMessage("§8[§2✔§8] §a" + action + " подтверждён (запрос от " + requester + ").");
            Bukkit.broadcastMessage("§8[§4⚠§8] §c" + action + " сервера подтверждён консолью.");
        } else sender.sendMessage("§4❌ §cОшибка при подтверждении.");
        return true;
    }

    private static boolean handleUndo(CommandSender sender, PowerManager pm) {
        if (!pm.hasPendingRequest()) { sender.sendMessage("§8[§4⚠§8] §cНет активных запросов на выключение/перезапуск."); return true; }
        if (sender instanceof Player player && !player.hasPermission("mcplugin.command.power.undo")) {
            player.sendMessage("§4❌ §cУ вас нет прав на отмену запроса!"); return true;
        }
        String undoerName = sender instanceof Player ? ((Player) sender).getName() : "Консоль";
        String action = pm.undoRequest(undoerName);
        sender.sendMessage(action != null ? "§8[§2✔§8] §a" + action + " сервера отменён." : "§4❌ §cОшибка при отмене.");
        return true;
    }
}
