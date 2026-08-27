package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ReactorCommand implements CommandExecutor {

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>Only players can use this command."));
            return true;
        }

        // =========================
        // PERMISSION CHECK
        // =========================
        if (!player.hasPermission("ui.command.reactor")) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>У вас нет прав на использование этой команды!"));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("assemble")) {
            player.sendMessage(MessageUtil.parse("<red>Usage: /reactor assemble <type>"));
            return true;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "dark_synthesis" -> assembleDarkSynthesis(player);
            case "magnet" -> assembleMagnet(player);
            default -> player.sendMessage(MessageUtil.parse("<red>Неизвестный тип механизма: " + type));
        }

        return true;
    }

    // =========================
    // DFC STATS — show reactor stats (called from PluginReloadCommand)
    // =========================
    public static boolean showReactorStats(Player player) {

        ReactorManager reactor = ReactorManager.getInstance();

        if (reactor == null || !reactor.isValid()) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Активных реакторов не найдено."));
            return true;
        }

        Location playerLoc = player.getLocation();
        Location reactorLoc = reactor.getReactorLocation();

        if (reactorLoc == null || !playerLoc.getWorld().equals(reactorLoc.getWorld())) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Рядом нет активного реактора."));
            return true;
        }

        double distance = playerLoc.distance(reactorLoc);
        if (distance > 50) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>Рядом нет активного реактора (ближайший в <white>"
                    + String.format("%.1f", distance) + "<gray> м)."));
            return true;
        }

        String status;
        if (reactor.isMeltdownCountdown()) {
            status = "<dark_red>!!! <red>Взрыв неизбежен <dark_red>!!!";
        } else if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) {
            status = "<yellow>Деградация";
        } else {
            status = "<green>Нормальный";
        }

        int meltdownSecs = reactor.isMeltdownCountdown()
                ? (reactor.getMeltdownTimer() / 20)
                : 0;

        player.sendMessage(MessageUtil.parse("<dark_gray>┌────────────────────────────────┐"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_red>Р.Т.С <dark_gray>» <white>Статистика реактора"));
        player.sendMessage(MessageUtil.parse("<dark_gray>├────────────────────────────────┤"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>ID: <white>" + reactor.getReactorId()));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Статус: " + status));
        if (reactor.isMeltdownCountdown()) {
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Детонация: <red>" + meltdownSecs + " сек"));
        }
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Дист: <white>" + String.format("%.1f", distance) + " м"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gold>═[ <yellow>Данные ядра <gold>]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Температура:  <white>" + reactor.getDisplayCoreTemp() + " C*"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Давление:    <white>" + reactor.getDisplayCorePress() + " kPa"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Целостность: <white>" + reactor.getDisplayCoreShInt() + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gold>═[═══════════]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_aqua>═[ <aqua>Данные корпуса <dark_aqua>]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Температура:  <white>" + reactor.getDisplayCoreCaseTemp() + " C*"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Давление:    <white>" + reactor.getDisplayCoreCasePress() + " kPa"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Целостность: <white>" + reactor.getDisplayCoreCaseInt() + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_aqua>═[═══════════]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_purple>═[ <light_purple>Данные рецепта <dark_purple>]═"));
        int recipePct = reactor.getDisplayRecipeTime();
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Прогресс:   <white>" + recipePct + " %"));
        String recipeStatus;
        if (recipePct <= 0) {
            recipeStatus = "<gray>Бездействует";
        } else if (recipePct < 100) {
            recipeStatus = "<yellow>Готовится";
        } else {
            recipeStatus = "<green>Завершён";
        }
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Статус:     " + recipeStatus));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Износ:      <white>" + reactor.getDisplayReactorWear() + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Выработка:  <white>" + reactor.getDisplayEnergyRate() + " E/сек"));
        if (reactor.isSelfDestruct() && !reactor.isMeltdownCountdown()) {
            player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Самоликвид: <red>Активен"));
        }
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_purple>═[═══════════]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Позиция: <white>"
                + reactorLoc.getBlockX() + " "
                + reactorLoc.getBlockY() + " "
                + reactorLoc.getBlockZ()
                + " <gray>(мир: <white>" + reactorLoc.getWorld().getName() + "<gray>)"));
        player.sendMessage(MessageUtil.parse("<dark_gray>└────────────────────────────────┘"));
        player.sendMessage("");

        return true;
    }

    // =========================
    // DARK SYNTHESIS REACTOR (without netherite scrap)
    // =========================
    public static void assembleDarkSynthesis(Player player) {

        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null) return;

        // =========================
        // CHECK PENDING ASSEMBLY
        // =========================
        ReactorManager.PendingAssembly pending = ReactorManager.getPendingAssembly(player, "dark_synthesis");

        if (pending == null) {
            player.sendMessage(MessageUtil.parse("<red>Сначала нажмите SHIFT+ПКМ по рамке реактора!"));
            return;
        }

        // =========================
        // VALIDATE STRUCTURE — with detailed errors
        // =========================
        java.util.List<String> errors = ReactorStructure.getValidationErrors(pending.center());
        if (!errors.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Структура реактора повреждена! <gray>Найдены ошибки:"));
            for (String err : errors) {
                player.sendMessage(MessageUtil.parse("<dark_gray> • <white>" + err));
            }
            player.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // CHECK IF ALREADY ACTIVE
        // =========================
        Location existing = reactor.getReactorLocation();
        if (existing != null) {
            if (existing.equals(pending.center())) {
                player.sendMessage(MessageUtil.parse("<yellow>Реактор уже активен на этом месте!"));
                ReactorManager.clearPendingAssembly(player);
                return;
            }
            player.sendMessage(MessageUtil.parse("<red>Другой реактор уже активен! Сломайте его сначала."));
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // REMOVE ITEM FRAME & DROP IT
        // =========================
        ItemFrame frame = pending.frame();
        if (frame != null && frame.isValid() && !frame.isDead()) {
            Location frameLoc = frame.getLocation();
            frame.getWorld().dropItemNaturally(
                    frameLoc,
                    new ItemStack(Material.ITEM_FRAME)
            );
            frame.remove();
        }

        // =========================
        // ACTIVATE REACTOR
        // =========================
        reactor.setReactorLocation(pending.center());

        // =========================
        // NAME THE FUEL BARRELS
        // =========================
        nameBarrel(pending.center(), 0, -3, -2, "<gold>Топливо: <aqua>Алмазные блоки");
        nameBarrel(pending.center(), 0, -3, 2, "<gold>Топливо: <yellow>Золотые блоки");

        player.sendMessage(MessageUtil.parse("<green>✔ <white>Реактор тёмного синтеза собран! <dark_gray>(ID: " + reactor.getReactorId() + ")"));
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Температура ядра: <white>" + reactor.getCoreTemp() + " C*"));
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Давление: <white>" + reactor.getCorePress() + " kPa"));
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Целостность оболочки: <white>" + reactor.getCoreShInt() + "%"));
        player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Топливо: <aqua>алмазные блоки <gray>→ левая бочка, <yellow>золотые блоки <gray>→ правая бочка"));

        ReactorManager.clearPendingAssembly(player);

        ConsoleLogger.info(
                "[Reactor] Assembled by " + player.getName()
                        + " at " + pending.center()
        );
    }

    // =========================
    // 🏆 POWER TIER NAME (shared static)
    // =========================
    public static String getMagnetPowerTierStatic(int power) {
        if (power >= 10000000) return "<obfuscated>✧ <dark_red>✧✧ АБСОЛЮТНАЯ БЕСКОНЕЧНОСТЬ ✧✧ <obfuscated>✧ <dark_gray>(" + power + ")";
        if (power >= 5000000) return "<dark_red>✧✧ БЕСКОНЕЧНАЯ БЕЗДНА ✧✧ <dark_gray>(" + power + ")";
        if (power >= 2500000) return "<red>✦ ВСЕЛЕНСКАЯ КАТАСТРОФА ✦ <dark_gray>(" + power + ")";
        if (power >= 1000000) return "<light_purple>✧ ПЕРВОЗДАННАЯ СИНГУЛЯРНОСТЬ ✧ <dark_gray>(" + power + ")";
        if (power >= 500000) return "<gold>☠ НЕПОСТИЖИМАЯ ☠ <dark_gray>(" + power + ")";
        if (power >= 250000) return "<dark_aqua>✦ БОГОПОДОБНАЯ ✦ <dark_gray>(" + power + ")";
        if (power >= 100000) return "<dark_red>✧✧✧ ВСЕСОКРУШАЮЩАЯ СИНГУЛЯРНОСТЬ ✧✧✧ <dark_gray>(" + power + ")";
        if (power >= 50000) return "<red>☠ АБСОЛЮТНАЯ СИНГУЛЯРНОСТЬ ☠ <dark_gray>(" + power + ")";
        if (power >= 25000) return "<gold>⚡ БОЖЕСТВЕННАЯ СИНГУЛЯРНОСТЬ ⚡ <dark_gray>(" + power + ")";
        if (power >= 10000) return "<light_purple>✧✧ НЕПРЕВЗОЙДЁННАЯ ✧✧ <dark_gray>(" + power + ")";
        if (power >= 5000) return "<dark_purple>✦ ТРАНСЦЕНДЕНТНАЯ ✦ <dark_gray>(" + power + ")";
        if (power >= 2500) return "<blue>⚜ СИНГУЛЯРНАЯ ⚜ <dark_gray>(" + power + ")";
        if (power >= 1000) return "<dark_aqua>✦ БЕСКОНЕЧНАЯ ✦ <dark_gray>(" + power + ")";
        if (power >= 500) return "<dark_purple>✧✧ АБСОЛЮТНАЯ ✧✧ <dark_gray>(" + power + ")";
        if (power >= 300) return "<dark_purple>☯ КОСМИЧЕСКАЯ ☯ <dark_gray>(" + power + ")";
        if (power >= 200) return "<light_purple>✦ ТИТАНИЧЕСКАЯ ✦ <dark_gray>(" + power + ")";
        if (power >= 150) return "<light_purple>◈ ЛЕГЕНДАРНАЯ ◈ <dark_gray>(" + power + ")";
        if (power >= 100) return "<red>☆ НЕВЕРОЯТНАЯ ☆ <dark_gray>(" + power + ")";
        if (power >= 75) return "<red>♦ ЧРЕЗВЫЧАЙНАЯ ♦ <dark_gray>(" + power + ")";
        if (power >= 50) return "<gold>★ ИСКЛЮЧИТЕЛЬНАЯ ★ <dark_gray>(" + power + ")";
        if (power >= 30) return "<gold>⬆ ОЧЕНЬ СИЛЬНАЯ ⬆ <dark_gray>(" + power + ")";
        if (power >= 20) return "<yellow>⬆ СИЛЬНАЯ ⬆ <dark_gray>(" + power + ")";
        if (power >= 12) return "<yellow>⬆ ВЫШЕ СРЕДНЕГО ⬆ <dark_gray>(" + power + ")";
        if (power >= 7) return "<green>➤ СРЕДНЯЯ ➤ <dark_gray>(" + power + ")";
        if (power >= 4) return "<gray>➤ НИЖЕ СРЕДНЕГО ➤ <dark_gray>(" + power + ")";
        if (power >= 2) return "<gray>▸ СЛАБАЯ ▸ <dark_gray>(" + power + ")";
        return "<gray>▸ ОЧЕНЬ СЛАБАЯ ▸ <dark_gray>(" + power + ")";
    }

    // =========================
    // MAGNET
    // =========================
    public static void assembleMagnet(Player player) {

        // =========================
        // CHECK PENDING ASSEMBLY
        // =========================
        ReactorManager.PendingAssembly pending = ReactorManager.getPendingAssembly(player, "magnet");

        if (pending == null) {
            player.sendMessage(MessageUtil.parse("<red>Сначала нажмите SHIFT+ПКМ по рамке на магните!"));
            return;
        }

        Location loc = pending.center();

        // =========================
        // VALIDATE — the block must be LODESTONE
        // =========================
        if (loc.getBlock().getType() != Material.LODESTONE) {
            player.sendMessage(MessageUtil.parse("<red>Магнитный камень (LODESTONE) не найден!"));
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // CHECK IF ALREADY ACTIVE
        // =========================
        if (MagnetManager.isActive(loc)) {
            player.sendMessage(MessageUtil.parse("<yellow>Магнит уже активен на этом месте!"));
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // REMOVE ITEM FRAME & DROP IT
        // =========================
        ItemFrame frame = pending.frame();
        if (frame != null && frame.isValid() && !frame.isDead()) {
            Location frameLoc = frame.getLocation();
            frame.getWorld().dropItemNaturally(
                    frameLoc,
                    new ItemStack(Material.ITEM_FRAME)
            );
            frame.remove();
        }

        // =========================
        // ACTIVATE MAGNET — async structure scanning
        // =========================
        MagnetManager.activateAsync(loc, player);

        ReactorManager.clearPendingAssembly(player);

        ConsoleLogger.info(
                "[Magnet] Assembled by " + player.getName()
                        + " at " + loc
        );
    }

    // =========================
    // NAME BARREL HELPER
    // =========================
    private static void nameBarrel(Location base, int dx, int dy, int dz, String displayName) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() == Material.BARREL) {
            Barrel barrel = (Barrel) block.getState();
            barrel.customName(MessageUtil.parse(displayName));
            barrel.update();
        }
    }
}
