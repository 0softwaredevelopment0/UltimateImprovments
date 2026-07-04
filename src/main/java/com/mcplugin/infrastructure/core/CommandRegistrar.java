package com.mcplugin.infrastructure.core;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.commands.PluginReloadCommand;
import com.mcplugin.infrastructure.commands.PowerCommand;
import com.mcplugin.infrastructure.commands.VanishListCommand;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.energy.generation.reactor.ReactorCommand;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.TabCompleter;

import java.lang.reflect.Field;
import java.util.Map;

public class CommandRegistrar {

    private static CommandRegistrar instance;

    public static void init(Main plugin) {
        instance = new CommandRegistrar();
    }

    public static CommandRegistrar getInstance() {
        return instance;
    }

    // =========================
    // REGISTER COMMANDS
    // =========================
    public void registerAll(Main plugin) {
        PluginReloadCommand mpCmd = new PluginReloadCommand();
        register(plugin, "mp", mpCmd);
        registerTab(plugin, "mp", mpCmd);
        register(plugin, "reactor", new ReactorCommand());
        registerOverride(plugin, "list", new VanishListCommand());
        registerOverride(plugin, "stop", new PowerCommand("stop", false));
        registerOverride(plugin, "restart", new PowerCommand("restart", true));
    }

    private void register(Main plugin, String name, CommandExecutor executor) {
        if (plugin.getCommand(name) == null) {
            ConsoleLogger.warn("Command /" + name + " not found!");
            return;
        }
        plugin.getCommand(name).setExecutor(executor);
    }

    /**
     * Registers a command by overriding it through the server's CommandMap.
     * Required for built-in server commands like /stop and /restart
     * which cannot be overridden via the standard plugin.yml mechanism.
     */
    private void registerOverride(Main plugin, String name, Command command) {
        try {
            Field field = plugin.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap commandMap = (CommandMap) field.get(plugin.getServer());

            // Unregister existing command
            Command existing = commandMap.getCommand(name);
            if (existing != null) {
                existing.unregister(commandMap);
            }

            // Remove from knownCommands to avoid conflicts (Bukkit/Spigot legacy).
            // Paper 1.21+ commandMap не имеет поля knownCommands — это нормально, не варним.
            try {
                Field knownFields = findField(commandMap.getClass(), "knownCommands");
                if (knownFields != null) {
                    knownFields.setAccessible(true);
                    Map<String, Command> knownCommands = (Map<String, Command>) knownFields.get(commandMap);
                    knownCommands.remove(name);
                    knownCommands.remove("bukkit:" + name);
                    knownCommands.remove("minecraft:" + name);
                }
            } catch (Exception e) {
                ConsoleLogger.info("[COMMANDS] KnownCommands not available (Paper 1.21+): " + e.getMessage());
            }

            // Register our command
            commandMap.register(name, plugin.getName().toLowerCase(), command);
            ConsoleLogger.info("[COMMANDS] Registered /" + name + " (overridden via CommandMap)");
        } catch (Exception e) {
            ConsoleLogger.warn("[COMMANDS] Failed to override /" + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ищет поле в классе и всех его суперклассах.
     * Возвращает null если поле не найдено (Paper 1.21+ commandMap).
     */
    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void registerTab(Main plugin, String name, TabCompleter completer) {
        if (plugin.getCommand(name) == null) {
            ConsoleLogger.warn("Command /" + name + " not found!");
            return;
        }
        plugin.getCommand(name).setTabCompleter(completer);
    }
}
