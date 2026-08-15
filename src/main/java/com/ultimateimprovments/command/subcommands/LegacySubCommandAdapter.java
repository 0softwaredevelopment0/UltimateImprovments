package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

/**
 * Adapter for migrating from static subcommands to the {@link SubCommand} interface.
 *
 * <p>Allows registering existing subcommands in {@code SubCommandRegistry}
 * without immediately refactoring each class. For new commands use
 * {@code class MyCmd implements SubCommand} directly.</p>
 *
 * <p>Tab-complete: use {@link #tc(BiFunction)} to build tab completion:
 * <pre>{@code
 * LegacySubCommandAdapter.of("check", CheckSubcommand::execute,
 *     LegacySubCommandAdapter.tc((s, a) -> {
 *         if (a.length == 2) return onlinePlayerNames();
 *         return List.of();
 *     }));
 * }</pre>
 */
public class LegacySubCommandAdapter implements SubCommand {

    private final String name;
    private final BiFunction<CommandSender, String[], Boolean> executor;
    private final BiFunction<CommandSender, String[], List<String>> tabCompleter;
    private final List<String> aliases;

    private LegacySubCommandAdapter(String name,
                                     BiFunction<CommandSender, String[], Boolean> executor,
                                     BiFunction<CommandSender, String[], List<String>> tabCompleter,
                                     List<String> aliases) {
        this.name = name;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
        this.aliases = aliases;
    }

    // ── Factory methods ──

    /** Without tab completion or aliases. */
    public static LegacySubCommandAdapter of(String name,
                                              BiFunction<CommandSender, String[], Boolean> executor) {
        return new LegacySubCommandAdapter(name, executor, (s, a) -> List.of(), List.of());
    }

    /** With tab completion, no aliases. */
    public static LegacySubCommandAdapter of(String name,
                                              BiFunction<CommandSender, String[], Boolean> executor,
                                              BiFunction<CommandSender, String[], List<String>> tabCompleter) {
        return new LegacySubCommandAdapter(name, executor, tabCompleter, List.of());
    }

    /** With tab completion and aliases. */
    public static LegacySubCommandAdapter of(String name,
                                              BiFunction<CommandSender, String[], Boolean> executor,
                                              BiFunction<CommandSender, String[], List<String>> tabCompleter,
                                              List<String> aliases) {
        return new LegacySubCommandAdapter(name, executor, tabCompleter, aliases);
    }

    /**
     * Helper for creating a tab-complete function.
     * <pre>{@code
     * LegacySubCommandAdapter.tc((s, a) -> {
     *     if (a.length == 2) return List.of("opt1", "opt2");
     *     return List.of();
     * })
     * }</pre>
     */
    public static BiFunction<CommandSender, String[], List<String>> tc(
            BiFunction<CommandSender, String[], List<String>> completer) {
        return completer;
    }

    // ── SubCommand ──

    @Override
    public String getName() { return name; }

    @Override
    public List<String> getAliases() { return aliases; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return executor.apply(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (tabCompleter == null) return List.of();
        return tabCompleter.apply(sender, args);
    }
}
