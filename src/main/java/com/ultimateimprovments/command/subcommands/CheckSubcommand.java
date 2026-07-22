package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.security.check.CheckManager;
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

        if (!inspector.hasPermission("ui.command.check")) {
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
                    "<red>❌ Player</red> <yellow>%player%</yellow> <red>not found!</red>")
                    .replace("%player%", targetName)));
            return true;
        }

        CheckManager.startCheck(inspector, suspect);
        return true;
    }

    /**
     * /mp uncheck [player] — завершить проверку читов
     * <p>
     * Без аргумента — завершает текущую проверку (force-end, если suspect офлайн).
     * С аргументом — завершает проверку указанного игрока.
     */
    public static boolean uncheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player inspector)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        if (!inspector.hasPermission("ui.command.check")) {
            inspector.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission",
                    "<red>❌ You don't have permission to use this command!</red>")));
            return true;
        }

        // /mp uncheck (без аргумента) — force-end текущей проверки
        if (args.length < 2 || args[1].isEmpty()) {
            CheckManager.forceEndCheck(inspector);
            return true;
        }

        String targetName = args[1];
        @SuppressWarnings("deprecation")
        Player suspect = Bukkit.getPlayerExact(targetName);

        // Если suspect найден и онлайн — normal end
        if (suspect != null && suspect.isOnline()) {
            CheckManager.endCheck(inspector, suspect);
            return true;
        }

        // Если suspect офлайн — force-end
        inspector.sendMessage(MessageUtil.parse(
                "<yellow>⚠</yellow> <white>Player</white> <yellow>" + targetName +
                "</yellow> <white>is offline. Forcing check end...</white>"));
        CheckManager.forceEndCheck(inspector);
        return true;
    }
}
