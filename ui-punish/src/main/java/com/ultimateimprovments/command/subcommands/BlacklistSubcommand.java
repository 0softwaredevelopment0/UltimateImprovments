package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.whitelist.TimedAccessLists;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 📋 BlacklistSubcommand — handler for /ui blacklist.
 * <p>
 * All logic (instant, delayed and temp operations, list view, tab completion)
 * is shared — see {@link AccessListCommands}.
 */
public final class BlacklistSubcommand {

    private BlacklistSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.blacklist")) {
            CommandErrors.noPermission(sender, "ui.command.blacklist");
            return true;
        }
        return AccessListCommands.execute(sender, TimedAccessLists.AccessList.BLACKLIST, args, false);
    }

    public static List<String> tabComplete(String[] args) {
        return AccessListCommands.tabComplete(args);
    }
}
