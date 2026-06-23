package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.commands.CodePaneKeyCommand;
import com.mcplugin.mechanics.security.codepanel.CodePanelClick;
import com.mcplugin.mechanics.security.codepanel.CodePanelCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CodePaneSubcommand {

    private CodePaneSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("key")) {
            return CodePaneKeyCommand.execute(sender, args);
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cТолько игрок может открыть кодовую панель."); return true; }
        if (!player.hasPermission("mcplugin.command.codepane")) { player.sendMessage("§4❌ §cУ вас нет прав на использование кодовой панели!"); return true; }
        return CodePanelCommand.handleCommand(player);
    }

    // pane_click is handled separately
    public static boolean paneClick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду."); return true; }
        if (!player.hasPermission("mcplugin.command.codepane")) { player.sendMessage("§4❌ §cУ вас нет прав на использование кодовой панели!"); return true; }
        if (args.length < 2) return true;
        return CodePanelClick.handleClick(player, args[1]);
    }
}
