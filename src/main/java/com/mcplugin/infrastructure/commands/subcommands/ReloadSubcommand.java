package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.core.PluginShutdown;
import com.mcplugin.infrastructure.core.PluginStartup;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class ReloadSubcommand {

    private ReloadSubcommand() {}

    public static boolean execute(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("mcplugin.command.reload")) {
            player.sendMessage("§4❌ §cError: §7You don't have permission!");
            return true;
        }

        sender.sendMessage("§eReloading MC-Plugin...");
        Main plugin = Main.getInstance();

        if (sender instanceof ConsoleCommandSender) {
            executeReload(sender, plugin);
            return true;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                executeReload(sender, plugin);
            }
        }.runTask(plugin);
        return true;
    }

    /**
     * ПОЛНЫЙ disable + enable плагина:
     * 1. PluginShutdown — останавливает всё (модули → сохранение → задачи → чистка)
     * 2. reloadConfig
     * 3. PluginStartup — запускает всё заново (инфра → модули → пост-системы → финиш)
     */
    private static void executeReload(CommandSender sender, Main plugin) {
        try {
            long start = System.currentTimeMillis();

            // 1. Полный shutdown
            new PluginShutdown(plugin).shutdownPlugin();

            // 2. Перезагрузка конфига
            plugin.reloadConfig();

            // 3. Полный startup
            new PluginStartup(plugin).startupPlugin();

            long time = System.currentTimeMillis() - start;
            sender.sendMessage("§2✔ §aSuccess: §7Reload complete.");
            sender.sendMessage("§2✔ §aSuccess: §7Reload time: §e" + time + "ms");
            ConsoleLogger.info("[MCPLUGIN] Reload complete in " + time + "ms");
        } catch (Exception e) {
            sender.sendMessage("§4❌ §cError: §7Reload failed! Check console.");
            ConsoleLogger.error("[MCPLUGIN] Reload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
