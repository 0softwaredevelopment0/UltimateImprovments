package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class RadiationSubcommand {

    private RadiationSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("setrad")) {
            if (sender instanceof Player p && !p.hasPermission("ui.command.setrad")) {
                CommandErrors.noPermission(p); return true;
            }
            if (args.length < 3) { sender.sendMessage("§4❌ §cUsage: §f/ui setrad §7<player> <value>"); return true; }
            @SuppressWarnings("deprecation")
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§4❌ §cPlayer §e" + args[1] + "§c is offline or not exist!"); return true; }
            try {
                int value = Integer.parseInt(args[2]);
                if (value < 0) { sender.sendMessage("§4❌ §cRadiation value cannot be negative!"); return true; }
                RadiationManager.setRadiation(target, value);
                double roentgen = value / 100.0;
                sender.sendMessage("§a✔ §fPlayer radiation §e" + args[1] + "§f was set to §e" + value + " §7(§f" + String.format(Locale.US, "%.1f", roentgen) + " r/h§7)");
            } catch (NumberFormatException e) {
                sender.sendMessage("§4❌ §cIncorrect value: §f" + args[2]);
            }
            return true;
        }
        return false;
    }
}
