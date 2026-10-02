package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SharePosDialogScreen;
import com.ultimateimprovments.command.SubCommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /ui sharepos — opens a confirmation dialog; on ✔ Share the player's world and
 * coordinates are broadcast to the whole server (in the plugin's name, in English).
 *
 * <p>Permission: {@code ui.command.sharepos} (registered in {@code Permissions} — in code, not plugin.yml).</p>
 */
public final class SharePosSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.sharepos";

    @Override
    public String getName() {
        return "sharepos";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender, PERMISSION);
            return true;
        }
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }

        SharePosDialogScreen.openRequest(player);
        return true;
    }
}
