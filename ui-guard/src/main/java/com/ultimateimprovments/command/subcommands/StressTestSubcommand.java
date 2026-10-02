package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.mechanics.benchmark.StressPower;
import com.ultimateimprovments.mechanics.benchmark.StressTestManager;
import com.ultimateimprovments.mechanics.benchmark.StressTestType;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code /ui stresstest} subcommand — server benchmark runs.
 * <p>
 * Usage:
 * <pre>
 *   /ui stresstest start &lt;type&gt; &lt;power&gt;   — start a benchmark run
 *   /ui stresstest stop                    — stop the active run and print the report
 * </pre>
 * Types: {@code entity} (entity spawning), {@code block} (chain block updates),
 * {@code chunk} (chunk load/unload), {@code selector} (entity-iteration spam).
 * Powers: {@code minimal}, {@code low}, {@code moderate}, {@code high},
 * {@code max}. Requires {@link Permissions#CMD_STRESSTEST} and
 * {@code stresstest.enabled = true} in {@code UI-Guard.toml}.
 * <p>
 * The run itself is driven by {@link StressTestManager}; this class only
 * validates arguments and reports errors (codes 015-017).
 */
public final class StressTestSubcommand {

    /** Special error 015 (this command's own): unknown load type. */
    public static final int ERR_INVALID_TYPE = 15;
    /** Special error 016 (this command's own): unknown power level. */
    public static final int ERR_INVALID_POWER = 16;
    /** Special error 017 (this command's own): missing arguments. */
    public static final int ERR_MISSING_ARGS = 17;

    private StressTestSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CMD_STRESSTEST)) {
            CommandErrors.noPermission(sender, Permissions.CMD_STRESSTEST);
            return true;
        }

        if (!StressTestManager.cfg().getBoolean("stresstest.enabled", true)) {
            CommandErrors.custom(sender, CommandErrors.ERR_COMMAND_DISABLED,
                    StressTestManager.msg("stresstest.disabled",
                            "<red>This command is disabled on this server.</red> <gray>(stresstest.enabled = false)</gray>"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> start(sender, args);
            case "stop" -> stop(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // =========================
    // ACTIONS
    // =========================

    private static boolean start(CommandSender sender, String[] args) {
        if (args.length < 4) {
            CommandErrors.custom(sender, ERR_MISSING_ARGS, usageMessage());
            return true;
        }

        StressTestType type = StressTestType.parse(args[2]);
        if (type == null) {
            CommandErrors.custom(sender, ERR_INVALID_TYPE,
                    StressTestManager.msg("stresstest.invalid_type",
                            "<red>❌ Unknown stress test type: </red><white>%value%</white>"
                                    + "<gray> — use </gray><white>entity</white><gray>, </gray>"
                                    + "<white>block</white><gray>, </gray><white>chunk</white>"
                                    + "<gray> or </gray><white>selector</white>",
                            "%value%", args[2]));
            return true;
        }

        StressPower power = StressPower.parse(args[3]);
        if (power == null) {
            CommandErrors.custom(sender, ERR_INVALID_POWER,
                    StressTestManager.msg("stresstest.invalid_power",
                            "<red>❌ Unknown power level: </red><white>%value%</white>"
                                    + "<gray> — use </gray><white>minimal</white><gray>, </gray>"
                                    + "<white>low</white><gray>, </gray><white>moderate</white>"
                                    + "<gray>, </gray><white>high</white><gray> or </gray><white>max</white>",
                            "%value%", args[3]));
            return true;
        }

        StressTestManager manager = StressTestManager.getInstance();
        if (manager == null) {
            sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse(
                    "<red>❌ Stress test module is not initialized!</red>"));
            return true;
        }

        manager.start(sender, type, power);
        return true;
    }

    private static boolean stop(CommandSender sender) {
        StressTestManager manager = StressTestManager.getInstance();
        if (manager == null) {
            sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse(
                    "<red>❌ Stress test module is not initialized!</red>"));
            return true;
        }
        if (!manager.isActive()) {
            manager.reportNotRunning(sender);
            return true;
        }
        manager.stop();
        return true;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse(usageMessage()));
    }

    private static String usageMessage() {
        return StressTestManager.msg("stresstest.usage",
                "<red>❌ Usage:</red>\n"
                        + "<white>/ui stresstest start <type> <power></white>"
                        + " <gray>— start a benchmark run</gray>\n"
                        + "<white>/ui stresstest stop</white> <gray>— stop the run and print the report</gray>\n"
                        + "<gray>Types: </gray>" + join(StressTestType.ids())
                        + "<gray>   Powers: </gray>" + join(StressPower.ids()));
    }

    private static String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) builder.append("<gray>, </gray>");
            builder.append("<white>").append(values[i]).append("</white>");
        }
        return builder.toString();
    }

    // =========================
    // TAB COMPLETION
    // =========================

    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            completions.add("start");
            completions.add("stop");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("start")) {
            completions.addAll(List.of(StressTestType.ids()));
        } else if (args.length == 4 && args[1].equalsIgnoreCase("start")) {
            completions.addAll(List.of(StressPower.ids()));
        }

        // Nothing to suggest (e.g. after "/ui stresstest stop "): return an
        // empty string instead of an empty list, otherwise the dispatcher falls
        // back to suggesting online player names.
        if (completions.isEmpty()) {
            return List.of("");
        }
        return completions;
    }
}
