package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.command.ChgDimCommand;
import com.ultimateimprovements.config.MessagesManager;
import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.command.CommandSender;
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
        ChgDimCommand.startChatInput(player);
        return true;
    }
}
