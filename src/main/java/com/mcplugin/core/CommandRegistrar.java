package com.mcplugin.core;

import com.mcplugin.command.PluginReloadCommand;
import com.mcplugin.command.PowerCommand;
import com.mcplugin.command.VanishListCommand;
import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.energy.generation.reactor.ReactorCommand;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.lang.reflect.Field;
import java.util.List;
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
        register(plugin, "mp", mpCmd, mpCmd);
        register(plugin, "reactor", new ReactorCommand(), null);
        registerOverride(plugin, "list", new VanishListCommand());
        registerOverride(plugin, "stop", new PowerCommand("stop", false));
        registerOverride(plugin, "restart", new PowerCommand("restart", true));
    }

    /**
     * Registers a command via CommandMap, so no plugin.yml declaration is needed.
     */
    private void register(Main plugin, String name, CommandExecutor executor, TabCompleter completer) {
        try {
            Field field = plugin.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap commandMap = (CommandMap) field.get(plugin.getServer());

            BukkitCommand cmd = new BukkitCommand(name, executor, completer);
            commandMap.register(plugin.getName().toLowerCase(), cmd);
            ConsoleLogger.info("[COMMANDS] Registered /" + name);
        } catch (Exception e) {
            ConsoleLogger.warn("[COMMANDS] Failed to register /" + name + ": " + e.getMessage());
        }
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

    /**
     * Wraps a CommandExecutor + optional TabCompleter into a Bukkit Command
     * for registration via CommandMap (no plugin.yml needed).
     */
    private static class BukkitCommand extends Command {
        private final CommandExecutor executor;
        private final TabCompleter completer;

        public BukkitCommand(String name, CommandExecutor executor, TabCompleter completer) {
            super(name);
            this.executor = executor;
            this.completer = completer != null ? completer :
                (executor instanceof TabCompleter tc ? tc : null);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return executor.onCommand(sender, this, commandLabel, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (completer != null) {
                List<String> result = completer.onTabComplete(sender, this, alias, args);
                return result != null ? result : super.tabComplete(sender, alias, args);
            }
            return super.tabComplete(sender, alias, args);
        }
    }
}
