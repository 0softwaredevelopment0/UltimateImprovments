package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class RadiationSubcommand {

    private RadiationSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("checkrad")) {
            if (sender instanceof Player p && !p.hasPermission("mcplugin.command.checkrad")) {
                p.sendMessage("§4❌ §cУ вас нет прав на проверку радиации!"); return true;
            }
            if (args.length < 2) {
                if (!(sender instanceof Player self)) { sender.sendMessage("§4❌ §cИспользование: §f/mp checkrad §7<ник>"); return true; }
                printRadiation(sender, self, self.getName());
                return true;
            }
            @SuppressWarnings("deprecation")
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§4❌ §cИгрок §e" + args[1] + "§c не в сети!"); return true; }
            printRadiation(sender, target, args[1]);
            return true;
        }

        if (sub.equals("setrad")) {
            if (sender instanceof Player p && !p.hasPermission("mcplugin.command.setrad")) {
                p.sendMessage("§4❌ §cУ вас нет прав на изменение радиации!"); return true;
            }
            if (args.length < 3) { sender.sendMessage("§4❌ §cИспользование: §f/mp setrad §7<ник> <значение>"); return true; }
            @SuppressWarnings("deprecation")
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§4❌ §cИгрок §e" + args[1] + "§c не в сети!"); return true; }
            try {
                int value = Integer.parseInt(args[2]);
                if (value < 0) { sender.sendMessage("§4❌ §cЗначение радиации не может быть отрицательным!"); return true; }
                RadiationManager.setRadiation(target, value);
                double roentgen = value / 100.0;
                sender.sendMessage("§a✅ §fРадиация игрока §e" + args[1] + "§f установлена на §e" + value + " §7(§f" + String.format(Locale.US, "%.1f", roentgen) + " Р/Ч§7)");
            } catch (NumberFormatException e) {
                sender.sendMessage("§4❌ §cНеверное число: §f" + args[2]);
            }
            return true;
        }
        return false;
    }

    private static void printRadiation(CommandSender sender, Player target, String displayName) {
        int rad = RadiationManager.getRadiation(target);
        double roentgen = rad / 100.0;
        String levelColor, levelName;
        if (rad < 200) { levelColor = "§a"; levelName = "Безопасный"; }
        else if (rad < 400) { levelColor = "§e"; levelName = "Лёгкий"; }
        else if (rad < 800) { levelColor = "§6"; levelName = "Средний"; }
        else if (rad < 1600) { levelColor = "§c"; levelName = "Высокий"; }
        else if (rad < 3200) { levelColor = "§4"; levelName = "Критический"; }
        else { levelColor = "§5"; levelName = "Смертельный"; }
        sender.sendMessage("");
        sender.sendMessage("§8┌──────────────────────────┐");
        sender.sendMessage("§8│ §d☢ Радиация §8» §f" + displayName);
        sender.sendMessage("§8├──────────────────────────┤");
        sender.sendMessage("§8│ §7Уровень: §f" + rad + " §7(§f" + String.format(Locale.US, "%.1f", roentgen) + " Р/Ч§7)");
        sender.sendMessage("§8│ §7Статус:  " + levelColor + levelName);
        sender.sendMessage("§8└──────────────────────────┘");
        sender.sendMessage("");
    }
}
