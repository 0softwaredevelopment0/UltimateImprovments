package com.ultimateimprovments.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Unified interface for all /ui subcommands.
 *
 * <p>Each subcommand is a separate class with execute() and tabComplete().
 * It is registered in {@link SubCommandRegistry} and automatically picked up
 * by the {@link PluginReloadCommand} dispatcher.</p>
 */
public interface SubCommand {

    /**
     * Executes the subcommand.
     *
     * @param sender the command sender (player or console)
     * @param args   the full argument array (args[0] — the subcommand name)
     * @return true if the command was handled
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Returns tab-complete suggestions for this subcommand.
     *
     * @param sender the sender
     * @param args   the full argument array
     * @return a list of suggestions or an empty list
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    /**
     * Returns the subcommand name (case-insensitive).
     * By default — the class name in lowercase.
     */
    default String getName() {
        return getClass().getSimpleName()
                .replace("Subcommand", "")
                .toLowerCase();
    }

    /**
     * Returns the alias list (additional names) for this subcommand.
     */
    default List<String> getAliases() {
        return Collections.emptyList();
    }
}
