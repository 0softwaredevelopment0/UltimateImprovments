package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
import com.mcplugin.crafting.MultimeterCraftListener;
import com.mcplugin.database.DatabaseManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

public class PluginReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args
    ) {

        // =========================
        // CHECK ARGUMENTS
        // =========================
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§4❌ §cError: §7Usage: /mcplugin reload");
            return true;
        }

        // =========================
        // ONLY PLAYERS (optional, но оставил как у тебя)
        // =========================
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§4❌ §cError: §7Only players can use this command.");
            return true;
        }

        // =========================
        // PERMISSION CHECK
        // =========================
        if (!player.hasPermission("mcplugin.reload")) {
            player.sendMessage("§4❌ §cError: §7You don't have permission!");
            return true;
        }

        player.sendMessage("§eReloading MC-Plugin...");

        Main plugin = Main.getInstance();

        // =========================
        // SAFE ASYNC-LIKE RELOAD TASK (sync runnable)
        // =========================
        new BukkitRunnable() {

            @Override
            public void run() {

                try {

                    long start = System.currentTimeMillis();

                    // =========================
                    // STOP TASKS
                    // =========================
                    plugin.stopTasks();

                    // =========================
                    // SAVE DATA
                    // =========================
                    CableNetwork.save();

                    // =========================
                    // CLOSE DB
                    // =========================
                    DatabaseManager.close();

                    // =========================
                    // RELOAD CONFIG
                    // =========================
                    plugin.reloadConfig();
                    RedstoneGuard.reload();

                    // =========================
                    // RECONNECT DB
                    // =========================
                    DatabaseManager.connect();

                    // =========================
                    // REINIT SYSTEMS
                    // =========================
                    CableNetwork.init();
                    EnergyWorkbenchManager.init();
                    MultimeterCraftListener.init();

                    // =========================
                    // RESTART TASKS
                    // =========================
                    plugin.startTasks();

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