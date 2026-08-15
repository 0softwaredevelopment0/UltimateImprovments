package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.GetPosDialogScreen;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /ui getpos — opens a dialog to fetch a player's coordinates and world.
 * <p>
 * Works like /ui askpos: no arguments needed — the command opens a dialog with a
 * nickname field and ✔ Get / ✖ Cancel buttons. On ✔ Get the target's world and
 * coordinates are printed to the sender's chat. The dialog uses
 * {@code DialogAction.CLOSE}, so there is no ~4 second "Waiting for server…" hang
 * after a button click.
 * <p>
 * Permission: {@code ui.command.getpos} (registered in {@code Permissions} — in code, not plugin.yml).
 */
public final class GetPosSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.getpos";

    @Override
    public String getName() {
        return "getpos";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        GetPosDialogScreen.openRequest(player);
        return true;
    }

    /**
     * Prints the target's world and coordinates to the sender's chat.
     * Called from {@link GetPosDialogHandler} when the ✔ Get button is clicked.
     */
    public static void showPosition(CommandSender sender, Player target) {
        Location loc = target.getLocation();
        World world = loc.getWorld();
        String worldName = world != null ? world.getName() : "unknown";
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><green>Coordinates</green>"));
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gray>Player: </gray><yellow>" + target.getName() + "</yellow>"));
        sender.sendMessage(MessageUtil.parse("<gray>World: </gray><white>" + worldName + "</white>"));
        sender.sendMessage(MessageUtil.parse("<gray>Coordinates: </gray><white>" + x + " / " + y + " / " + z + "</white>"));
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        sender.sendMessage("");
    }
}
