package com.mcplugin.commands.subcommands;

import com.mcplugin.Main;
import com.mcplugin.main.TaskManager;
import com.mcplugin.module.ModuleManager;
import com.mcplugin.structure.StructureTemplate;
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
                    com.mcplugin.listeners.PowerInterceptListener.reloadConfigStatic();
                    com.mcplugin.listeners.ChatFilterManager.reloadConfigStatic();

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
