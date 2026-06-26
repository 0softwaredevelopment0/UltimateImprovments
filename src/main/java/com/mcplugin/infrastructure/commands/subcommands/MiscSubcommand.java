package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.commands.home.HomeCommand;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.mechanics.features.items.NotesGUI;
import com.mcplugin.mechanics.features.player.VanishManager;
import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import com.mcplugin.mechanics.features.player.ElytraBoostManager;
import com.mcplugin.mechanics.features.items.AutoCraftManager;
import com.mcplugin.mechanics.features.world.MinecartSpeedManager;
import com.mcplugin.infrastructure.util.MessageUtil;
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
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_enabled", "<green>✔</green> <white>Player</white> <yellow>{player}</yellow> <white>is now hidden (vanished).</white>").replace("{player}", targetName)));
            if (!target.isOnline()) sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_offline_hint", "<gray>Player is offline — vanish will apply on next login.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_disabled", "<red>❌</red> <white>Player</white> <yellow>{player}</yellow> <white>is no longer hidden.</white>").replace("{player}", targetName)));
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
    // TOGGLEFLY
    // =========================
    public static boolean toggleFly(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        UUID uuid = player.getUniqueId();
        ElytraBoostManager.toggleFlyEnabled(uuid);
        if (ElytraBoostManager.isFlyEnabled(uuid)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.fly_enabled", "<green>✦</green> <white>Elytra boost on jump: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.fly_disabled", "<red>✦</red> <white>Elytra boost on jump: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // TOGGLERADVIEW
    // =========================
    public static boolean toggleRadView(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>")); return true; }
        if (!player.hasPermission("mcplugin.command.toggleradview")) { player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission!</red>")); return true; }
        RadiationManager.toggleRadView(player);
        if (RadiationManager.isRadViewEnabled(player)) {
            player.sendMessage(MessageUtil.parse("<green>☢</green> <white>Radiation display: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>☢</red> <white>Radiation display: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // TOGGLEAUTOCRAFT
    // =========================
    public static boolean toggleAutoCraft(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); return true; }
        if (!player.hasPermission("mcplugin.autocraft")) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission!</red>"))); return true; }
        AutoCraftManager.toggleAutoCraft(player);
        return true;
    }

    // =========================
    // HOME (delegates to HomeCommand)
    // =========================
    public static boolean home(CommandSender sender, String[] args) {
        return HomeCommand.dispatch(sender, args);
    }
}
