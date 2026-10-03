package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.whitelist.TimedAccessLists;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 📋 OpBlacklistSubcommand — handler for /ui opblacklist.
 * <p>
 * Players on the OP blacklist lose their OP automatically (join, add and
 * periodic checks — see {@link com.ultimateimprovments.whitelist.OpBlacklistManager}).
 * <p>
 * All command logic (instant, delayed and temp operations, list view, tab
 * completion) is shared — see {@link AccessListCommands}.
 */
public final class OpBlacklistSubcommand {

    private OpBlacklistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.opblacklist")) {
            CommandErrors.noPermission(sender, "ui.command.opblacklist");
            return true;
        }
        if (!com.ultimateimprovments.whitelist.OpBlacklistManager.isFeatureEnabled()) {
            CommandErrors.moduleDisabled(sender, "opblacklist");
            return true;
        }
        return AccessListCommands.execute(sender, TimedAccessLists.AccessList.OPBLACKLIST, args, true);
    }

    public static List<String> tabComplete(String[] args) {
        return AccessListCommands.tabComplete(args);
    }
}
