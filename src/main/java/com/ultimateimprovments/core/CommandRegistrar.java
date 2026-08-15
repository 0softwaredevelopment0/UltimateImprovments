package com.ultimateimprovments.core;

import com.ultimateimprovments.command.MsgCommand;
import com.ultimateimprovments.command.PluginReloadCommand;
import com.ultimateimprovments.command.PowerCommand;
import com.ultimateimprovments.command.TrollCommand;
import com.ultimateimprovments.command.VanishListCommand;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.energy.generation.reactor.ReactorCommand;

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
        ConsoleLogger.info("[Commands] Registering commands...");
        int registered = 0;
        int total = 0;

        PluginReloadCommand uiCmd = new PluginReloadCommand();
        total++; registered += register(plugin, "ui", uiCmd, uiCmd) ? 1 : 0; // main command
        total++; registered += register(plugin, "ultimateimprovments", uiCmd, uiCmd) ? 1 : 0; // alias
        total++; registered += register(plugin, "reactor", new ReactorCommand(), null) ? 1 : 0;

        // Fake commands to troll hackers (settings in config.yml → troll:)
        TrollCommand trollCmd = new TrollCommand();
        total++; registered += register(plugin, "forceop", trollCmd, trollCmd) ? 1 : 0; // fake OP grant
        total++; registered += register(plugin, "crash", trollCmd, trollCmd) ? 1 : 0;   // fake server crash + kick
        total++; registered += registerOverride(plugin, "list", new VanishListCommand()) ? 1 : 0;
        total++; registered += registerOverride(plugin, "stop", new PowerCommand("stop", false)) ? 1 : 0;
        total++; registered += registerOverride(plugin, "restart", new PowerCommand("restart", true)) ? 1 : 0;

        // Override vanilla /msg /tell /w /reply /r with the custom PM format
        total++; registered += registerOverride(plugin, "msg", new MsgCommand("msg", false)) ? 1 : 0;
        total++; registered += registerOverride(plugin, "tell", new MsgCommand("tell", false)) ? 1 : 0;
        total++; registered += registerOverride(plugin, "w", new MsgCommand("w", false)) ? 1 : 0;
        total++; registered += registerOverride(plugin, "reply", new MsgCommand("reply", true)) ? 1 : 0;
        total++; registered += registerOverride(plugin, "r", new MsgCommand("r", true)) ? 1 : 0;

        int failed = total - registered;
        if (failed > 0) {
            ConsoleLogger.warn("[Commands] Registered " + registered + "/" + total + " commands, " + failed + " failed.");
        } else {
            ConsoleLogger.info("[Commands] Registered " + registered + " commands.");
        }
    }

    /**
     * Registers a command via CommandMap, so no plugin.yml declaration is needed.
     *
     * @return true if the command was registered successfully
     */
    private boolean register(Main plugin, String name, CommandExecutor executor, TabCompleter completer) {
        try {
            Field field = plugin.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap commandMap = (CommandMap) field.get(plugin.getServer());

            BukkitCommand cmd = new BukkitCommand(name, executor, completer);
            commandMap.register(plugin.getName().toLowerCase(), cmd);
            return true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Commands] Failed to register /" + name + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Registers a command by overriding it through the server's CommandMap.
     * Required for built-in server commands like /stop and /restart
     * which cannot be overridden via the standard plugin.yml mechanism.
     *
     * @return true if the command was registered successfully
     */
    private boolean registerOverride(Main plugin, String name, Command command) {
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
            // Paper 1.21+ commandMap has no knownCommands field — that's fine, don't warn.
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
                ConsoleLogger.info("[Commands] KnownCommands not available (Paper 1.21+): " + e.getMessage());
            }

            // Register our command
            commandMap.register(name, plugin.getName().toLowerCase(), command);
            return true;
        } catch (Exception e) {
            ConsoleLogger.warn("[Commands] Failed to override /" + name + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Finds a field in the class and all its superclasses.
     * Returns null if the field is not found (Paper 1.21+ commandMap).
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
