package com.mcplugin.commands.subcommands;

import com.mcplugin.Main;
import com.mcplugin.core1.ReactorCommand;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.features.lightning.LightningManager;
import com.mcplugin.features.magnet.MagnetManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class StructureSubcommand {

    private StructureSubcommand() {}

    public static boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§4❌ §cUsage: /mp structures <dfc|magnet|lightning> <stats|assemble|enable|disable>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "dfc" -> handleDfc(player, args);
            case "magnet" -> handleMagnet(player, args);
            case "lightning" -> handleLightning(player, args);
            default -> player.sendMessage("§4❌ §cНеизвестный тип структуры: §f" + args[1]);
        }
        return true;
    }

    private static void handleDfc(Player player, String[] args) {
        if (!player.hasPermission("mcplugin.command.structures")) {
            player.sendMessage("§4❌ §cУ вас нет прав на управление структурами!");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§4❌ §cUsage: /mp structures dfc <stats|assemble>");
            return;
        }
        if (args[2].equalsIgnoreCase("stats")) {
            ReactorManager reactor = ReactorManager.getInstance();
            if (reactor == null || !reactor.isValid()) {
                player.sendMessage("§4❌ §cError: §7Активных реакторов не найдено.");
                return;
            }
            Location playerLoc = player.getLocation();
            Location reactorLoc = reactor.getReactorLocation();
            if (reactorLoc == null || !playerLoc.getWorld().equals(reactorLoc.getWorld())) {
                player.sendMessage("§4❌ §cError: §7Рядом нет активного реактора.");
                return;
            }
            double distance = playerLoc.distance(reactorLoc);
            if (distance > 50) {
                player.sendMessage("§4❌ §cError: §7Рядом нет активного реактора (ближайший в §f"
                        + String.format("%.1f", distance) + "§7 м).");
                return;
            }

            String status;
            if (reactor.isMeltdownCountdown()) status = "§4!!! §cВзрыв неизбежен §4!!!";
            else if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) status = "§eДеградация";
            else status = "§aНормальный";

            int meltdownSecs = reactor.isMeltdownCountdown() ? (reactor.getMeltdownTimer() / 20) : 0;

            player.sendMessage("§8┌────────────────────────────────┐");
            player.sendMessage("§8│ §4Р.Т.С §8» §fСтатистика реактора");
            player.sendMessage("§8├────────────────────────────────┤");
            player.sendMessage("§8│ §7ID: §f" + reactor.getReactorId());
            player.sendMessage("§8│ §7Статус: " + status);
            if (reactor.isMeltdownCountdown()) player.sendMessage("§8│ §7Детонация: §c" + meltdownSecs + " сек");
            player.sendMessage("§8│ §7Дист: §f" + String.format("%.1f", distance) + " м");
            player.sendMessage("§8│ §6═[ §eДанные ядра §6]═");
            player.sendMessage("§8│ §7Температура:  §f" + reactor.getDisplayCoreTemp() + " C*");
            player.sendMessage("§8│ §7Давление:    §f" + reactor.getDisplayCorePress() + " kPa");
            player.sendMessage("§8│ §7Целостность: §f" + reactor.getDisplayCoreShInt() + " %");
            player.sendMessage("§8│ §3═[ §bДанные корпуса §3]═");
            player.sendMessage("§8│ §7Температура:  §f" + reactor.getDisplayCoreCaseTemp() + " C*");
            player.sendMessage("§8│ §7Давление:    §f" + reactor.getDisplayCoreCasePress() + " kPa");
            player.sendMessage("§8│ §7Целостность: §f" + reactor.getDisplayCoreCaseInt() + " %");
            player.sendMessage("§8│ §5═[ §dДанные рецепта §5]═");
            player.sendMessage("§8│ §7Прогресс:   §f" + reactor.getDisplayRecipeTime() + " %");
            player.sendMessage("§8│ §7Износ:      §f" + reactor.getDisplayReactorWear() + " %");
            player.sendMessage("§8│ §7Выработка:  §f" + reactor.getDisplayEnergyRate() + " E/сек");
            player.sendMessage("§8│ §7Позиция: §f" + reactorLoc.getBlockX() + " " + reactorLoc.getBlockY() + " " + reactorLoc.getBlockZ());
            player.sendMessage("§8└────────────────────────────────┘");
        } else if (args[2].equalsIgnoreCase("assemble")) {
            if (!player.hasPermission("mcplugin.command.structures.dfc")) {
                player.sendMessage("§4❌ §cУ вас нет прав на сборку реактора!");
                return;
            }
            ReactorCommand.assembleDarkSynthesis(player);
        } else {
            player.sendMessage("§4❌ §cUsage: /mp structures dfc <stats|assemble>");
        }
    }

    private static void handleMagnet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§4❌ §cUsage: /mp str magnet <stats|assemble>");
            return;
        }
        if (args[2].equalsIgnoreCase("stats")) {
            if (!player.hasPermission("mcplugin.command.structures.magnet")) {
                player.sendMessage("§4❌ §cУ вас нет прав на просмотр статистики магнита!");
                return;
            }
            Location playerLoc = player.getLocation();
            MagnetManager.MagnetCluster nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (MagnetManager.MagnetCluster cluster : MagnetManager.getClusters()) {
                if (cluster.center == null || !cluster.center.getWorld().equals(playerLoc.getWorld())) continue;
                double dist = playerLoc.distance(cluster.center);
                if (dist < nearestDist) { nearestDist = dist; nearest = cluster; }
            }
            if (nearest == null) { player.sendMessage("§4❌ §cАктивных магнитов не найдено!"); return; }
            if (nearestDist > 50) {
                player.sendMessage("§4❌ §cРядом нет активного магнита (ближайший в §f" + String.format("%.1f", nearestDist) + "§c м).");
                return;
            }
            int power = nearest.blockKeys.size();
            int radius = MagnetManager.getClusterRadiusForPower(power);
            String tierName = ReactorCommand.getMagnetPowerTierStatic(power);
            player.sendMessage("§8┌────────────────────────────────┐");
            player.sendMessage("§8│ §bМагнит §8» §fСтатистика");
            player.sendMessage("§8├────────────────────────────────┤");
            player.sendMessage("§8│ §7Блоков: §f" + power + " §7шт");
            player.sendMessage("§8│ §7Сила: " + tierName);
            player.sendMessage("§8│ §7Радиус: §f" + radius + " §7блоков");
            player.sendMessage("§8│ §7Центр: §f" + nearest.center.getBlockX() + " " + nearest.center.getBlockY() + " " + nearest.center.getBlockZ());
            player.sendMessage("§8│ §7Дистанция: §f" + String.format("%.1f", nearestDist) + " м");
            player.sendMessage("§8└────────────────────────────────┘");
        } else if (args[2].equalsIgnoreCase("assemble")) {
            if (!player.hasPermission("mcplugin.command.structures.magnet")) {
                player.sendMessage("§4❌ §cУ вас нет прав на сборку магнита!");
                return;
            }
            ReactorCommand.assembleMagnet(player);
        } else {
            player.sendMessage("§4❌ §cUsage: /mp str magnet <stats|assemble>");
        }
    }

    private static void handleLightning(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§4❌ §cUsage: /mp str lightning <enable|disable|stats>");
            return;
        }
        Location playerLoc = player.getLocation();
        Location nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Location loc : LightningManager.getActiveLocations()) {
            if (!loc.getWorld().equals(playerLoc.getWorld())) continue;
            double dist = playerLoc.distance(loc);
            if (dist < nearestDist) { nearestDist = dist; nearest = loc; }
        }
        if (nearest == null) {
            player.sendMessage("§4❌ §cАктивных структур молний не найдено!");
            return;
        }
        if (nearestDist > 50) {
            player.sendMessage("§4❌ §cРядом нет активной структуры молний (ближайшая в §f" + String.format("%.1f", nearestDist) + "§c м).");
            return;
        }
        switch (args[2].toLowerCase()) {
            case "stats" -> {
                String stats = LightningManager.getStats(nearest);
                if (stats != null) {
                    player.sendMessage("§8┌────────────────────────────────┐");
                    player.sendMessage("§8│ §e⚡ Молнии §8» §fСтатистика");
                    player.sendMessage("§8├────────────────────────────────┤");
                    player.sendMessage(stats);
                    player.sendMessage("§8│ §7Дистанция: §f" + String.format("%.1f", nearestDist) + " м");
                    player.sendMessage("§8└────────────────────────────────┘");
                }
            }
            case "enable" -> {
                LightningManager.setEnabled(nearest, true);
                player.sendMessage("§a✅ §fСтруктура молний включена!");
            }
            case "disable" -> {
                LightningManager.setEnabled(nearest, false);
                player.sendMessage("§c✗ §fСтруктура молний выключена!");
            }
            default -> player.sendMessage("§4❌ §cUsage: /mp str lightning <enable|disable|stats>");
        }
    }
}
