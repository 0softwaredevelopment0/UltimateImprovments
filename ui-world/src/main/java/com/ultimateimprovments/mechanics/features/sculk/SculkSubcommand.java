package com.ultimateimprovments.mechanics.features.sculk;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code /ui sculk status|start|stop} — control panel of the sculk growth.
 * <p>
 * Every action has its own permission, so a server can hand out the read-only
 * status without allowing staff to stop the infection:
 * <ul>
 *   <li>{@code ui.command.sculk.status} — state, active catalyst count, filter;</li>
 *   <li>{@code ui.command.sculk.start} — enable growth (persisted in the DB);</li>
 *   <li>{@code ui.command.sculk.stop} — disable growth (persisted in the DB).</li>
 * </ul>
 */
public final class SculkSubcommand {

    private SculkSubcommand() {}

    /** {@code /ui sculk <status|start|stop>} dispatcher. */
    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "start" -> toggle(sender, true);
            case "stop" -> toggle(sender, false);
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>✖</red> <white>Unknown sculk subcommand: </white><yellow>"
                        + args[1] + "</yellow>"));
                usage(sender);
                yield true;
            }
        };
    }

    /** Tab-complete: only the actions the sender is allowed to run. */
    public static List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 2) return List.of();
        List<String> out = new ArrayList<>(3);
        if (sender.hasPermission(Permissions.CMD_SCULK_STATUS)) out.add("status");
        if (sender.hasPermission(Permissions.CMD_SCULK_START)) out.add("start");
        if (sender.hasPermission(Permissions.CMD_SCULK_STOP)) out.add("stop");
        return out;
    }

    // =========================
    // ACTIONS
    // =========================

    private static boolean status(CommandSender sender) {
        if (!require(sender, Permissions.CMD_SCULK_STATUS)) return true;

        SculkGrowthManager manager = SculkGrowthManager.getInstance();
        if (manager == null) {
            CommandErrors.moduleDisabled(sender, "sculk");
            return true;
        }

        String state = manager.isRunning()
                ? "<green>ON</green> <dark_gray>(runtime switch, ui_state)</dark_gray>"
                : "<red>OFF</red> <dark_gray>(runtime switch, ui_state)</dark_gray>";
        if (!manager.isConfigEnabled()) {
            state += " <dark_gray>— master switch </dark_gray><yellow>[sculk] enabled = false</yellow>";
        }

        sender.sendMessage(MessageUtil.parse("<dark_gray>────────── <white>Sculk growth</white> <dark_gray>──────────"));
        sender.sendMessage(MessageUtil.parse("<gray>State: </gray>" + state));
        sender.sendMessage(MessageUtil.parse("<gray>Catalysts: </gray><yellow>" + manager.activeCatalysts()
                + "</yellow><gray> active </gray><dark_gray>/</dark_gray><gray> </gray><yellow>"
                + manager.trackedCatalysts() + "</yellow><gray> tracked</gray>"));
        sender.sendMessage(MessageUtil.parse("<gray>Worlds: </gray>" + filterSummary(manager)));
        sender.sendMessage(MessageUtil.parse("<gray>Hazards: </gray>" + hazardSummary()));
        sender.sendMessage(MessageUtil.parse("<gray>Pulse: every </gray><white>" + manager.getIntervalTicks()
                + "t</white><gray> · charge </gray><white>" + manager.getChargePerPulse()
                + "</white><gray> · max </gray><white>" + manager.getMaxCatalystsPerPulse()
                + "</white><gray> catalyst(s)/pulse · MSPT gate </gray><white>" + manager.getMaxMspt()
                + "</white>"));
        return true;
    }

    private static boolean toggle(CommandSender sender, boolean start) {
        if (!require(sender, start ? Permissions.CMD_SCULK_START : Permissions.CMD_SCULK_STOP)) return true;

        SculkGrowthManager manager = SculkGrowthManager.getInstance();
        if (manager == null) {
            CommandErrors.moduleDisabled(sender, "sculk");
            return true;
        }

        if (start && !manager.isConfigEnabled()) {
            sender.sendMessage(MessageUtil.parse("<red>✖</red> <white>Cannot start sculk growth: the master switch"
                    + " </white><yellow>[sculk] enabled</yellow><white> is </white><red>false</red><white> in"
                    + " </white><yellow>configs/UI-World.toml</yellow><white>.</white>"));
            return true;
        }

        if (manager.isGrowthEnabled() == start) {
            sender.sendMessage(MessageUtil.parse("<yellow>⚠</yellow> <white>Sculk growth is already "
                    + (start ? "running" : "stopped") + ".</white>"));
            return true;
        }

        boolean persisted = manager.setGrowthEnabled(start);
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Sculk growth "
                + (start ? "started" : "stopped") + ".</white> "
                + (persisted
                        ? "<dark_gray>(saved in the database)</dark_gray>"
                        : "<red>(not saved — database is unavailable)</red>")));
        return true;
    }

    // =========================
    // HELPERS
    // =========================

    private static boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        CommandErrors.noPermission(sender, permission);
        return false;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse("<red>✖ Usage:</red> <white>/ui sculk status|start|stop</white>"));
    }

    /** One line describing the contact hazards (module state, pulse, rules). */
    private static String hazardSummary() {
        SculkHazardManager hazards = SculkHazardManager.getInstance();
        if (hazards == null) return "<red>OFF</red> <dark_gray>(module not initialised)</dark_gray>";
        if (!hazards.isEnabled()) return "<red>OFF</red> <dark_gray>[sculk.hazards] enabled = false</dark_gray>";
        return "<green>ON</green> <dark_gray>every</dark_gray> <white>" + hazards.getIntervalTicks()
                + "t</white> <dark_gray>·</dark_gray> " + hazards.describe();
    }

    /** One line describing the world filter (whitelist, blacklist or both). */
    private static String filterSummary(SculkGrowthManager manager) {
        boolean whitelist = manager.isWhitelistEnabled();
        boolean blacklist = manager.isBlacklistEnabled();
        if (!whitelist && !blacklist) return "<white>all worlds</white>";

        StringBuilder out = new StringBuilder();
        if (whitelist) {
            out.append("<white>whitelist</white> <green>ON</green> ").append(worldList(manager.getWhitelist()));
        }
        if (blacklist) {
            if (whitelist) out.append(" <dark_gray>minus</dark_gray> ");
            out.append("<white>blacklist</white> <green>ON</green> ").append(worldList(manager.getBlacklist()));
        }
        return out.toString();
    }

    private static String worldList(Set<String> worlds) {
        if (worlds.isEmpty()) return "<dark_gray>(none)</dark_gray>";
        Set<String> sorted = new TreeSet<>(worlds);
        return "<aqua>" + String.join("</aqua><dark_gray>, </dark_gray><aqua>", sorted) + "</aqua>";
    }
}
