package com.ultimateimprovments.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CommandOutcomeTracker — records how the {@code /ui} dispatch of a player's
 * command ended, so CmdLogger (UI-Chat) can report the real outcome instead of
 * assuming success.
 * <p>
 * Why it exists: {@link SubCommandRegistry#dispatch(CommandSender, String[])}
 * never throws. On an unknown subcommand or a missing permission it just sends
 * a chat message and returns {@code true}, and subcommands report errors the
 * same way — so Paper never fires a {@code ServerCommandException} and the
 * command-logger assumed every {@code /ui ...} command "runs correctly".
 * <p>
 * Flow for {@code /ui ...}:
 * <ol>
 *   <li>the registry opens a dispatch context ({@link #begin(CommandSender, String)})
 *       before executing the subcommand;</li>
 *   <li>failure points mark it: {@link #markDenied(CommandSender)} (no permission,
 *       called from CommandErrors), {@link #markUnknown(CommandSender, String)}
 *       (unknown subcommand), {@link #markFailed(CommandSender, String)} (a
 *       subcommand reported an error);</li>
 *   <li>{@link #end(CommandSender, String)} closes the context: a marked outcome
 *       is delivered to the {@link Listener}, otherwise SUCCESS.</li>
 * </ol>
 * The listener (CmdLogger in UI-Chat) is resolved lazily by class name so
 * ui-core keeps no compile-time dependency on ui-chat.
 */
public final class CommandOutcomeTracker {

    /** Outcome of one /ui dispatch. */
    public enum Outcome { SUCCESS, ERROR, NO_PERMISSION, UNKNOWN_COMMAND, FAILED }

    /** Delivers a finished outcome to the command logger. */
    public interface Listener {
        void onOutcome(CommandSender sender, String command, Outcome outcome, String detail);
    }

    /** One in-flight /ui dispatch. */
    private static final class Ctx {
        final CommandSender sender;
        final String command;
        Outcome outcome = Outcome.SUCCESS;
        String detail = null;

        Ctx(CommandSender sender, String command) {
            this.sender = sender;
            this.command = command;
        }
    }

    private static final Map<UUID, Deque<Ctx>> contexts = new HashMap<>();
    private static volatile Listener listener;

    private CommandOutcomeTracker() {}

    /**
     * Sets the outcome listener. Called by the command logger at startup; the
     * class name is intentionally configurable so Core stays decoupled.
     */
    public static void setListener(Listener l) {
        listener = l;
    }

    /** Removes the listener (Core shutdown). */
    public static void clearListener() {
        listener = null;
        contexts.clear();
    }

    // ── Dispatch lifecycle (called from SubCommandRegistry) ──

    /**
     * Opens a dispatch context for a {@code /ui ...} command.
     * Must be matched by {@link #end(CommandSender, String)}.
     */
    public static void begin(CommandSender sender, String command) {
        if (!(sender instanceof org.bukkit.entity.Player player)) return;
        contexts.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>())
                .addLast(new Ctx(sender, command));
    }

    /**
     * Closes the dispatch context opened by {@link #begin}: delivers the marked
     * outcome to the listener, or SUCCESS if nothing marked a failure.
     */
    public static void end(CommandSender sender, String command) {
        if (!(sender instanceof org.bukkit.entity.Player player)) return;
        deliverAndPop(player.getUniqueId(), command);
    }

    // ── Marks (called from failure points) ──

    /** Marks the current dispatch as "no permission" (CommandErrors). */
    public static void markDenied(CommandSender sender) {
        mark(sender, Outcome.NO_PERMISSION, null);
    }

    /** Marks the current dispatch as "unknown subcommand". */
    public static void markUnknown(CommandSender sender, String sub) {
        mark(sender, Outcome.UNKNOWN_COMMAND, sub);
    }

    /** Marks the current dispatch as "subcommand reported an error". */
    public static void markFailed(CommandSender sender, String detail) {
        mark(sender, Outcome.FAILED, detail);
    }

    private static void mark(CommandSender sender, Outcome outcome, String detail) {
        if (!(sender instanceof org.bukkit.entity.Player player)) return;
        Ctx ctx = current(player.getUniqueId());
        if (ctx == null) return;
        if (ctx.outcome == Outcome.SUCCESS) { // keep the first mark
            ctx.outcome = outcome;
            ctx.detail = detail;
        }
    }

    // ── Internals ──

    private static Ctx current(UUID uuid) {
        Deque<Ctx> deque = contexts.get(uuid);
        return (deque == null || deque.isEmpty()) ? null : deque.peekLast();
    }

    private static void deliverAndPop(UUID uuid, String command) {
        Ctx ctx = null;
        Deque<Ctx> deque = contexts.get(uuid);
        if (deque != null) {
            // Pop the context matching this command (deepest-first).
            for (var it = deque.descendingIterator(); it.hasNext();) {
                Ctx c = it.next();
                if (c.command.equals(command)) {
                    ctx = c;
                    it.remove();
                    break;
                }
            }
            if (deque.isEmpty()) contexts.remove(uuid);
        }
        if (ctx == null) return;

        Listener l = listener;
        if (l != null) {
            try {
                l.onOutcome(ctx.sender, ctx.command, ctx.outcome, ctx.detail);
            } catch (Throwable ignored) {
                // Logging must never break command dispatch.
            }
        }
    }
}
