package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.whitelist.TimedAccessLists;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 📋 OpWhitelistSubcommand — handler for /ui opwhitelist.
 * <p>
 * All logic (instant, delayed and temp operations, list view, tab completion)
 * is shared — see {@link AccessListCommands}.
 */
public final class OpWhitelistSubcommand {

    private OpWhitelistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.opwhitelist")) {
            CommandErrors.noPermission(sender, "ui.command.opwhitelist");
            return true;
        }
        if (!com.ultimateimprovments.whitelist.OpWhitelistManager.isFeatureEnabled()) {
            CommandErrors.moduleDisabled(sender, "opwhitelist");
            return true;
        }
        return AccessListCommands.execute(sender, TimedAccessLists.AccessList.OPWHITELIST, args, true);
    }

    public static List<String> tabComplete(String[] args) {
        return AccessListCommands.tabComplete(args);
    }
}
