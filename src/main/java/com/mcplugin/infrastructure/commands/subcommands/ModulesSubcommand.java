package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.modules.ModuleManager;
import org.bukkit.command.CommandSender;

public final class ModulesSubcommand {

    private ModulesSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp modules list|enable|disable §7<модуль>");
            return true;
        }

        var mm = ModuleManager.getInstance();

        return switch (args[1].toLowerCase()) {
            case "list" -> handleList(sender, mm);
            case "enable" -> handleEnable(sender, args, mm);
            case "disable" -> handleDisable(sender, args, mm);
            default -> {
                sender.sendMessage("§4❌ §cИспользование: §f/mp modules list|enable|disable §7<модуль>");
                yield true;
            }
        };
    }

    private static boolean handleList(CommandSender sender, ModuleManager mm) {
        sender.sendMessage("§6═══════════════════════");
        sender.sendMessage("§6  ✦ §fМодули MC-Plugin");
        sender.sendMessage("§6═══════════════════════");
        for (var m : mm.getModules()) {
            String status = m.isEnabled() ? "§a✔" : "§c✘";
            String essential = m.isEssential() ? " §8[§eядро§8]" : "";
            sender.sendMessage(status + " §f" + m.getName() + essential);
        }
        sender.sendMessage("§6═══════════════════════");
        return true;
    }

    private static boolean handleEnable(CommandSender sender, String[] args, ModuleManager mm) {
        if (!sender.hasPermission("*") && !sender.isOp()) {
            sender.sendMessage("§4❌ §cУ вас нет прав на управление модулями!");
            return true;
        }
        if (args.length < 3) { sender.sendMessage("§4❌ §cИспользование: §f/mp modules enable §7<имя модуля>"); return true; }
        String name = args[2];
        boolean ok = mm.enableModule(name);
        var m = mm.getModule(name);
        if (ok && m != null && m.isEnabled()) {
            sender.sendMessage("§a✅ §fМодуль §e" + name + "§f включён.");
            Main.getInstance().getLogger().info("[CMD] " + sender.getName() + " enabled module: " + name);
        } else {
            sender.sendMessage("§4❌ §cНе удалось включить модуль §e" + name + "§c! Проверьте имя.");
        }
        return true;
    }

    private static boolean handleDisable(CommandSender sender, String[] args, ModuleManager mm) {
        if (!sender.hasPermission("*") && !sender.isOp()) {
            sender.sendMessage("§4❌ §cУ вас нет прав на управление модулями!");
            return true;
        }
        if (args.length < 3) { sender.sendMessage("§4❌ §cИспользование: §f/mp modules disable §7<имя модуля>"); return true; }
        String name = args[2];
        boolean ok = mm.disableModule(name);
        if (ok) {
            sender.sendMessage("§c✘ §fМодуль §e" + name + "§f отключён.");
            Main.getInstance().getLogger().info("[CMD] " + sender.getName() + " disabled module: " + name);
        } else {
            sender.sendMessage("§4❌ §cНе удалось отключить модуль §e" + name + "§c! Проверьте имя или это ядро.");
        }
        return true;
    }
}
