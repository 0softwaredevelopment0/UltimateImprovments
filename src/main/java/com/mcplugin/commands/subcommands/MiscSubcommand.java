package com.mcplugin.commands.subcommands;

import com.mcplugin.commands.home.HomeCommand;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.features.notes.NotesGUI;
import com.mcplugin.features.vanish.VanishManager;
import com.mcplugin.features.minecartspeed.MinecartSpeedManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class MiscSubcommand {

    private MiscSubcommand() {}

    // =========================
    // VANISH
    // =========================
    public static boolean vanish(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.vanish")) {
            p.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_no_permission", "<red>❌ You don't have permission to use vanish!</red>"))); return true;
        }
        if (args.length < 2) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_usage", "<red>❌ Usage: </red><white>/mp vanish <nick></white>"))); return true; }
        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found", "<red>❌ Player</red> <yellow>{player}</yellow> <red>not found!</red>").replace("{player}", targetName))); return true;
        }
        UUID uuid = target.getUniqueId();
        VanishManager.toggleVanish(target);
        boolean isVanished = VanishManager.isVanished(uuid);
        if (isVanished) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_enabled", "<green>✅</green> <white>Player</white> <yellow>{player}</yellow> <white>is now hidden (vanished).</white>").replace("{player}", targetName)));
            if (!target.isOnline()) sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_offline_hint", "<gray>Player is offline — vanish will apply on next login.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_disabled", "<red>✗</red> <white>Player</white> <yellow>{player}</yellow> <white>is no longer hidden.</white>").replace("{player}", targetName)));
        }
        return true;
    }

    // =========================
    // NOTES
    // =========================
    public static boolean notes(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.notes_player_only", "<red>❌ Only players can use notes!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.command.notes")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.notes_no_permission", "<red>❌ You don't have permission to use notes!</red>"))); return true; }
        NotesGUI.openMainGUI(player);
        return true;
    }

    // =========================
    // TOGGLESPEED
    // =========================
    public static boolean toggleSpeed(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        MinecartSpeedManager.toggleSpeedDisplay(uuid);
        if (MinecartSpeedManager.isSpeedDisplayEnabled(uuid)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_enabled", "<green>⚡</green> <white>Speed display: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_disabled", "<red>⚡</red> <white>Speed display: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // HOME (delegates to HomeCommand)
    // =========================
    public static boolean home(CommandSender sender, String[] args) {
        return HomeCommand.dispatch(sender, args);
    }
}
