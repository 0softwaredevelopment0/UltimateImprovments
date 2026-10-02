package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.mechanics.security.consolelockdown.ConsoleLockdownManager;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handles the {@code /ui console} subcommand — console lockdown control.
 * <p>
 * Usage:
 * <pre>
 *   /ui console lockdown                     — show status
 *   /ui console lockdown on [-t &lt;10s|5m|2h|1d&gt;]   — enable now (or after the delay)
 *   /ui console lockdown off [-t &lt;10s|5m|2h|1d&gt;]  — disable now (or after the delay)
 *   /ui console lockdown timed &lt;10s|5m|2h|1d&gt;     — enable, auto-disable after the duration
 * </pre>
 * While the lockdown is active EVERY console command is cancelled, including
 * {@code /ui} itself — the lockdown can only be lifted from an in-game account
 * with the {@code ui.command.console} permission, or via the
 * {@code console_lockdown.kill_switch} config escape hatch.
 */
public final class ConsoleSubcommand {

    private ConsoleSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CMD_CONSOLE)) {
            CommandErrors.noPermission(sender);
            return true;
        }

        // /ui console (no lockdown action) — usage
        if (args.length < 2 || !args[1].equalsIgnoreCase("lockdown")) {
            sendUsage(sender);
            return true;
        }

        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        ConsoleLockdownManager manager = ConsoleLockdownManager.getInstance();
        if (manager == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Console lockdown module is not initialized!</red>"));
            return true;
        }

        return switch (action) {
            case "" -> showStatus(sender, manager);
            case "on" -> lockdownOn(sender, manager, args);
            case "off" -> lockdownOff(sender, manager, args);
            case "timed" -> lockdownTimed(sender, manager, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // =========================
    // ACTIONS
    // =========================

    private static boolean lockdownOn(CommandSender sender, ConsoleLockdownManager manager, String[] args) {
        Long delay = parseTimeFlag(sender, args);
        if (delay == null) return true; // error already reported

        if (delay > 0) {
            if (manager.isActive() && manager.getScheduledAction() == null) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Console lockdown is already enabled.</white>"));
                return true;
            }
            manager.scheduleEnable(System.currentTimeMillis() + delay);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Scheduled console lockdown </white><green>ENABLE</green>"
                            + "<white> in </white><yellow>"
                            + ConsoleLockdownManager.formatDuration(delay) + "</yellow>"));
        } else {
            if (manager.isActive()) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Console lockdown is already enabled.</white>"));
                return true;
            }
            manager.enable();
        }
        return true;
    }

    private static boolean lockdownOff(CommandSender sender, ConsoleLockdownManager manager, String[] args) {
        Long delay = parseTimeFlag(sender, args);
        if (delay == null) return true; // error already reported

        if (delay > 0) {
            manager.scheduleDisable(System.currentTimeMillis() + delay);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Scheduled console lockdown </white><red>DISABLE</red>"
                            + "<white> in </white><yellow>"
                            + ConsoleLockdownManager.formatDuration(delay) + "</yellow>"));
        } else {
            if (!manager.isActive() && manager.getScheduledAction() == null) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Console lockdown is already disabled.</white>"));
                return true;
            }
            manager.disable();
        }
        return true;
    }

    private static boolean lockdownTimed(CommandSender sender, ConsoleLockdownManager manager, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui console lockdown timed &lt;10s|5m|2h|1d&gt;</white>"));
            return true;
        }
        long duration = ConsoleLockdownManager.parseTimeToMillis(args[3]);
        if (duration <= 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Invalid time format! Use: </red><white>10s</white><gray>, </gray><white>5m</white>"
                            + "<gray>, </gray><white>2h</white><gray>, </gray><white>1d</white>"));
            return true;
        }
        manager.enableTimed(duration);
        sender.sendMessage(MessageUtil.parse(
                "<yellow>⏰</yellow> <white>Console lockdown will be automatically disabled in </white><yellow>"
                        + ConsoleLockdownManager.formatDuration(duration) + "</yellow>"));
        return true;
    }

    // =========================
    // STATUS
    // =========================

    private static boolean showStatus(CommandSender sender, ConsoleLockdownManager manager) {
        String status = manager.isActive()
                ? "<red>🔒 LOCKED</red>"
                : "<green>✔ OPEN</green>";
        sender.sendMessage(MessageUtil.parse("<gray>═══ <white>Console Lockdown</white> ═══</gray>"));
        sender.sendMessage(MessageUtil.parse("<gray>Status: " + status + "</gray>"));

        if (manager.getScheduledAction() != null) {
            long remaining = Math.max(0L, manager.getScheduledAt() - System.currentTimeMillis());
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⏰</yellow> <white>Scheduled</white> <yellow>"
                            + manager.getScheduledAction().toUpperCase(Locale.ROOT)
                            + "</yellow> <white>in</white> <yellow>"
                            + ConsoleLockdownManager.formatDuration(remaining) + "</yellow>"));
        }
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>While locked, every console/RCON command is cancelled —</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>lift via an in-game account with ui.command.console</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>or console_lockdown.kill_switch = true + restart//ui reload.</dark_gray>"));
        return true;
    }

    // =========================
    // -t FLAG
    // =========================

    /**
     * Extracts the optional {@code -t <time>} flag from args (after the action).
     *
     * @return delay in millis, 0 when the flag is absent, or null on a
     *         malformed flag (error message already sent to the sender)
     */
    private static Long parseTimeFlag(CommandSender sender, String[] args) {
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.equals("-t")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ The </red><white>-t</white><red> flag requires a time value"
                                    + " (10s, 5m, 2h, 1d)!</red>"));
                    return null;
                }
                long millis = ConsoleLockdownManager.parseTimeToMillis(args[i + 1]);
                if (millis <= 0) {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ Invalid time format! Use: </red><white>10s</white><gray>, </gray>"
                                    + "<white>5m</white><gray>, </gray><white>2h</white><gray>, </gray>"
                                    + "<white>1d</white>"));
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-t") && lower.length() > 2) {
                long millis = ConsoleLockdownManager.parseTimeToMillis(lower.substring(2));
                if (millis <= 0) {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ Invalid time format! Use: </red><white>10s</white><gray>, </gray>"
                                    + "<white>5m</white><gray>, </gray><white>2h</white><gray>, </gray>"
                                    + "<white>1d</white>"));
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-")) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Unknown flag: </white><yellow>" + arg + "</yellow>"));
                return null;
            }
        }
        return 0L;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n"
                        + "<white>/ui console lockdown</white> <gray>— show status</gray>\n"
                        + "<white>/ui console lockdown on [-t 10s|5m|2h|1d]</white> <gray>— enable now (or after the delay)</gray>\n"
                        + "<white>/ui console lockdown off [-t 10s|5m|2h|1d]</white> <gray>— disable now (or after the delay)</gray>\n"
                        + "<white>/ui console lockdown timed &lt;10s|5m|2h|1d&gt;</white> <gray>— enable, auto-disable after the duration</gray>"
        ));
    }

    // =========================
    // TAB COMPLETION
    // =========================

    public static List<String> tabComplete(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            completions.add("lockdown");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("lockdown")) {
            for (String action : List.of("on", "off", "timed")) {
                completions.add(action);
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("lockdown")) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("on") || action.equals("off")) {
                completions.add("-t");
            } else if (action.equals("timed")) {
                completions.addAll(TIME_SUGGESTIONS);
            }
        } else if (args.length == 5 && args[1].equalsIgnoreCase("lockdown")) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if ((action.equals("on") || action.equals("off"))
                    && args[3].equalsIgnoreCase("-t")) {
                completions.addAll(TIME_SUGGESTIONS);
            }
        }

        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last))
                .collect(Collectors.toList());
    }

    private static final List<String> TIME_SUGGESTIONS =
            List.of("10s", "30s", "5m", "15m", "30m", "1h", "6h", "1d");
}
