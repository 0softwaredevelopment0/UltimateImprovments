package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.module.meteor.MeteorModule;
import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.command.CommandSender;

/**
 * /mp meteor spawn <count> — форсированный спавн метеоров.
 * <p>
 * Требует permission: mcplugin.command.meteor.spawn
 * Модуль должен быть включён в config.yml (meteor.enabled: true).
 */
public final class MeteorSubcommand {

    private MeteorSubcommand() {}

    /**
     * /mp meteor <spawn> [count]
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp meteor spawn [count]</white>"));
            return true;
        }

        String sub = args[1].toLowerCase();
        return switch (sub) {
            case "spawn" -> handleSpawn(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Unknown meteor subcommand: </red><white>" + sub + "</white>"));
                sender.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/mp meteor spawn [count]</white>"));
                yield true;
            }
        };
    }

    private static boolean handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.command.meteor.spawn")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to spawn meteors!</red>"));
            return true;
        }

        int count = 1;
        if (args.length > 2) {
            try {
                count = Integer.parseInt(args[2]);
                if (count < 1) count = 1;
                if (count > 100) count = 100;
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Invalid count! Usage: </red><white>/mp meteor spawn [count]</white>"));
                return true;
            }
        }

        MeteorModule module = MeteorModule.getInstance();
        if (module == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Meteor module is not initialized!</red>"));
            return true;
        }

        var cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("meteor.enabled", false)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Meteor module is disabled in config.yml!</red>"));
            return true;
        }

        module.spawnMeteors(count);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Spawned </white><yellow>" + count + "</yellow><white> meteor(s)!</white>"
        ));

        return true;
    }
}
