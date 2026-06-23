package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.commands.ChgDimGUI;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class ChgDimSubcommand {

    private ChgDimSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.player_only",
                            "<dark_red>❌</dark_red> <red>Только игрок может использовать эту команду!</red>")));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.chgdim")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("changedimmension.messages.no_permission",
                            "<dark_red>❌</dark_red> <red>У вас нет прав на эту команду!</red>")));
            return true;
        }
        ChgDimGUI.open(player);
        return true;
    }
}
