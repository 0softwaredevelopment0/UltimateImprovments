package com.ultimateimprovments.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ultimateimprovments.core.Main;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * PaperCommands — registers the {@code /ui} root via the <b>Paper command API</b>
 * (Brigadier + {@link LifecycleEvents#COMMANDS}), replacing the legacy
 * {@code CommandMap} registration.
 * <p>
 * The subcommands themselves still live in {@link SubCommandRegistry}; the
 * Brigadier node forwards the raw argument tail to
 * {@link SubCommandRegistry#dispatch} / {@link SubCommandRegistry#tabComplete}.
 * This keeps every existing subcommand handler untouched while moving the
 * registration onto Paper's command API.
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
                    Commands.literal("ui")
                            .executes(ctx -> dispatch(ctx, new String[0]))
                            .then(Commands.argument("args", StringArgumentType.greedyString())
                                    .suggests(PaperCommands::suggest)
                                    .executes(ctx -> dispatch(ctx,
                                            splitArgs(ctx.getArgument("args", String.class)))))
                            .build(),
                    "UltimateImprovments — main command",
                    List.of("ultimateimprovments"));
        });
    }

    private static int dispatch(CommandContext<CommandSourceStack> ctx, String[] args) {
        CommandSender sender = ctx.getSource().getSender();
        boolean handled = SubCommandRegistry.getInstance().dispatch(sender, args);
        return handled ? com.mojang.brigadier.Command.SINGLE_SUCCESS : 0;
    }

    /**
     * Suggests subcommand completions. Because the tail is a Brigadier
     * {@code greedyString}, the client replaces the WHOLE remaining text with the
     * chosen suggestion — so every suggestion is prefixed with the already-typed
     * part (up to the last space).
     */
    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder) {
        CommandSender sender = ctx.getSource().getSender();
        String remaining = builder.getRemaining();
        String[] parts = remaining.trim().isEmpty() ? new String[]{""} : remaining.split(" ", -1);

        List<String> suggestions = SubCommandRegistry.getInstance().tabComplete(sender, parts);

        String prefix = remaining.contains(" ")
                ? remaining.substring(0, remaining.lastIndexOf(' ') + 1)
                : "";
        for (String suggestion : suggestions) {
            builder.suggest(prefix + suggestion);
        }
        return builder.buildFuture();
    }

    private static String[] splitArgs(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        return raw.trim().split("\\s+");
    }
}
