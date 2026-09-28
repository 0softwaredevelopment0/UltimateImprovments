package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/**
 * PaperCommands — registers the {@code /ui} root via the <b>Paper command API</b>
 * ({@link BasicCommand} + {@link LifecycleEvents#COMMANDS}), replacing the legacy
 * {@code CommandMap} registration.
 * <p>
 * {@link BasicCommand} is Paper's "legacy-style" command: it hands the raw
 * {@code String[]} args and a {@code suggest} callback, which maps 1:1 onto the
 * existing {@link SubCommandRegistry} dispatcher — no Brigadier tree, no
 * greedy-string suggestion quirks. Every existing subcommand handler stays
 * untouched.
 */
public final class PaperCommands {

    /** Guards against re-registering the listener across {@code /ui reload} cycles. */
    private static boolean registered = false;

    private PaperCommands() {}

    public static void register(Main plugin) {
        if (registered) return;
        registered = true;

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    "ui",
                    "UltimateImprovments — main command",
                    List.of("ultimateimprovments"),
                    new UiCommand());
        });
    }

    /** Bridges the Paper command API to the legacy {@link SubCommandRegistry} dispatcher. */
    private static final class UiCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            SubCommandRegistry.getInstance().dispatch(source.getSender(), args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            return SubCommandRegistry.getInstance().tabComplete(source.getSender(), args);
        }

        @Override
        public boolean canUse(CommandSender sender) {
            // Per-subcommand permissions are checked by the subcommands themselves.
            return true;
        }
    }
}
