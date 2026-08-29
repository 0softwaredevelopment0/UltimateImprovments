package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.chat.ChatChannel;
import com.ultimateimprovments.chat.ChatManager;
import com.ultimateimprovments.chat.PlayerChannelManager;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.mechanics.security.check.CheckManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ui chatchnl &lt;channel&gt; [player] — switch active chat channel.
 * <p>
 * Channels: local, global, world, private, admin.
 * For private channel a target player name is required.
 * <p>
 * Permissions (per-channel):
 * <ul>
 *   <li>{@code ui.chat.channel.local}</li>
 *   <li>{@code ui.chat.channel.global}</li>
 *   <li>{@code ui.chat.channel.world}</li>
 *   <li>{@code ui.chat.channel.private}</li>
 *   <li>{@code ui.chat.channel.admin}</li>
 * </ul>
 * Base command permission: {@code ui.command.chatchnl}
 */
public final class ChatChannelSubcommand implements SubCommand {

    @Override
    public String getName() { return "chatchnl"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>This command can only be used by players.</red>"));
            return true;
        }

        if (!player.hasPermission("ui.command.chatchnl")) {
            CommandErrors.noPermission(player);
            return true;
        }

        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String channelName = args[1].toLowerCase();
        ChatChannel channel = ChatChannel.fromConfigKey(channelName);

        if (channel == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>\u274c Unknown channel: </red><yellow>" + channelName + "</yellow>"
                    + "<gray>. Available: </gray><white>local, global, world, private, admin, check</white>"));
            return true;
        }

        // Check channel — for a player under an anti-cheat check: toggle it so their
        // messages go ONLY to the inspector. Requires the base command permission and
        // being actually checked (a normal player or moderator off-check can't enable it).
        if (channel == ChatChannel.CHECK) {
            if (!CheckManager.isBeingChecked(player)) {
                player.sendMessage(MessageUtil.parse(
                        "<red>\u274c You are not under a check — you can't use the check channel.</red>"));
                return true;
            }
            if (PlayerChannelManager.getChannel(player) == ChatChannel.CHECK) {
                PlayerChannelManager.setChannel(player, ChatChannel.GLOBAL);
                player.sendMessage(MessageUtil.parse(
                        "<green>\u2714</green> <white>Check channel <red>off</red> — chat restored.</white>"));
            } else {
                PlayerChannelManager.setChannel(player, ChatChannel.CHECK);
                player.sendMessage(MessageUtil.parse(
                        "<green>\u2714</green> <white>Check channel <red>on</red> — messages go only to the inspector.</white>"));
            }
            return true;
        }

        // Check permission for this channel
        if (!player.hasPermission(channel.getPermission()) && !player.isOp()) {
            player.sendMessage(MessageUtil.parse(
                    "<red>\u274c You don't have permission to use the </red><yellow>" + channel.getConfigKey() + "</yellow><red> channel.</red>"));
            return true;
        }

        // Private channel requires a target
        if (channel == ChatChannel.PRIVATE) {
            if (args.length < 3) {
                player.sendMessage(MessageUtil.parse(
                        "<red>\u274c Usage: </red><white>/ui chatchnl private &lt;player&gt;</white>"));
                return true;
            }
            String targetName = args[2];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage(MessageUtil.parse(
                        "<red>\u274c Player </red><yellow>" + targetName + "</yellow><red> is not online!</red>"));
                return true;
            }
            PlayerChannelManager.setPrivateTarget(player, target.getName());
        }

        PlayerChannelManager.setChannel(player, channel);

        // Build success message
        String channelLabel = channel.getConfigKey().substring(0, 1).toUpperCase() + channel.getConfigKey().substring(1);
        String extra = "";
        if (channel == ChatChannel.PRIVATE) {
            extra = " <gray>\u2192 </gray><yellow>" + args[2] + "</yellow>";
        } else if (channel == ChatChannel.LOCAL) {
            extra = " <gray>(radius: " + ChatManager.getLocalRadius() + " blocks)</gray>";
        }

        player.sendMessage(MessageUtil.parse(
                "<green>\u2714</green> <white>Chat channel set to </white><yellow>" + channelLabel + "</yellow>" + extra));
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(MessageUtil.parse(
                "<yellow>Usage: </yellow><white>/ui chatchnl &lt;channel&gt; [player]</white>"));
        player.sendMessage(MessageUtil.parse(
                "<gray>Channels: </gray><white>local, global, world, private, admin, check</white>"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("ui.command.chatchnl")) return List.of();

        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Arrays.stream(ChatChannel.values())
                    .map(ChatChannel::getConfigKey)
                    .filter(k -> k.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("private")) {
            String partial = args[2].toLowerCase();
            List<String> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    result.add(p.getName());
                }
            }
            return result;
        }

        return List.of();
    }
}
