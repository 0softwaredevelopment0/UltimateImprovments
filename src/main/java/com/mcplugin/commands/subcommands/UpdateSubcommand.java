package com.mcplugin.commands.subcommands;

import com.mcplugin.features.updater.UpdateChecker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UpdateSubcommand {

    private UpdateSubcommand() {}

    public static boolean checkOnly(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.checkver")) {
            p.sendMessage("§4❌ §cУ вас нет прав на проверку обновлений!"); return true;
        }
        sender.sendMessage("§e⟳ §7Проверка обновлений на GitHub...");
        UpdateChecker.checkOnly(sender);
        return true;
    }

    public static boolean downloadAndReplace(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.checkver")) {
            p.sendMessage("§4❌ §cУ вас нет прав на установку обновлений!"); return true;
        }
        sender.sendMessage("§e⟳ §7Загрузка обновления с GitHub...");
        UpdateChecker.downloadAndReplace(sender);
        return true;
    }
}
