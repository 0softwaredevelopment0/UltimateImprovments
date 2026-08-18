package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /ui invsee &lt;player&gt; — unified inventory viewer/editor.
 *
 * <p>Online players open the live {@link InvseeCommand} GUI (Bukkit API). Offline
 * players open a snapshot GUI backed by their {@code <uuid>.dat} file
 * ({@link OfflineInvEditor}); with {@code ui.command.inv.edit} changes are written
 * back and the file is backed up to {@code <uuid>-backup.dat}.</p>
 *
 * <p>{@link EnderseeSubcommand} reuses this class for the ender chest.</p>
 */
public class InvseeSubcommand implements SubCommand {

    private final String name;
    private final String permission;
    private final boolean ender;

    public InvseeSubcommand() {
        this("invsee", "ui.command.invsee", false);
    }

    protected InvseeSubcommand(String name, String permission, boolean ender) {
        this.name = name;
        this.permission = permission;
        this.ender = ender;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }
        if (!player.hasPermission(permission)) {
            CommandErrors.noPermission(player);
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui " + name + " <player></white>"));
            return true;
        }

        // ── Online: reuse the live invsee GUI (Bukkit API) ──
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target != null && target.isOnline()) {
            if (ender) {
                InvseeCommand.openEnder(player, target);
            } else {
                InvseeCommand.openInvsee(player, target);
            }
            return true;
        }

        // ── Offline: edit the .dat file ──
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
        UUID uuid = offline.getUniqueId();

        if (!OfflineInvEditor.hasDataFile(uuid)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ No data file found for player</red> <yellow>" + args[1] + "</yellow><red>.</red>"));
            return true;
        }

        String targetName = offline.getName() != null ? offline.getName() : args[1];
        OfflineInvEditor.open(player, uuid, targetName, ender);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> result = new ArrayList<>();
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    result.add(p.getName());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
