package com.mcplugin.commands;

import com.mcplugin.features.vanish.VanishManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Переопределяет /list команду, чтобы скрывать ванишнутых игроков из вывода.
 * Работает как для консоли, так и для игроков.
 */
public class VanishListCommand extends Command {

    public VanishListCommand() {
        super("list");
        setDescription("Показать список игроков онлайн");
        setUsage("/list");
        setPermission(null);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        boolean canSeeVanished = sender.hasPermission("mcplugin.command.vanish");

        List<String> names = new ArrayList<>();
        int vanishedCount = 0;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (VanishManager.isVanished(online.getUniqueId())) {
                vanishedCount++;
                if (canSeeVanished) {
                    names.add("§7" + online.getDisplayName() + "§r");
                }
            } else {
                names.add("§f" + online.getDisplayName() + "§r");
            }
        }

        int totalOnline = Bukkit.getOnlinePlayers().size();
        int visibleCount = totalOnline - (canSeeVanished ? 0 : vanishedCount);

        sender.sendMessage("§7На сервере §f" + visibleCount + "§7/§f" + Bukkit.getMaxPlayers() + " §7игроков:");
        if (!names.isEmpty()) {
            sender.sendMessage(String.join("§7, ", names));
        }
        if (vanishedCount > 0 && canSeeVanished) {
            sender.sendMessage("§7(" + vanishedCount + " в ванише)");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }
}
