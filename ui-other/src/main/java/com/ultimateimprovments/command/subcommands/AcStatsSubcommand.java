package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.AbstractCheck;
import com.ultimateimprovments.mechanics.security.anticheat.core.CheckCategory;
import com.ultimateimprovments.mechanics.security.anticheat.core.ExemptionManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.PlayerData;
import com.ultimateimprovments.mechanics.security.anticheat.nms.PacketHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /ui ac stats — anticheat diagnostics: status, checks, player VLs, PlayerData.
 */
public final class AcStatsSubcommand {

    private AcStatsSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.anticheat.notify") && !sender.isOp()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Нет прав!</red>"));
            return true;
        }

        AntiCheatManager acm = AntiCheatManager.getInstance();
        if (acm == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ AntiCheatManager не инициализирован!</red>"));
            return true;
        }

        // ── Parse sub-args ──
        // args: ["ac", "subcommand", "playerName"]
        String sub = args.length > 1 ? args[1].toLowerCase() : "overview";

        return switch (sub) {
            case "overview" -> showOverview(sender, acm);
            case "checks" -> showChecks(sender, acm);
            case "players" -> showPlayers(sender, acm);
            case "player" -> showPlayer(sender, acm, args);
            case "exempt" -> exemptPlayer(sender, args);
            case "unexempt" -> unexemptPlayer(sender, args);
            case "toggle" -> toggleAntiCheat(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Неизвестная подкоманда. Используйте:</red>\n"
                        + "<white>/ui ac overview</white> — общая статистика\n"
                        + "<white>/ui ac checks</white> — список проверок\n"
                        + "<white>/ui ac players</white> — VL всех игроков\n"
                        + "<white>/ui ac player <ник></white> — VL конкретного игрока\n"
                        + "<white>/ui ac exempt <ник></white> — освободить игрока от античита\n"
                        + "<white>/ui ac unexempt <ник></white> — снять освобождение\n"
                        + "<white>/ui ac toggle [on|off]</white> — глобально включить/выключить античит"));
                yield true;
            }
        };
    }

    // =========================
    // OVERVIEW
    // =========================

    private static boolean showOverview(CommandSender sender, AntiCheatManager acm) {
        List<AbstractCheck> allChecks = acm.getAllChecks();
        long enabledCount = allChecks.stream().filter(AbstractCheck::isEnabled).count();
        long disabledCount = allChecks.size() - enabledCount;

        // Packet interception status
        boolean packetEnabled = PacketHandler.getInstance() != null;

        // Player count
        int trackedPlayers = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (acm.getPlayerData(p) != null) trackedPlayers++;
        }

        // VL totals
        Map<String, Long> vlCounts = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = acm.getPlayerData(p);
            if (data == null) continue;
            for (Map.Entry<String, Double> entry : data.getAllVl().entrySet()) {
                if (entry.getValue() > 0) {
                    vlCounts.merge(entry.getKey(), 1L, Long::sum);
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃   <gold>⚡ AntiCheat <gray>— <white>Diagnostics"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Module:    <white>" + (Main.getInstance().getConfig().getBoolean("anticheat.enabled", true) ? "<green>ENABLED" : "<red>DISABLED")));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Packet:    " + (packetEnabled ? "<green>ACTIVE" : "<red>OFF (event-only)")));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Checks:    <green>" + enabledCount + " enabled <gray>/ <red>" + disabledCount + " disabled <gray>(" + allChecks.size() + " total)"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Players:   <white>" + trackedPlayers + " <gray>tracked <dark_gray>/ <white>" + Bukkit.getOnlinePlayers().size() + " online"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Active VL: <white>" + vlCounts.size() + " <gray>checks with violations"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>└ <click> <white>/ui ac checks <gray>— список проверок"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>└ <click> <white>/ui ac players <gray>— VL игроков"));

        // Category breakdown
        for (CheckCategory cat : CheckCategory.values()) {
            List<AbstractCheck> catChecks = acm.getChecksByCategory(cat);
            long catEnabled = catChecks.stream().filter(AbstractCheck::isEnabled).count();
            String catName = switch (cat) {
                case COMBAT -> "<red>⚔ Combat";
                case MOVEMENT -> "<aqua>🏃 Movement";
                case WORLD -> "<green>🌍 World";
                case MISC -> "<light_purple>🎒 Misc";
            };
            sender.sendMessage(MessageUtil.parse("<dark_gray>┃     " + catName + "<gray>: <white>" + catChecks.size() + " <gray>(<green>" + catEnabled + " active<gray>)"));
        }

        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Actions:    <white>NOTIFY <gray>≥1 VL  |  <white>SETBACK <gray>≥1 VL"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        sender.sendMessage("");

        return true;
    }

    // =========================
    // CHECKS LIST
    // =========================

    private static boolean showChecks(CommandSender sender, AntiCheatManager acm) {
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃   <gold>📋 AntiCheat <gray>— All Checks"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));

        for (CheckCategory cat : CheckCategory.values()) {
            List<AbstractCheck> catChecks = acm.getChecksByCategory(cat);
            String catPrefix = switch (cat) {
                case COMBAT -> "<red>⚔";
                case MOVEMENT -> "<aqua>🏃";
                case WORLD -> "<green>🌍";
                case MISC -> "<light_purple>🎒";
            };

            sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>┃  " + catPrefix + " <white>" + cat.name()));

            for (AbstractCheck check : catChecks) {
                String status = check.isEnabled() ? "<green>✔" : "<red>✘";
                String vlDecay = String.format("%.1f", check.getVlDecay());
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃    " + status + " <white>" + padRight(check.getName(), 16)
                        + "<gray> decay: <white>" + vlDecay + "<gray>/s"
                        + " <dark_gray>[" + check.getConfigPath() + "]"));
            }
        }

        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Legend: <green>✔ enabled  <red>✘ disabled"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        sender.sendMessage("");

        return true;
    }

    // =========================
    // PLAYERS LIST
    // =========================

    private static boolean showPlayers(CommandSender sender, AntiCheatManager acm) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<yellow>ℹ Нет игроков онлайн.</yellow>"));
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃   <gold>👤 AntiCheat <gray>— Player VLs"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));

        for (Player p : online) {
            PlayerData data = acm.getPlayerData(p);
            if (data == null) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>" + padRight(p.getName(), 16) + "<dark_gray>no data"));
                continue;
            }

            Map<String, Double> vls = data.getAllVl();
            List<String> activeVls = new ArrayList<>();
            for (Map.Entry<String, Double> entry : vls.entrySet()) {
                if (entry.getValue() > 0) {
                    activeVls.add(entry.getKey() + "<gray>:<yellow>" + String.format("%.1f", entry.getValue()));
                }
            }

            if (activeVls.isEmpty()) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <green>✔ <white>" + padRight(p.getName(), 16) + "<gray>clean"));
            } else {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <red>⚠ <white>" + padRight(p.getName(), 16)
                        + "<gray>" + String.join(" <dark_gray>|<gray> ", activeVls)));
            }
        }

        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray><click> <white>/ui ac player <ник> <gray>— детали игрока"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        sender.sendMessage("");

        return true;
    }

    // =========================
    // SINGLE PLAYER
    // =========================

    private static boolean showPlayer(CommandSender sender, AntiCheatManager acm, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui ac player <ник></white>"));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Игрок</red> <yellow>" + targetName + "</yellow> <red>не найден!</red>"));
            return true;
        }

        PlayerData data = acm.getPlayerData(target);
        if (data == null) {
            sender.sendMessage(MessageUtil.parse("<yellow>ℹ Нет данных для " + target.getName() + "</yellow>"));
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃   <gold>👤 AC Stats <gray>— <white>" + target.getName()));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>GameMode:  <white>" + target.getGameMode()));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Ping:      <white>" + target.getPing() + "ms"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Exempt:    " + (data.isExempted() ? "<green>YES" : "<red>NO")));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>On Ground: <white>" + data.wasOnGround()));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>CPS:       <white>" + data.getCps()));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Position:  <white>" + (data.getLastLocation() != null
                ? String.format("%.0f, %.0f, %.0f",
                        data.getLastLocation().getX(),
                        data.getLastLocation().getY(),
                        data.getLastLocation().getZ())
                : "none")));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));

        // Violation levels by check
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gold>VL by check:"));

        var vls = data.getAllVl();
        if (vls.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<dark_gray>┃    <gray>(no violations)"));
        } else {
            boolean hasAny = false;
            for (Map.Entry<String, Double> entry : vls.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .collect(Collectors.toList())) {
                if (entry.getValue() <= 0) continue;
                hasAny = true;
                String color = entry.getValue() >= 10 ? "<red>" : entry.getValue() >= 5 ? "<yellow>" : "<green>";
                String bar = getBar(entry.getValue(), 15);
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃    <white>" + padRight(entry.getKey(), 14)
                        + color + String.format("%5.1f", entry.getValue())
                        + " <dark_gray>" + bar));
            }
            if (!hasAny) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃    <gray>(no active violations)"));
            }
        }

        sender.sendMessage(MessageUtil.parse("<dark_gray>┃"));
        String loc = target.getLocation().getBlockX() + " " + target.getLocation().getBlockY() + " " + target.getLocation().getBlockZ();
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Location:  <white>" + loc + " <dark_gray>[" + target.getWorld().getName() + "]"));
        if (data.getLastGroundLocation() != null) {
            var g = data.getLastGroundLocation();
            sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>Last gnd: <white>" + g.getBlockX() + " " + g.getBlockY() + " " + g.getBlockZ()));
        }
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        sender.sendMessage("");

        return true;
    }

    // =========================
    // EXEMPT / UNEXEMPT / TOGGLE
    // =========================

    /**
     * /ui ac exempt <player> — exempt a player from all anticheat checks
     */
    private static boolean exemptPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui ac exempt <ник></white>"));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + targetName + "</yellow> <red>not found!</red>"));
            return true;
        }

        ExemptionManager.getInstance().exemptAll(target.getUniqueId());

        // Also update PlayerData flag for quick checks
        PlayerData data = AntiCheatManager.getInstance().getPlayerData(target);
        if (data != null) data.setExempted(true);

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + target.getName()
                + "</yellow> <white>is now exempt from all anticheat checks.</white>"));

        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse(
                    "<yellow>✦</yellow> <white>You have been exempted from anticheat checks.</white>"));
        }

        return true;
    }

    /**
     * /ui ac unexempt <player> — remove the exemption from a player
     */
    private static boolean unexemptPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui ac unexempt <ник></white>"));
            return true;
        }

        String targetName = args[2];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + targetName + "</yellow> <red>not found!</red>"));
            return true;
        }

        ExemptionManager.getInstance().unexemptAll(target.getUniqueId());

        PlayerData data = AntiCheatManager.getInstance().getPlayerData(target);
        if (data != null) data.setExempted(false);

        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Player</white> <yellow>" + target.getName()
                + "</yellow> <white>is no longer exempt from anticheat checks.</white>"));

        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse(
                    "<yellow>✦</yellow> <white>You are no longer exempt from anticheat checks.</white>"));
        }

        return true;
    }

    /**
     * /ui ac toggle [on|off] — globally enable/disable the anticheat
     */
    private static boolean toggleAntiCheat(CommandSender sender, String[] args) {
        AntiCheatManager acm = AntiCheatManager.getInstance();
        if (acm == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ AntiCheatManager не инициализирован!</red>"));
            return true;
        }

        boolean newState;
        if (args.length >= 3) {
            String state = args[2].toLowerCase();
            switch (state) {
                case "on", "enable", "true", "1" -> newState = true;
                case "off", "disable", "false", "0" -> newState = false;
                default -> {
                    sender.sendMessage(MessageUtil.parse(
                            "<red>❌ Usage: </red><white>/ui ac toggle [on|off]</white>"));
                    return true;
                }
            }
        } else {
            // Toggle current state
            newState = !acm.isGlobalEnabled();
        }

        acm.setGlobalEnabled(newState);

        if (newState) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>AntiCheat is now</white> <green>ENABLED</green><white>.</white>"));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<red>✔</red> <white>AntiCheat is now</white> <red>DISABLED</red><white>.</white>"));
        }

        return true;
    }

    // =========================
    // UTILITY
    // =========================

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    private static String getBar(double value, double max) {
        int bars = (int) Math.round((value / max) * 10);
        if (bars < 0) bars = 0;
        if (bars > 10) bars = 10;
        String filled = "█".repeat(bars);
        String empty = "░".repeat(10 - bars);
        return filled + empty;
    }
}
