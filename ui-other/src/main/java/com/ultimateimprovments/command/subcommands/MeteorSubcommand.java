package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.module.meteor.MeteorModule;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

/**
 * /ui meteor spawn <count> — force-spawns meteors.
 * <p>
 * Requires the permission: ui.command.meteor.spawn
 * The module must be enabled in the config ({@code [meteor] enabled = true} in
 * {@code configs/UI-World.toml}); texts live in {@code [messages.meteor]} of the
 * same file.
 */
public final class MeteorSubcommand {

    private static final String PERMISSION = "ui.command.meteor.spawn";

    private MeteorSubcommand() {}

    /**
     * /ui meteor <spawn> [count]
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "usage", "<red>❌ Usage: </red><white>/ui meteor spawn [count]</white>");
            return true;
        }

        String sub = args[1].toLowerCase();
        return switch (sub) {
            case "spawn" -> handleSpawn(sender, args);
            default -> {
                send(sender, "unknown_sub",
                        "<red>❌ Unknown meteor subcommand: </red><white>%sub%</white>", "%sub%", sub);
                send(sender, "usage", "<red>❌ Usage: </red><white>/ui meteor spawn [count]</white>");
                yield true;
            }
        };
    }

    private static boolean handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender, PERMISSION);
            return true;
        }

        int count = 1;
        if (args.length > 2) {
            try {
                count = Integer.parseInt(args[2]);
                if (count < 1) count = 1;
                if (count > 100) count = 100;
            } catch (NumberFormatException e) {
                send(sender, "invalid_count",
                        "<red>❌ Invalid count! Usage: </red><white>/ui meteor spawn [count]</white>");
                return true;
            }
        }

        MeteorModule module = MeteorModule.getInstance();
        if (module == null) {
            send(sender, "module_not_init", "<red>❌ Meteor module is not initialized!</red>");
            return true;
        }

        var cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("meteor.enabled", false)) {
            send(sender, "module_disabled",
                    "<red>❌ Meteor module is disabled in configs/UI-World.toml!</red>");
            return true;
        }

        int spawned = module.spawnMeteors(count);
        if (spawned <= 0) {
            send(sender, "spawn_failed",
                    "<red>❌ Could not spawn a meteor — see the server console for the reason.</red>");
        } else if (spawned < count) {
            send(sender, "spawn_partial",
                    "<yellow>⚠ Spawned </yellow><white>%spawned%</white><yellow> of </yellow><white>%count%</white>"
                            + "<yellow> meteor(s) — active limit reached.</yellow>",
                    "%spawned%", String.valueOf(spawned), "%count%", String.valueOf(count));
        } else {
            send(sender, "spawned",
                    "<green>✔</green> <white>Spawned </white><yellow>%count%</yellow><white> meteor(s)!</white>",
                    "%count%", String.valueOf(spawned));
        }

        return true;
    }

    /** Sends one configurable message, replacing {@code %name%} placeholders first. */
    private static void send(CommandSender sender, String key, String def, String... placeholders) {
        String text = MessagesManager.getString("meteor." + key, def);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace(placeholders[i], placeholders[i + 1]);
        }
        sender.sendMessage(MessageUtil.parse(text));
    }
}
