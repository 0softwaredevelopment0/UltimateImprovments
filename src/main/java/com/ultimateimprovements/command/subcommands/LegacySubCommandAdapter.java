package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

/**
 * Адаптер для перехода от статических субкоманд к интерфейсу {@link SubCommand}.
 *
 * <p>Позволяет регистрировать существующие субкоманды в {@code SubCommandRegistry}
 * без немедленного рефакторинга каждого класса. Для новых команд используй
 * {@code class MyCmd implements SubCommand} напрямую.</p>
 *
 * <p>Tab-complete: используй {@link #tc(BiFunction)} для создания таб-комплита:
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

    /** Без таб-комплита и алиасов. */
    public static LegacySubCommandAdapter of(String name,
                                              BiFunction<CommandSender, String[], Boolean> executor) {
        return new LegacySubCommandAdapter(name, executor, (s, a) -> List.of(), List.of());
    }

    /** С таб-комплитом, без алиасов. */
    public static LegacySubCommandAdapter of(String name,
                                              BiFunction<CommandSender, String[], Boolean> executor,
                                              BiFunction<CommandSender, String[], List<String>> tabCompleter) {
        return new LegacySubCommandAdapter(name, executor, tabCompleter, List.of());
    }

    /** С таб-комплитом и алиасами. */
    public static LegacySubCommandAdapter of(String name,
                                              BiFunction<CommandSender, String[], Boolean> executor,
                                              BiFunction<CommandSender, String[], List<String>> tabCompleter,
                                              List<String> aliases) {
        return new LegacySubCommandAdapter(name, executor, tabCompleter, aliases);
    }

    /**
     * Хелпер для создания таб-комплит функции.
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
