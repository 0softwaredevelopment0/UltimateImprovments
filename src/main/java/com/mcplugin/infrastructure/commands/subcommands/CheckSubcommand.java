package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.mechanics.security.check.CheckManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CheckSubcommand {

    private CheckSubcommand() {}

    /**
     * /mp check <player> — вызвать игрока на проверку читов
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player inspector)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!inspector.hasPermission("mcplugin.command.check")) {
            inspector.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp check <nick></white>"));
            return true;
        }

        String targetName = args[1];
        @SuppressWarnings("deprecation")
        Player suspect = Bukkit.getPlayerExact(targetName);

        if (suspect == null || !suspect.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_not_found",
                    "<red>❌ Player</red> <yellow>{player}</yellow> <red>not found!</red>")
                    .replace("{player}", targetName)));
            return true;
        }

        CheckManager.startCheck(inspector, suspect);
        return true;
    }

    /**
     * /mp uncheck <player> — завершить проверку читов
     */
    public static boolean uncheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player inspector)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!inspector.hasPermission("mcplugin.command.check")) {
            inspector.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        if (args.length < 2) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp uncheck <nick></white>"));
            return true;
        }

        String targetName = args[1];
        @SuppressWarnings("deprecation")
        Player suspect = Bukkit.getPlayerExact(targetName);

        if (suspect == null || !suspect.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_not_found",
                    "<red>❌ Player</red> <yellow>{player}</yellow> <red>not found!</red>")
                    .replace("{player}", targetName)));
            return true;
        }

        CheckManager.endCheck(inspector, suspect);
        return true;
    }
}
