package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.core.TaskManager;
import com.mcplugin.infrastructure.modules.ModuleManager;
import com.mcplugin.infrastructure.util.StructureTemplate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class ReloadSubcommand {

    private ReloadSubcommand() {}

    public static boolean execute(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§4❌ §cError: §7Only players can use this command."); return true; }
        if (!player.hasPermission("mcplugin.command.reload")) { player.sendMessage("§4❌ §cError: §7You don't have permission!"); return true; }

        player.sendMessage("§eReloading MC-Plugin...");
        Main plugin = Main.getInstance();

        new BukkitRunnable() {
            @Override
            public void run() {
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
                    player.sendMessage("§2✔ §aSuccess: §7Reload complete.");
                    player.sendMessage("§2✔ §aSuccess: §7Reload time: §e" + time + "ms");
                    plugin.getLogger().info("[MCPLUGIN] Reload complete in " + time + "ms");
                } catch (Exception e) {
                    player.sendMessage("§4❌ §cError: §7Reload failed! Check console.");
                    plugin.getLogger().severe("[MCPLUGIN] Reload failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTask(plugin);
        return true;
    }
}
