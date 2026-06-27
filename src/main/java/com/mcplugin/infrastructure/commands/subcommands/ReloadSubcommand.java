package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.core.TaskManager;
import com.mcplugin.infrastructure.modules.ModuleManager;
import com.mcplugin.infrastructure.util.StructureTemplate;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class ReloadSubcommand {

    private ReloadSubcommand() {}

    public static boolean execute(CommandSender sender) {
        // Проверка прав: консоль может всё, игрок — только с пермишеном
        if (sender instanceof Player player && !player.hasPermission("mcplugin.command.reload")) {
            player.sendMessage("§4❌ §cError: §7You don't have permission!");
            return true;
        }

        sender.sendMessage("§eReloading MC-Plugin...");
        Main plugin = Main.getInstance();

        // Если отправитель — консоль, выполняем reload сразу (уже на главном потоке)
        if (sender instanceof ConsoleCommandSender) {
            executeReload(sender, plugin);
            return true;
        }

        // Если отправитель — игрок, планируем на главный поток (защита от асинхронных команд)
        new BukkitRunnable() {
            @Override
            public void run() {
                executeReload(sender, plugin);
            }
        }.runTask(plugin);
        return true;
    }

    /**
     * Выполняет перезагрузку плагина: выключает не-essential модули,
     * перезагружает конфиги, включает модули обратно.
     * <p>
     * ДОЛЖЕН вызываться только с главного потока сервера!
     */
    private static void executeReload(CommandSender sender, Main plugin) {
        try {
            long start = System.currentTimeMillis();
            TaskManager.getInstance().stopAll();

            var mm = ModuleManager.getInstance();
            for (var m : mm.getModules()) {
                if (!m.isEssential()) m.disable(plugin);
            }

            plugin.reloadConfig();
            mm.reloadAllConfigs();
            MessagesManager.reload();
            com.mcplugin.infrastructure.listeners.PowerInterceptListener.reloadConfigStatic();
            com.mcplugin.infrastructure.listeners.ChatFilterManager.reloadConfigStatic();

            for (var m : mm.getModules()) {
                if (!m.isEssential()) m.initialize(plugin);
            }

            StructureTemplate.initAll();
            TaskManager.getInstance().startAll(plugin);

            long time = System.currentTimeMillis() - start;
            sender.sendMessage("§2✔ §aSuccess: §7Reload complete.");
            sender.sendMessage("§2✔ §aSuccess: §7Reload time: §e" + time + "ms");
            plugin.getLogger().info("[MCPLUGIN] Reload complete in " + time + "ms");
        } catch (Exception e) {
            sender.sendMessage("§4❌ §cError: §7Reload failed! Check console.");
            plugin.getLogger().severe("[MCPLUGIN] Reload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
