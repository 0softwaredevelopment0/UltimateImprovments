package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.mechanics.security.anticheat.AntiCheatManager;
import com.mcplugin.mechanics.security.anticheat.core.AbstractCheck;
import com.mcplugin.mechanics.security.anticheat.core.CheckCategory;
import com.mcplugin.mechanics.security.anticheat.core.ExemptionManager;
import com.mcplugin.mechanics.security.anticheat.core.PlayerData;
import com.mcplugin.mechanics.security.anticheat.nms.PacketHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /mp ac stats — диагностика античита: статус, проверки, VL игроков, PlayerData.
 */
public final class AcStatsSubcommand {

    private AcStatsSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcplugin.anticheat.notify") && !sender.isOp()) {
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
                        + "<white>/mp ac overview</white> — общая статистика\n"
                        + "<white>/mp ac checks</white> — список проверок\n"
                        + "<white>/mp ac players</white> — VL всех игроков\n"
                        + "<white>/mp ac player <ник></white> — VL конкретного игрока\n"
                        + "<white>/mp ac exempt <ник></white> — освободить игрока от античита\n"
                        + "<white>/mp ac unexempt <ник></white> — снять освобождение\n"
                        + "<white>/mp ac toggle [on|off]</white> — глобально включить/выключить античит"));
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
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃   §6⚡ AntiCheat §7— §fDiagnostics");
        sender.sendMessage("§8┃");
        sender.sendMessage("§8┃  §7Module:    §f" + (Main.getInstance().getConfig().getBoolean("anticheat.enabled", true) ? "§aENABLED" : "§cDISABLED"));
        sender.sendMessage("§8┃  §7Packet:    " + (packetEnabled ? "§aACTIVE" : "§cOFF (event-only)"));
        sender.sendMessage("§8┃  §7Checks:    §a" + enabledCount + " enabled §7/ §c" + disabledCount + " disabled §7(" + allChecks.size() + " total)");
        sender.sendMessage("§8┃  §7Players:   §f" + trackedPlayers + " §7tracked §8/ §f" + Bukkit.getOnlinePlayers().size() + " online");
        sender.sendMessage("§8┃  §7Active VL: §f" + vlCounts.size() + " §7checks with violations");
        sender.sendMessage("§8┃");
        sender.sendMessage("§8┃  §7└ <click> §f/mp ac checks §7— список проверок");
        sender.sendMessage("§8┃  §7└ <click> §f/mp ac players §7— VL игроков");

        // Category breakdown
        for (CheckCategory cat : CheckCategory.values()) {
            List<AbstractCheck> catChecks = acm.getChecksByCategory(cat);
            long catEnabled = catChecks.stream().filter(AbstractCheck::isEnabled).count();
            String catName = switch (cat) {
                case COMBAT -> "§c⚔ Combat";
                case MOVEMENT -> "§b🏃 Movement";
                case WORLD -> "§a🌍 World";
                case MISC -> "§d🎒 Misc";
            };
            sender.sendMessage("§8┃     " + catName + "§7: §f" + catChecks.size() + " §7(§a" + catEnabled + " active§7)");
        }

        sender.sendMessage("§8┃");
        sender.sendMessage("§8┃  §7Actions:    §fNOTIFY §7≥1 VL  |  §fSETBACK §7≥1 VL");
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        sender.sendMessage("");

        return true;
    }

    // =========================
    // CHECKS LIST
    // =========================

    private static boolean showChecks(CommandSender sender, AntiCheatManager acm) {
        sender.sendMessage("");
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃   §6📋 AntiCheat §7— All Checks");
        sender.sendMessage("§8┃");

        for (CheckCategory cat : CheckCategory.values()) {
            List<AbstractCheck> catChecks = acm.getChecksByCategory(cat);
            String catPrefix = switch (cat) {
                case COMBAT -> "§c⚔";
                case MOVEMENT -> "§b🏃";
                case WORLD -> "§a🌍";
                case MISC -> "§d🎒";
            };

            sender.sendMessage("§8┃");
            sender.sendMessage("§8┃  " + catPrefix + " §f" + cat.name());

            for (AbstractCheck check : catChecks) {
                String status = check.isEnabled() ? "§a✔" : "§c✘";
                String vlDecay = String.format("%.1f", check.getVlDecay());
                sender.sendMessage("§8┃    " + status + " §f" + padRight(check.getName(), 16)
                        + "§7 decay: §f" + vlDecay + "§7/s"
                        + " §8[" + check.getConfigPath() + "]");
            }
        }

        sender.sendMessage("§8┃");
        sender.sendMessage("§8┃  §7Legend: §a✔ enabled  §c✘ disabled");
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
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
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃   §6👤 AntiCheat §7— Player VLs");
        sender.sendMessage("§8┃");

        for (Player p : online) {
            PlayerData data = acm.getPlayerData(p);
            if (data == null) {
                sender.sendMessage("§8┃  §7" + padRight(p.getName(), 16) + "§8no data");
                continue;
            }

            Map<String, Double> vls = data.getAllVl();
            List<String> activeVls = new ArrayList<>();
            for (Map.Entry<String, Double> entry : vls.entrySet()) {
                if (entry.getValue() > 0) {
                    activeVls.add(entry.getKey() + "§7:§e" + String.format("%.1f", entry.getValue()));
                }
            }

            if (activeVls.isEmpty()) {
                sender.sendMessage("§8┃  §a✔ §f" + padRight(p.getName(), 16) + "§7clean");
            } else {
                sender.sendMessage("§8┃  §c⚠ §f" + padRight(p.getName(), 16)
                        + "§7" + String.join(" §8|§7 ", activeVls));
            }
        }

        sender.sendMessage("§8┃");
        sender.sendMessage("§8┃  §7<click> §f/mp ac player <ник> §7— детали игрока");
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        sender.sendMessage("");

        return true;
    }

    // =========================
    // SINGLE PLAYER
    // =========================

    private static boolean showPlayer(CommandSender sender, AntiCheatManager acm, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp ac player <ник></white>"));
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
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃   §6👤 AC Stats §7— §f" + target.getName());
        sender.sendMessage("§8┃");
        sender.sendMessage("§8┃  §7GameMode:  §f" + target.getGameMode());
        sender.sendMessage("§8┃  §7Ping:      §f" + target.getPing() + "ms");
        sender.sendMessage("§8┃  §7Exempt:    " + (data.isExempted() ? "§aYES" : "§cNO"));
        sender.sendMessage("§8┃  §7On Ground: §f" + data.wasOnGround());
        sender.sendMessage("§8┃  §7CPS:       §f" + data.getCps());
        sender.sendMessage("§8┃  §7Position:  §f" + (data.getLastLocation() != null
                ? String.format("%.0f, %.0f, %.0f",
                        data.getLastLocation().getX(),
                        data.getLastLocation().getY(),
                        data.getLastLocation().getZ())
                : "none"));
        sender.sendMessage("§8┃");

        // Violation levels by check
        sender.sendMessage("§8┃  §6VL by check:");

        var vls = data.getAllVl();
        if (vls.isEmpty()) {
            sender.sendMessage("§8┃    §7(no violations)");
        } else {
            boolean hasAny = false;
            for (Map.Entry<String, Double> entry : vls.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .collect(Collectors.toList())) {
                if (entry.getValue() <= 0) continue;
                hasAny = true;
                String color = entry.getValue() >= 10 ? "§c" : entry.getValue() >= 5 ? "§e" : "§a";
                String bar = getBar(entry.getValue(), 15);
                sender.sendMessage("§8┃    §f" + padRight(entry.getKey(), 14)
                        + color + String.format("%5.1f", entry.getValue())
                        + " §8" + bar);
            }
            if (!hasAny) {
                sender.sendMessage("§8┃    §7(no active violations)");
            }
        }

        sender.sendMessage("§8┃");
        String loc = target.getLocation().getBlockX() + " " + target.getLocation().getBlockY() + " " + target.getLocation().getBlockZ();
        sender.sendMessage("§8┃  §7Location:  §f" + loc + " §8[" + target.getWorld().getName() + "]");
        if (data.getLastGroundLocation() != null) {
            var g = data.getLastGroundLocation();
            sender.sendMessage("§8┃  §7Last gnd: §f" + g.getBlockX() + " " + g.getBlockY() + " " + g.getBlockZ());
        }
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        sender.sendMessage("");

        return true;
    }

    // =========================
    // EXEMPT / UNEXEMPT / TOGGLE
    // =========================

    /**
     * /mp ac exempt <player> — освободить игрока от всех проверок античита
     */
    private static boolean exemptPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp ac exempt <ник></white>"));
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
     * /mp ac unexempt <player> — снять освобождение с игрока
     */
    private static boolean unexemptPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp ac unexempt <ник></white>"));
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
     * /mp ac toggle [on|off] — глобально включить/выключить античит
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
                            "<red>❌ Usage: </red><white>/mp ac toggle [on|off]</white>"));
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
