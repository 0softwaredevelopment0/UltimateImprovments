package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.mechanics.environment.radiation.RadiationManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class RadiationSubcommand {

    private RadiationSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);

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
                sender.sendMessage("§a✔ §fРадиация игрока §e" + args[1] + "§f установлена на §e" + value + " §7(§f" + String.format(Locale.US, "%.1f", roentgen) + " Р/Ч§7)");
            } catch (NumberFormatException e) {
                sender.sendMessage("§4❌ §cНеверное число: §f" + args[2]);
            }
            return true;
        }
        return false;
    }
}
