package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.module.meteor.MeteorModule;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

/**
 * /ui meteor spawn <count> — force-spawns meteors.
 * <p>
 * Requires the permission: ui.command.meteor.spawn
 * The module must be enabled in config.yml (meteor.enabled: true).
 */
public final class MeteorSubcommand {

    private MeteorSubcommand() {}

    /**
     * /ui meteor <spawn> [count]
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui meteor spawn [count]</white>"));
            return true;
        }

        String sub = args[1].toLowerCase();
        return switch (sub) {
            case "spawn" -> handleSpawn(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Unknown meteor subcommand: </red><white>" + sub + "</white>"));
                sender.sendMessage(MessageUtil.parse("<red>Usage: </red><white>/ui meteor spawn [count]</white>"));
                yield true;
            }
        };
    }

    private static boolean handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.meteor.spawn")) {
            CommandErrors.noPermission(sender);
            return true;
        }

        int count = 1;
        if (args.length > 2) {
            try {
                count = Integer.parseInt(args[2]);
                if (count < 1) count = 1;
                if (count > 100) count = 100;
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Invalid count! Usage: </red><white>/ui meteor spawn [count]</white>"));
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

        int spawned = module.spawnMeteors(count);
        if (spawned < count) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠ Spawned </yellow><white>" + spawned + "</white><yellow> of </yellow><white>"
                    + count + "</white><yellow> meteor(s) — active limit reached.</yellow>"
            ));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Spawned </white><yellow>" + spawned + "</yellow><white> meteor(s)!</white>"
            ));
        }

        return true;
    }
}
