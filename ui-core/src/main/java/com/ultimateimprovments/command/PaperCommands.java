package com.ultimateimprovments.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ultimateimprovments.core.Main;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * PaperCommands — registers {@code /ui} through the <b>Paper/Brigadier command API</b>
 * ({@link LifecycleEvents#COMMANDS}).
 * <p>
 * The tree is intentionally <b>dynamic</b>:
 * <pre>
 *   ui &lt;sub&gt; [args...]
 * </pre>
 * {@code <sub>} is a {@code word} argument (with suggestions from
 * {@link SubCommandRegistry#getAllCommandNames()}) and {@code [args...]} is a
 * {@code greedyString}. Both route into the untouched {@link SubCommandRegistry}, so all
 * subcommands and their permissions keep working, and — crucially — subcommands
 * registered later by addons (after UI-Core's enable) are still resolvable, because
 * nothing is enumerated at registration time (only suggested at completion time).
 * <p>
 * This replaces the previous single {@code BasicCommand} bridge with real Brigadier
 * argument nodes while preserving byte-for-byte the legacy {@code String[]} contract.
 */
public final class PaperCommands {

    /** Guards against re-registering the listener across {@code /ui reload} cycles. */
    private static boolean registered = false;

    private PaperCommands() {}

    public static void register(Main plugin) {
        if (registered) return;
        registered = true;

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ui")
                    .executes(ctx -> {
                        dispatch(ctx.getSource().getSender(), new String[0]);
                        return 1;
                    });

            root.then(Commands.argument("sub", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSub(ctx.getSource(), builder))
                    .executes(ctx -> {
                        dispatch(ctx.getSource().getSender(),
                                new String[]{ctx.getArgument("sub", String.class)});
                        return 1;
                    })
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestTail(ctx.getSource(), builder))
                            .executes(ctx -> {
                                String sub = ctx.getArgument("sub", String.class);
                                String rest = ctx.getArgument("args", String.class);
                                dispatch(ctx.getSource().getSender(), merge(sub, rest));
                                return 1;
                            })));

            event.registrar().register(root.build(),
                    "UltimateImprovments — main command",
                    List.of("ultimateimprovments"));
        });
    }

    private static void dispatch(CommandSender sender, String[] args) {
        SubCommandRegistry.getInstance().dispatch(sender, args);
    }

    /** Rebuilds the legacy {@code String[] args} (args[0] = subcommand) from the greedy tail. */
    private static String[] merge(String sub, String rest) {
        if (rest == null || rest.isBlank()) return new String[]{sub};
        String[] parts = rest.trim().split("\\s+");
        String[] full = new String[parts.length + 1];
        full[0] = sub;
        System.arraycopy(parts, 0, full, 1, parts.length);
        return full;
    }

    /** First level: suggest subcommand names/aliases for the typed prefix. */
    private static CompletableFuture<Suggestions> suggestSub(CommandSourceStack source, SuggestionsBuilder builder) {
        String partial = builder.getRemaining();
        for (String name : SubCommandRegistry.getInstance().getAllCommandNames()) {
            if (name.startsWith(partial.toLowerCase())) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    /** Tail level: delegate to the subcommand's own tab-complete. */
    private static CompletableFuture<Suggestions> suggestTail(CommandSourceStack source, SuggestionsBuilder builder) {
        CommandSender sender = source.getSender();
        String input = builder.getInput();
        if (input.startsWith("/")) input = input.substring(1);

        String[] tokens = input.split(" ", -1);
        String[] args = tokens.length <= 1
                ? new String[]{""}
                : Arrays.copyOfRange(tokens, 1, tokens.length);

        for (String suggestion : SubCommandRegistry.getInstance().tabComplete(sender, args)) {
            if (suggestion != null && !suggestion.isEmpty()) builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }
}
