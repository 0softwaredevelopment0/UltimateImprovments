package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.mechanics.security.serverlockdown.ServerLockdownManager;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handles the {@code /ui server} subcommand — server lockdown control.
 * <p>
 * Usage:
 * <pre>
 *   /ui server lockdown                     — show status
 *   /ui server lockdown on [-t &lt;10s|5m|2h|1d&gt;]   — enable now (or after the delay)
 *   /ui server lockdown off [-t &lt;10s|5m|2h|1d&gt;]  — disable now (or after the delay)
 *   /ui server lockdown timed &lt;10s|5m|2h|1d&gt;     — enable, auto-disable after the duration
 * </pre>
 * While the lockdown is active new connections are refused (anti-bot-attack
 * measure), but players already online are never kicked — they keep a grace
 * window ({@code server_lockdown.grace_time}, default 10s) to rejoin after
 * quitting. The lockdown can be lifted with this command or via the
 * {@code server_lockdown.kill_switch} config escape hatch.
 */
public final class ServerSubcommand {

    /** Special error 012 (this command's own): invalid time format. */
    public static final int ERR_INVALID_TIME = 12;
    /** Special error 013 (this command's own): the -t flag has no time value. */
    public static final int ERR_MISSING_TIME_VALUE = 13;
    /** Special error 014 (this command's own): unknown flag. */
    public static final int ERR_UNKNOWN_FLAG = 14;

