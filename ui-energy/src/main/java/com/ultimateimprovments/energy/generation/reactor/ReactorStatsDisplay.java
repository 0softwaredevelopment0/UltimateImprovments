package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Chat output of the reactor statistics box.
 * <p>
 * Shown when a player right-clicks the reactor status sign (ReactorListener)
 * and replaces the legacy {@code /ui str dfc stats} command.
 */
public final class ReactorStatsDisplay {

    private ReactorStatsDisplay() {}

    /** Sends the reactor stats box to the player if an active reactor is nearby. */
    public static void sendStats(Player player) {
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
    }
}
