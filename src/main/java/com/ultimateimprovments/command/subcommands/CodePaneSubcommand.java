package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.command.CodePaneKeyCommand;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelDialogScreen;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelSession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CodePaneSubcommand {

    private CodePaneSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("key")) {
            return CodePaneKeyCommand.execute(sender, args);
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cТолько игрок может открыть кодовую панель."); return true; }
        if (!player.hasPermission("ui.command.codepane")) { CommandErrors.noPermission(player); return true; }

        // Open a dialog with clean input
        CodePanelSession.reset(player.getUniqueId());
        CodePanelDialogScreen.open(player);
        return true;
    }
}
