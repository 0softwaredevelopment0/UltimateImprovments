package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /ui clearchat &lt;player|all&gt; — clears the chat.
 * <p>
 * Sends 200 empty lines to push the chat history out of view. {@code all} clears
 * the chat for every online player; a nickname clears it only for that player.
 * Works from the console too.
 * <p>
 * Permission: {@code ui.command.clearchat} (registered in {@code Permissions} — in code, not plugin.yml).
 */
public final class ClearChatSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.clearchat";

    /** How many empty lines are sent to scroll the chat history out of view. */
    private static final int EMPTY_LINES = 200;

    @Override
    public String getName() {
        return "clearchat";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>Usage: </yellow><white>/ui clearchat <player|all></white>"));
            return true;
        }

        String target = args[1];

        // all — clear the chat for everyone
        if (target.equalsIgnoreCase("all")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearChat(player);
            }
            sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Chat cleared for everyone.</white>"));
            return true;
        }

        // nickname — clear the chat only for that player
        Player targetPlayer = Bukkit.getPlayerExact(target);
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player </red><yellow>" + target + "</yellow><red> is not online!</red>"));
            return true;
        }

        clearChat(targetPlayer);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Chat cleared for </white><yellow>" + targetPlayer.getName()
                        + "</yellow><white>.</white>"));
        return true;
    }

    /**
     * Sends {@value EMPTY_LINES} empty lines to the player, scrolling the old
     * chat history out of the visible window.
     */
    private void clearChat(Player player) {
        for (int i = 0; i < EMPTY_LINES; i++) {
            player.sendMessage("");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 2) return List.of();

        String partial = args[1].toLowerCase();
        List<String> result = new ArrayList<>();

        if ("all".startsWith(partial)) {
            result.add("all");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(partial)) {
                result.add(player.getName());
            }
        }
        return result;
    }
}
