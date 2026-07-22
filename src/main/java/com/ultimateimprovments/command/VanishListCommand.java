package com.ultimateimprovments.command;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.mechanics.features.player.VanishManager;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Overrides /list command to hide vanished players from output.
 * Works for both console and players.
 */
public class VanishListCommand extends Command {

    public VanishListCommand() {
        super("list");
        setDescription("Show list of online players");
        setUsage("/list");
        setPermission(null);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        boolean canSeeVanished = sender.hasPermission("ui.command.vanish");

        List<String> names = new ArrayList<>();
        int vanishedCount = 0;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (VanishManager.isVanished(online.getUniqueId())) {
                vanishedCount++;
                if (canSeeVanished) {
                    names.add(MessageUtil.legacy("<gray>" + online.getDisplayName() + "</gray>"));
                }
            } else {
                names.add(MessageUtil.legacy("<white>" + online.getDisplayName() + "</white>"));
            }
        }

        int totalOnline = Bukkit.getOnlinePlayers().size();
        int visibleCount = totalOnline - (canSeeVanished ? 0 : vanishedCount);

        sender.sendMessage(MessageUtil.legacy(MessagesManager.getString("vanish_list.online_players",
                "<gray>Online: </gray><white>%visible%<gray>/%max%</gray></white><gray> players:</gray>")
                .replace("%visible%", String.valueOf(visibleCount))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))));
        if (!names.isEmpty()) {
            sender.sendMessage(String.join(MessageUtil.legacy("<gray>, </gray>"), names));
        }
        if (vanishedCount > 0 && canSeeVanished) {
            sender.sendMessage(MessageUtil.legacy(MessagesManager.getString("vanish_list.vanished_count",
                    "<gray>(%count% vanished)</gray>")
                    .replace("%count%", String.valueOf(vanishedCount))));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }
}
