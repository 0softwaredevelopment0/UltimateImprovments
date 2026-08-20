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
            if (args.length < 3) { sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_red>❌ <red>Usage: <white>/ui setrad <gray><player> <value>")); return true; }
            @SuppressWarnings("deprecation")
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_red>❌ <red>Player <yellow>" + args[1] + "<red> is offline or not exist!")); return true; }
            try {
                int value = Integer.parseInt(args[2]);
                if (value < 0) { sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_red>❌ <red>Radiation value cannot be negative!")); return true; }
                RadiationManager.setRadiation(target, value);
                double roentgen = value / 100.0;
                sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<green>✔ <white>Player radiation <yellow>" + args[1] + "<white> was set to <yellow>" + value + " <gray>(<white>" + String.format(Locale.US, "%.1f", roentgen) + " r/h<gray>)"));
            } catch (NumberFormatException e) {
                sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_red>❌ <red>Incorrect value: <white>" + args[2]));
            }
            return true;
        }
        return false;
    }
}
