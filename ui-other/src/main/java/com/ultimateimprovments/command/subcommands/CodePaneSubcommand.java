package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.command.CodePaneKeyCommand;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelDatabase;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelDialogScreen;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelSession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class CodePaneSubcommand {

    private CodePaneSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("key")) {
            return CodePaneKeyCommand.execute(sender, args);
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<dark_red>❌ <red>Только игрок может открыть кодовую панель.")); return true; }
        if (!player.hasPermission("ui.command.codepane")) { CommandErrors.noPermission(player); return true; }

        // Open a dialog with clean input
        CodePanelSession.reset(player.getUniqueId());
        CodePanelDialogScreen.open(player);
        return true;
    }

    /**
     * Tab-complete for /ui codepane:
     * <pre>
     *   /ui codepane &lt;TAB&gt;              → key
     *   /ui codepane key &lt;TAB&gt;          → add, list, remove, modify
     *   /ui codepane key remove|modify &lt;TAB&gt; → existing key names from DB
     *   /ui codepane key add|modify … &lt;TAB&gt;  → flag prefixes
     * </pre>
     */
    public static List<String> tabComplete(String[] args) {
        if (args.length <= 2) {
            // /ui codepane <TAB>
            return List.of("key");
        }
        if (args[1].equalsIgnoreCase("key")) {
            if (args.length == 3) {
                // /ui codepane key <TAB>
                return List.of("add", "list", "remove", "modify");
            }
            String sub = args[2].toLowerCase();
            if (args.length == 4 && (sub.equals("remove") || sub.equals("modify"))) {
                // /ui codepane key remove|modify <TAB> — existing key names
                return CodePanelDatabase.getAllKeyNames();
            }
            if (args.length >= 5 && (sub.equals("add") || sub.equals("modify"))) {
                // /ui codepane key add|modify <name> <code> [flags]
                return List.of("attempts:", "time:", "whitelist:", "blacklist:", "command:");
            }
        }
        return List.of();
    }
}
