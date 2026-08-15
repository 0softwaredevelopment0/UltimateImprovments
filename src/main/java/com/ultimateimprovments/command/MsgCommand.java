package com.ultimateimprovments.command;

import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Overrides the vanilla {@code /msg} (and {@code /tell}, {@code /w}, {@code /reply}, {@code /r})
 * commands with a custom MiniMessage format:
 *
 * <ul>
 *   <li>Sender sees: {@code [<target> » You] <message>}</li>
 *   <li>Receiver sees: {@code [You » <sender>] <message>}</li>
 * </ul>
 */
public class MsgCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Tracks the last message partner per player so /reply keeps working. */
    private static final Map<UUID, UUID> LAST_MESSAGED = new ConcurrentHashMap<>();

    private final boolean reply;

    public MsgCommand(String name, boolean reply) {
        super(name);
        this.reply = reply;
        setDescription("Send a private message");
        setPermission(null); // same as vanilla default — everyone can use it
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>Only players can use this command.</red>"));
            return true;
        }

        if (reply) {
            UUID targetId = LAST_MESSAGED.get(player.getUniqueId());
            if (targetId == null) {
                player.sendMessage(MessageUtil.parse("<red>You have no one to reply to.</red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                player.sendMessage(MessageUtil.parse("<red>That player is offline.</red>"));
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/" + label + " <message></white>"));
                return true;
            }
            sendMessage(player, target, join(args, 0));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/" + label + " <player> <message></white>"));
            return true;
        }

        Player target = findPlayer(args[0]);
        if (target == null) {
            player.sendMessage(MessageUtil.parse("<red>Player not found: </red><white>" + args[0] + "</white>"));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(MessageUtil.parse("<red>You can't send a private message to yourself!</red>"));
            return true;
        }

        sendMessage(player, target, join(args, 1));
        return true;
    }

    private void sendMessage(Player sender, Player target, String message) {
        String escaped = escape(message);

        // Sender: [target » You] ; Receiver: [You » sender]
        String senderView = "<white>[<gray>" + target.getName() + " <yellow>» <gray>You<white>] <reset>" + escaped;
        String targetView = "<white>[<gray>You<yellow> » <gray>" + sender.getName() + "<white>] <reset>" + escaped;

        sender.sendMessage(MM.deserialize(senderView));
        target.sendMessage(MM.deserialize(targetView));

        // Ping sound so the recipient notices the incoming message
        SoundUtil.playSound(target, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // Remember the partner for /reply in both directions
        LAST_MESSAGED.put(sender.getUniqueId(), target.getUniqueId());
        LAST_MESSAGED.put(target.getUniqueId(), sender.getUniqueId());
    }

    /**
     * Makes the player's message safe for MiniMessage:
     * escapes tags and strips legacy {@code §}-codes (MiniMessage throws on them).
     */
    private String escape(String message) {
        if (message == null) return "";
        return message.replace("<", "\\<")
                .replace(">", "\\>")
                .replaceAll("\u00A7[0-9a-fk-orx]", "")
                .replace("\u00A7", "");
    }

    private Player findPlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (reply) return Collections.emptyList();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    result.add(p.getName());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
