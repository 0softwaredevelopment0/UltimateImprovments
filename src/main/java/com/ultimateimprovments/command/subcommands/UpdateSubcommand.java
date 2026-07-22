package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.mechanics.features.updater.UpdateChecker;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UpdateSubcommand {

    private UpdateSubcommand() {}

    public static boolean checkOnly(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.checkver")) {
            p.sendMessage(MessageUtil.parse(MessagesManager.getString("update.no_permission_check", "<red>❌ You don't have permission to check for updates!</red>"))); return true;
        }
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.checking", "<yellow>⟳</yellow> <gray>Checking for updates on GitHub...</gray>")));
        UpdateChecker.checkOnly(sender);
        return true;
    }

    public static boolean downloadAndReplace(CommandSender sender) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.checkver")) {
            p.sendMessage(MessageUtil.parse(MessagesManager.getString("update.no_permission_install", "<red>❌ You don't have permission to install updates!</red>"))); return true;
        }
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("update.downloading", "<yellow>⟳</yellow> <gray>Downloading update from GitHub...</gray>")));
        UpdateChecker.downloadAndReplace(sender);
        return true;
    }
}