    private ServerSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CMD_SERVER)) {
            CommandErrors.noPermission(sender, Permissions.CMD_SERVER);
            return true;
        }

        // /ui server (no lockdown action) — usage
        if (args.length < 2 || !args[1].equalsIgnoreCase("lockdown")) {
            sendUsage(sender);
            return true;
        }

        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        ServerLockdownManager manager = ServerLockdownManager.getInstance();
        if (manager == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Server lockdown module is not initialized!</red>"));
            return true;
        }

        return switch (action) {
            case "" -> showStatus(sender, manager);
            case "status" -> handleStatus(sender, manager, args);
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

    private static boolean lockdownOn(CommandSender sender, ServerLockdownManager manager, String[] args) {
        Long delay = parseTimeFlag(sender, args);
        if (delay == null) return true; // error already reported

        if (delay > 0) {
            if (manager.isActive() && manager.getScheduledAction() == null) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Server lockdown is already enabled.</white>"));
                return true;
            }
            manager.scheduleEnable(System.currentTimeMillis() + delay);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Scheduled server lockdown </white><green>ENABLE</green>"
                            + "<white> in </white><yellow>"
                            + ServerLockdownManager.formatDuration(delay) + "</yellow>"));
        } else {
            if (manager.isActive()) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Server lockdown is already enabled.</white>"));
                return true;
            }
            manager.enable();
        }
        return true;
    }

    private static boolean lockdownOff(CommandSender sender, ServerLockdownManager manager, String[] args) {
        Long delay = parseTimeFlag(sender, args);
        if (delay == null) return true; // error already reported

        if (delay > 0) {
            manager.scheduleDisable(System.currentTimeMillis() + delay);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Scheduled server lockdown </white><red>DISABLE</red>"
                            + "<white> in </white><yellow>"
                            + ServerLockdownManager.formatDuration(delay) + "</yellow>"));
        } else {
            if (!manager.isActive() && manager.getScheduledAction() == null) {
                sender.sendMessage(MessageUtil.parse(
                        "<yellow>⚠</yellow> <white>Server lockdown is already disabled.</white>"));
                return true;
            }
            manager.disable();
        }
        return true;
    }

    private static boolean lockdownTimed(CommandSender sender, ServerLockdownManager manager, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui server lockdown timed <10s|5m|2h|1d></white>"));
            return true;
        }
        long duration = ServerLockdownManager.parseTimeToMillis(args[3]);
        if (duration <= 0) {
            CommandErrors.custom(sender, ERR_INVALID_TIME,
                    "<red>Invalid time format! Use: </red><white>10s</white><gray>, </gray><white>5m</white>"
                            + "<gray>, </gray><white>2h</white><gray>, </gray><white>1d</white>");
            return true;
        }
        manager.enableTimed(duration);
        sender.sendMessage(MessageUtil.parse(
                "<yellow>⏰</yellow> <white>Server lockdown will be automatically disabled in </white><yellow>"
                        + ServerLockdownManager.formatDuration(duration) + "</yellow>"));
        return true;
    }

    // =========================
    // STATUS
    // =========================

    private static boolean showStatus(CommandSender sender, ServerLockdownManager manager) {
        String status = manager.isActive()
                ? "<red>🔒 LOCKED</red>"
                : "<green>✔ OPEN</green>";
        sender.sendMessage(MessageUtil.parse("<gray>═══ <white>Server Lockdown</white> ═══</gray>"));
        sender.sendMessage(MessageUtil.parse("<gray>Status: " + status + "</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Grace window:</gray> <yellow>"
                        + ServerLockdownManager.formatDuration(manager.getGraceMillis()) + "</yellow>"));
        sender.sendMessage(MessageUtil.parse(
                "<gray>Blocked joins this session:</gray> <red>"
                        + manager.getBlockedCount() + "</red>"
                        + "<gray>,  currently in grace:</gray> <yellow>"
                        + manager.getActiveGraceEntries().size() + "</yellow>"));

        if (manager.getScheduledAction() != null) {
            long remaining = Math.max(0L, manager.getScheduledAt() - System.currentTimeMillis());
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⏰</yellow> <white>Scheduled</white> <yellow>"
                            + manager.getScheduledAction().toUpperCase(Locale.ROOT)
                            + "</yellow> <white>in</white> <yellow>"
                            + ServerLockdownManager.formatDuration(remaining) + "</yellow>"));
        }

        // ─── Clickable section tabs (like the /ui help navigation) ───
        Component blockedTab = Component.text("[Blocked joins]")
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/ui server lockdown status blocked 1"))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse(
                        "<gray>Who was refused during this lockdown session")));
        Component graceTab = Component.text("[In grace]")
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/ui server lockdown status grace 1"))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse(
                        "<gray>Who quit and may still rejoin within the grace window")));
        Component thresholdsTab = Component.text("[Thresholds]")
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/ui server lockdown status thresholds 1"))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse(
                        "<gray>Alert thresholds and whether they already fired this session")));
        sender.sendMessage(MessageUtil.parse("<gray>Sections: </gray>")
                .append(blockedTab)
                .append(MessageUtil.parse(" "))
                .append(graceTab)
                .append(MessageUtil.parse(" "))
                .append(thresholdsTab));

        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>While locked, new connections are refused — players already</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>online are not kicked and may rejoin within the grace window</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>after quitting. Lift via /ui server lockdown off</dark_gray>"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_gray>or server_lockdown.kill_switch = true + restart//ui reload.</dark_gray>"));
        return true;
    }

    /**
     * Dispatches {@code /ui server lockdown status [section] [page]}:
     * without a section — the summary; with a section — the paginated list.
     */
    private static boolean handleStatus(CommandSender sender, ServerLockdownManager manager, String[] args) {
        if (args.length < 4 || args[3].equalsIgnoreCase("summary")) {
            return showStatus(sender, manager);
        }
        String section = args[3].toLowerCase(Locale.ROOT);
        if (!section.equals("blocked") && !section.equals("grace") && !section.equals("thresholds")) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Unknown section: </red><white>" + args[3]
                            + "</white><gray> — use </gray><white>blocked</white><gray>, </gray><white>grace</white>"
                            + "<gray> or </gray><white>thresholds</white>"));
            return true;
        }
        int page = 1;
        if (args.length >= 5) {
            try {
                page = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Invalid page number: </red><white>" + args[4] + "</white>"));
                return true;
            }
        }
        switch (section) {
            case "blocked":
                return showBlockedSection(sender, manager, page);
            case "grace":
                return showGraceSection(sender, manager, page);
            default:
                return showThresholdsSection(sender, manager, page);
        }
    }

    /** Paginated "Blocked joins" list: who was refused during this session. */
    private static boolean showBlockedSection(CommandSender sender, ServerLockdownManager manager, int requestedPage) {
        List<ServerLockdownManager.BlockedAttempt> attempts = manager.getBlockedAttempts();
        int totalPages = Math.max(1, (attempts.size() + STATUS_PER_PAGE - 1) / STATUS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int from = (page - 1) * STATUS_PER_PAGE;
        int to = Math.min(from + STATUS_PER_PAGE, attempts.size());

        sender.sendMessage(MessageUtil.parse("<gray>═══ <white>Server Lockdown</white> — "
                + "<red>Blocked joins</red> <gray>(</gray><red>"
                        + manager.getBlockedCount() + "</red><gray> total, page "
                        + page + "/" + totalPages + ") ═══</gray>"));

        if (attempts.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Nobody has been refused during this lockdown session.</gray>"));
        }
        for (ServerLockdownManager.BlockedAttempt a : attempts.subList(from, to)) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>•</dark_gray> <white>" + a.name() + "</white> <gray>(" + a.ip()
                            + ")</gray> <dark_gray>at " + ServerLockdownManager.formatTimestamp(a.at())
                            + "</dark_gray>"));
        }
        sendSectionFooter(sender, "blocked", page, totalPages);
        return true;
    }

    /** Paginated "In grace" list: who quit and may still rejoin. */
    private static boolean showGraceSection(CommandSender sender, ServerLockdownManager manager, int requestedPage) {
        List<ServerLockdownManager.GraceEntry> entries = manager.getActiveGraceEntries();
        int totalPages = Math.max(1, (entries.size() + STATUS_PER_PAGE - 1) / STATUS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int from = (page - 1) * STATUS_PER_PAGE;
        int to = Math.min(from + STATUS_PER_PAGE, entries.size());

        sender.sendMessage(MessageUtil.parse("<gray>═══ <white>Server Lockdown</white> — "
                + "<yellow>In grace</yellow> <gray>(</gray><yellow>"
                        + entries.size() + "</yellow><gray> players, page "
                        + page + "/" + totalPages + ") ═══</gray>"));

        if (entries.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>No grandfathered player is currently inside the grace window.</gray>"));
        }
        for (ServerLockdownManager.GraceEntry e : entries.subList(from, to)) {
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>•</dark_gray> <white>" + e.name() + "</white> <gray>— quit "
                            + ServerLockdownManager.formatDuration(System.currentTimeMillis() - e.quitAt())
                            + " ago, may rejoin for </gray><yellow>"
                            + ServerLockdownManager.formatDuration(e.remainingMillis()) + "</yellow>"));
        }
        sendSectionFooter(sender, "grace", page, totalPages);
        return true;
    }

    /**
     * Paginated "Thresholds" list: every configured alert threshold with its
     * config-file order number, trigger value and OK/ALERT status. ALERT
     * thresholds already fired during the current lockdown session (persisted
     * in the DB; the whole session resets when the lockdown is toggled).
     */
    private static boolean showThresholdsSection(CommandSender sender, ServerLockdownManager manager, int requestedPage) {
        List<ServerLockdownManager.AlertThreshold> thresholds = manager.getAlertThresholds();
        int totalPages = Math.max(1, (thresholds.size() + STATUS_PER_PAGE - 1) / STATUS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int from = (page - 1) * STATUS_PER_PAGE;
        int to = Math.min(from + STATUS_PER_PAGE, thresholds.size());

        sender.sendMessage(MessageUtil.parse("<gray>═══ <white>Server Lockdown</white> — "
                + "<yellow>Alert thresholds</yellow> <gray>(</gray><red>"
                + manager.getBlockedCount() + "</red><gray> blocked this session, page "
                + page + "/" + totalPages + ") ═══</gray>"));

        if (thresholds.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>No alert thresholds configured.</gray>"));
        }
        for (int i = from; i < to; i++) {
            ServerLockdownManager.AlertThreshold t = thresholds.get(i);
            boolean fired = manager.isThresholdFired(t.threshold());
            sender.sendMessage(MessageUtil.parse(
                    "<dark_gray>•</dark_gray> <white>#" + (i + 1) + "</white> <gray>— fires at</gray> <yellow>"
                            + t.threshold() + "</yellow> <gray>blocked</gray> <dark_gray>—</dark_gray> "
                            + (fired ? "<red>ALERT</red>" : "<green>OK</green>")));
        }
        sendSectionFooter(sender, "thresholds", page, totalPages);
        return true;
    }

    /** Section footer: clickable [<] / [>] page arrows and the other section tabs. */
    private static void sendSectionFooter(CommandSender sender, String section, int page, int totalPages) {
        Component footer = MessageUtil.parse("<gray>Page <yellow>" + page + "<gray>/"
                + totalPages + "   ");

        if (page > 1) {
            footer = footer.append(Component.text("[<]")
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/ui server lockdown status " + section + " " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(MessageUtil.parse("<gray>Previous page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }
        footer = footer.append(MessageUtil.parse("  "));
        if (page < totalPages) {
            footer = footer.append(Component.text("[>]")
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/ui server lockdown status " + section + " " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(MessageUtil.parse("<gray>Next page"))));
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }
        sender.sendMessage(footer);

        // Cross-section switch tabs (all sections except the current one) + summary
        Component tabs = MessageUtil.parse("<gray>Sections: </gray>");
        boolean first = true;
        for (String other : List.of("blocked", "grace", "thresholds")) {
            if (other.equals(section)) continue;
            if (!first) tabs = tabs.append(MessageUtil.parse(" "));
            first = false;
            tabs = tabs.append(sectionTab(other));
        }
        Component backTab = Component.text("[Summary]")
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/ui server lockdown status"))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse("<gray>Back to the summary")));
        sender.sendMessage(tabs.append(MessageUtil.parse(" ")).append(backTab));
    }

    /** Clickable tab component for a status section. */
    private static Component sectionTab(String section) {
        String label = switch (section) {
            case "blocked" -> "[Blocked joins]";
            case "grace" -> "[In grace]";
            default -> "[Thresholds]";
        };
        String hover = switch (section) {
            case "blocked" -> "<gray>Who was refused during this lockdown session";
            case "grace" -> "<gray>Who quit and may still rejoin within the grace window";
            default -> "<gray>Alert thresholds and whether they already fired this session";
        };
        return Component.text(label)
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/ui server lockdown status " + section + " 1"))
                .hoverEvent(HoverEvent.showText(MessageUtil.parse(hover)));
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
                    CommandErrors.custom(sender, ERR_MISSING_TIME_VALUE,
                            "<red>The </red><white>-t</white><red> flag requires a time value"
                                    + " (10s, 5m, 2h, 1d)!</red>");
                    return null;
                }
                long millis = ServerLockdownManager.parseTimeToMillis(args[i + 1]);
                if (millis <= 0) {
                    CommandErrors.custom(sender, ERR_INVALID_TIME,
                            "<red>Invalid time format! Use: </red><white>10s</white><gray>, </gray>"
                                    + "<white>5m</white><gray>, </gray><white>2h</white><gray>, </gray>"
                                    + "<white>1d</white>");
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-t") && lower.length() > 2) {
                long millis = ServerLockdownManager.parseTimeToMillis(lower.substring(2));
                if (millis <= 0) {
                    CommandErrors.custom(sender, ERR_INVALID_TIME,
                            "<red>Invalid time format! Use: </red><white>10s</white><gray>, </gray>"
                                    + "<white>5m</white><gray>, </gray><white>2h</white><gray>, </gray>"
                                    + "<white>1d</white>");
                    return null;
                }
                return millis;
            }
            if (lower.startsWith("-")) {
                CommandErrors.custom(sender, ERR_UNKNOWN_FLAG,
                        "<yellow>⚠</yellow> <white>Unknown flag: </white><yellow>" + arg + "</yellow>");
                return null;
            }
        }
        return 0L;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Usage:</red>\n"
                        + "<white>/ui server lockdown</white> <gray>— show status (summary)</gray>\n"
                        + "<white>/ui server lockdown status [blocked|grace|thresholds] [page]</white> <gray>— summary / paginated section lists</gray>\n"
                        + "<white>/ui server lockdown on [-t 10s|5m|2h|1d]</white> <gray>— enable now (or after the delay)</gray>\n"
                        + "<white>/ui server lockdown off [-t 10s|5m|2h|1d]</white> <gray>— disable now (or after the delay)</gray>\n"
                        + "<white>/ui server lockdown timed <10s|5m|2h|1d></white> <gray>— enable, auto-disable after the duration</gray>"
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
            for (String action : List.of("status", "on", "off", "timed")) {
                completions.add(action);
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("lockdown")) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("status")) {
                completions.add("summary");
                completions.add("blocked");
                completions.add("grace");
                completions.add("thresholds");
            } else if (action.equals("on") || action.equals("off")) {
                completions.add("-t");
            } else if (action.equals("timed")) {
                completions.addAll(TIME_SUGGESTIONS);
            }
        } else if (args.length == 5 && args[1].equalsIgnoreCase("lockdown")) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("status")) {
                String section = args[3].toLowerCase(Locale.ROOT);
                if (section.equals("blocked") || section.equals("grace")) {
                    completions.add("1");
                }
            } else if ((action.equals("on") || action.equals("off"))
                    && args[3].equalsIgnoreCase("-t")) {
                completions.addAll(TIME_SUGGESTIONS);
            }
        }

        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last))
                .collect(Collectors.toList());
    }

    /** How many rows fit on one status-section page (same as /ui help). */
    private static final int STATUS_PER_PAGE = 8;

    private static final List<String> TIME_SUGGESTIONS =
            List.of("10s", "30s", "5m", "15m", "30m", "1h", "6h", "1d");
}
