package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.energy.generation.reactor.ReactorCommand;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class StructureSubcommand {

    private StructureSubcommand() {}

    public static boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui structures <dfc|magnet|lightning> <stats|assemble|enable|disable>"));
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "dfc" -> handleDfc(player, args);
            case "magnet" -> handleMagnet(player, args);
            case "lightning" -> handleLightning(player, args);
            default -> player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Неизвестный тип структуры: <white>" + args[1]));
        }
        return true;
    }

    private static void handleDfc(Player player, String[] args) {
        if (!player.hasPermission("ui.command.structures")) {
            CommandErrors.noPermission(player);
            return;
        }
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui structures dfc <stats|assemble>"));
            return;
        }
        if (args[2].equalsIgnoreCase("stats")) {
            ReactorManager reactor = ReactorManager.getInstance();
            if (reactor == null || !reactor.isValid()) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Активных реакторов не найдено."));
                return;
            }
            Location playerLoc = player.getLocation();
            Location reactorLoc = reactor.getReactorLocation();
            if (reactorLoc == null || !playerLoc.getWorld().equals(reactorLoc.getWorld())) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Рядом нет активного реактора."));
                return;
            }
            double distance = playerLoc.distance(reactorLoc);
            if (distance > 50) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Рядом нет активного реактора (ближайший в <white>"
                        + String.format("%.1f", distance) + "<gray> м)."));
                return;
            }

            String status;
            if (reactor.isMeltdownCountdown()) status = "<dark_red>!!! <red>Взрыв неизбежен <dark_red>!!!";
            else if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) status = "<yellow>Деградация";
            else status = "<green>Нормальный";

            int meltdownSecs = reactor.isMeltdownCountdown() ? (reactor.getMeltdownTimer() / 20) : 0;

            player.sendMessage(MessageUtil.parse("<dark_gray>┌────────────────────────────────┐"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_red>Р.Т.С <dark_gray>» <white>Статистика реактора"));
            player.sendMessage(MessageUtil.parse("<dark_gray>├────────────────────────────────┤"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>ID: <white>" + reactor.getReactorId()));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Статус: " + status));
            if (reactor.isMeltdownCountdown()) player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Детонация: <red>" + meltdownSecs + " сек"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Дист: <white>" + String.format("%.1f", distance) + " м"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gold>═[ <yellow>Данные ядра <gold>]═"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Температура:  <white>" + reactor.getDisplayCoreTemp() + " C*"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Давление:    <white>" + reactor.getDisplayCorePress() + " kPa"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Целостность: <white>" + reactor.getDisplayCoreShInt() + " %"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_aqua>═[ <aqua>Данные корпуса <dark_aqua>]═"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Температура:  <white>" + reactor.getDisplayCoreCaseTemp() + " C*"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Давление:    <white>" + reactor.getDisplayCoreCasePress() + " kPa"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Целостность: <white>" + reactor.getDisplayCoreCaseInt() + " %"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_purple>═[ <light_purple>Данные рецепта <dark_purple>]═"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Прогресс:   <white>" + reactor.getDisplayRecipeTime() + " %"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Износ:      <white>" + reactor.getDisplayReactorWear() + " %"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Выработка:  <white>" + reactor.getDisplayEnergyRate() + " E/сек"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Позиция: <white>" + reactorLoc.getBlockX() + " " + reactorLoc.getBlockY() + " " + reactorLoc.getBlockZ()));
            player.sendMessage(MessageUtil.parse("<dark_gray>└────────────────────────────────┘"));
        } else if (args[2].equalsIgnoreCase("assemble")) {
            if (!player.hasPermission("ui.command.structures.dfc")) {
                CommandErrors.noPermission(player);
                return;
            }
            ReactorCommand.assembleDarkSynthesis(player);
        } else {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui structures dfc <stats|assemble>"));
        }
    }

    private static void handleMagnet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui str magnet <stats|assemble>"));
            return;
        }
        if (args[2].equalsIgnoreCase("stats")) {
            if (!player.hasPermission("ui.command.structures.magnet")) {
                CommandErrors.noPermission(player);
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
            if (nearest == null) { player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Активных магнитов не найдено!")); return; }
            if (nearestDist > 50) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Рядом нет активного магнита (ближайший в <white>" + String.format("%.1f", nearestDist) + "<red> м)."));
                return;
            }
            int power = nearest.blockKeys.size();
            int radius = MagnetManager.getClusterRadiusForPower(power);
            String tierName = ReactorCommand.getMagnetPowerTierStatic(power);
            player.sendMessage(MessageUtil.parse("<dark_gray>┌────────────────────────────────┐"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <aqua>Магнит <dark_gray>» <white>Статистика"));
            player.sendMessage(MessageUtil.parse("<dark_gray>├────────────────────────────────┤"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Блоков: <white>" + power + " <gray>шт"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Сила: " + tierName));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Радиус: <white>" + radius + " <gray>блоков"));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Центр: <white>" + nearest.center.getBlockX() + " " + nearest.center.getBlockY() + " " + nearest.center.getBlockZ()));
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Дистанция: <white>" + String.format("%.1f", nearestDist) + " м"));
            player.sendMessage(MessageUtil.parse("<dark_gray>└────────────────────────────────┘"));
        } else if (args[2].equalsIgnoreCase("assemble")) {
            if (!player.hasPermission("ui.command.structures.magnet")) {
                CommandErrors.noPermission(player);
                return;
            }
            ReactorCommand.assembleMagnet(player);
        } else {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui str magnet <stats|assemble>"));
        }
    }

    private static void handleLightning(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui str lightning <enable|disable|stats>"));
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
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Активных структур молний не найдено!"));
            return;
        }
        if (nearestDist > 50) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Рядом нет активной структуры молний (ближайшая в <white>" + String.format("%.1f", nearestDist) + "<red> м)."));
            return;
        }
        switch (args[2].toLowerCase()) {
            case "stats" -> {
                String stats = LightningManager.getStats(nearest);
                if (stats != null) {
                    player.sendMessage(MessageUtil.parse("<dark_gray>┌────────────────────────────────┐"));
                    player.sendMessage(MessageUtil.parse("<dark_gray>│ <yellow>⚡ Молнии <dark_gray>» <white>Статистика"));
                    player.sendMessage(MessageUtil.parse("<dark_gray>├────────────────────────────────┤"));
                    player.sendMessage(MessageUtil.parse(stats));
                    player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Дистанция: <white>" + String.format("%.1f", nearestDist) + " м"));
                    player.sendMessage(MessageUtil.parse("<dark_gray>└────────────────────────────────┘"));
                }
            }
            case "enable" -> {
                LightningManager.setEnabled(nearest, true);
                player.sendMessage(MessageUtil.parse("<green>✔ <white>Структура молний включена!"));
            }
            case "disable" -> {
                LightningManager.setEnabled(nearest, false);
                player.sendMessage(MessageUtil.parse("<red>❌ <white>Структура молний выключена!"));
            }
            default -> player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Usage: /ui str lightning <enable|disable|stats>"));
        }
    }
}
