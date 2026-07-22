package com.ultimateimprovements.energy.generation.reactor;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.mechanics.environment.magnet.MagnetManager;

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
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // =========================
        // PERMISSION CHECK
        // =========================
        if (!player.hasPermission("mcplugin.command.reactor")) {
            player.sendMessage("§4❌ §cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("assemble")) {
            player.sendMessage("§cUsage: /reactor assemble <type>");
            return true;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "dark_synthesis" -> assembleDarkSynthesis(player);
            case "magnet" -> assembleMagnet(player);
            default -> player.sendMessage("§cНеизвестный тип механизма: " + type);
        }

        return true;
    }

    // =========================
    // DFC STATS — show reactor stats (called from PluginReloadCommand)
    // =========================
    public static boolean showReactorStats(Player player) {

        ReactorManager reactor = ReactorManager.getInstance();

        if (reactor == null || !reactor.isValid()) {
            player.sendMessage("§4❌ §cError: §7Активных реакторов не найдено.");
            return true;
        }

        Location playerLoc = player.getLocation();
        Location reactorLoc = reactor.getReactorLocation();

        if (reactorLoc == null || !playerLoc.getWorld().equals(reactorLoc.getWorld())) {
            player.sendMessage("§4❌ §cError: §7Рядом нет активного реактора.");
            return true;
        }

        double distance = playerLoc.distance(reactorLoc);
        if (distance > 50) {
            player.sendMessage("§4❌ §cError: §7Рядом нет активного реактора (ближайший в §f"
                    + String.format("%.1f", distance) + "§7 м).");
            return true;
        }

        String status;
        if (reactor.isMeltdownCountdown()) {
            status = "§4!!! §cВзрыв неизбежен §4!!!";
        } else if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) {
            status = "§eДеградация";
        } else {
            status = "§aНормальный";
        }

        int meltdownSecs = reactor.isMeltdownCountdown()
                ? (reactor.getMeltdownTimer() / 20)
                : 0;

        player.sendMessage("§8┌────────────────────────────────┐");
        player.sendMessage("§8│ §4Р.Т.С §8» §fСтатистика реактора");
        player.sendMessage("§8├────────────────────────────────┤");
        player.sendMessage("§8│ §7ID: §f" + reactor.getReactorId());
        player.sendMessage("§8│ §7Статус: " + status);
        if (reactor.isMeltdownCountdown()) {
            player.sendMessage("§8│ §7Детонация: §c" + meltdownSecs + " сек");
        }
        player.sendMessage("§8│ §7Дист: §f" + String.format("%.1f", distance) + " м");
        player.sendMessage("§8│ §6═[ §eДанные ядра §6]═");
        player.sendMessage("§8│ §7Температура:  §f" + reactor.getDisplayCoreTemp() + " C*");
        player.sendMessage("§8│ §7Давление:    §f" + reactor.getDisplayCorePress() + " kPa");
        player.sendMessage("§8│ §7Целостность: §f" + reactor.getDisplayCoreShInt() + " %");
        player.sendMessage("§8│ §6═[═══════════]═");
        player.sendMessage("§8│ §3═[ §bДанные корпуса §3]═");
        player.sendMessage("§8│ §7Температура:  §f" + reactor.getDisplayCoreCaseTemp() + " C*");
        player.sendMessage("§8│ §7Давление:    §f" + reactor.getDisplayCoreCasePress() + " kPa");
        player.sendMessage("§8│ §7Целостность: §f" + reactor.getDisplayCoreCaseInt() + " %");
        player.sendMessage("§8│ §3═[═══════════]═");
        player.sendMessage("§8│ §5═[ §dДанные рецепта §5]═");
        int recipePct = reactor.getDisplayRecipeTime();
        player.sendMessage("§8│ §7Прогресс:   §f" + recipePct + " %");
        String recipeStatus;
        if (recipePct <= 0) {
            recipeStatus = "§7Бездействует";
        } else if (recipePct < 100) {
            recipeStatus = "§eГотовится";
        } else {
            recipeStatus = "§aЗавершён";
        }
        player.sendMessage("§8│ §7Статус:     " + recipeStatus);
        player.sendMessage("§8│ §7Износ:      §f" + reactor.getDisplayReactorWear() + " %");
        player.sendMessage("§8│ §7Выработка:  §f" + reactor.getDisplayEnergyRate() + " E/сек");
        if (reactor.isSelfDestruct() && !reactor.isMeltdownCountdown()) {
            player.sendMessage("§8│ §7Самоликвид: §cАктивен");
        }
        player.sendMessage("§8│ §5═[═══════════]═");
        player.sendMessage("§8│ §7Позиция: §f"
                + reactorLoc.getBlockX() + " "
                + reactorLoc.getBlockY() + " "
                + reactorLoc.getBlockZ()
                + " §7(мир: §f" + reactorLoc.getWorld().getName() + "§7)");
        player.sendMessage("§8└────────────────────────────────┘");
        player.sendMessage("");

        return true;
    }

    // =========================
    // DARK SYNTHESIS REACTOR (без незеритового скрапа)
    // =========================
    public static void assembleDarkSynthesis(Player player) {

        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null) return;

        // =========================
        // CHECK PENDING ASSEMBLY
        // =========================
        ReactorManager.PendingAssembly pending = ReactorManager.getPendingAssembly(player, "dark_synthesis");

        if (pending == null) {
            player.sendMessage("§cСначала нажмите SHIFT+ПКМ по рамке реактора!");
            return;
        }

        // =========================
        // VALIDATE STRUCTURE — с детальными ошибками
        // =========================
        java.util.List<String> errors = ReactorStructure.getValidationErrors(pending.center());
        if (!errors.isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§4❌ §cСтруктура реактора повреждена! §7Найдены ошибки:");
            for (String err : errors) {
                player.sendMessage("§8 • §f" + err);
            }
            player.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // CHECK IF ALREADY ACTIVE
        // =========================
        Location existing = reactor.getReactorLocation();
        if (existing != null) {
            if (existing.equals(pending.center())) {
                player.sendMessage("§eРеактор уже активен на этом месте!");
                ReactorManager.clearPendingAssembly(player);
                return;
            }
            player.sendMessage("§cДругой реактор уже активен! Сломайте его сначала.");
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
        nameBarrel(pending.center(), 0, -3, -2, "§6Топливо: §bАлмазные блоки");
        nameBarrel(pending.center(), 0, -3, 2, "§6Топливо: §eЗолотые блоки");

        player.sendMessage("§a✔ §fРеактор тёмного синтеза собран! §8(ID: " + reactor.getReactorId() + ")");
        player.sendMessage("§8┃ §7Температура ядра: §f" + reactor.getCoreTemp() + " C*");
        player.sendMessage("§8┃ §7Давление: §f" + reactor.getCorePress() + " kPa");
        player.sendMessage("§8┃ §7Целостность оболочки: §f" + reactor.getCoreShInt() + "%");
        player.sendMessage("§8┃ §7Топливо: §bалмазные блоки §7→ левая бочка, §eзолотые блоки §7→ правая бочка");

        ReactorManager.clearPendingAssembly(player);

        ConsoleLogger.info(
                "[Reactor] Assembled by " + player.getName()
                        + " at " + pending.center()
        );
    }

    // =========================
    // 🏆 НАЗВАНИЕ ТИРА ПО МОЩНОСТИ (shared static)
    // =========================
    public static String getMagnetPowerTierStatic(int power) {
        if (power >= 10000000) return "§k✧ §4✧✧ АБСОЛЮТНАЯ БЕСКОНЕЧНОСТЬ ✧✧ §k✧ §8(" + power + ")";
        if (power >= 5000000) return "§4✧✧ БЕСКОНЕЧНАЯ БЕЗДНА ✧✧ §8(" + power + ")";
        if (power >= 2500000) return "§c✦ ВСЕЛЕНСКАЯ КАТАСТРОФА ✦ §8(" + power + ")";
        if (power >= 1000000) return "§d✧ ПЕРВОЗДАННАЯ СИНГУЛЯРНОСТЬ ✧ §8(" + power + ")";
        if (power >= 500000) return "§6☠ НЕПОСТИЖИМАЯ ☠ §8(" + power + ")";
        if (power >= 250000) return "§3✦ БОГОПОДОБНАЯ ✦ §8(" + power + ")";
        if (power >= 100000) return "§4✧✧✧ ВСЕСОКРУШАЮЩАЯ СИНГУЛЯРНОСТЬ ✧✧✧ §8(" + power + ")";
        if (power >= 50000) return "§c☠ АБСОЛЮТНАЯ СИНГУЛЯРНОСТЬ ☠ §8(" + power + ")";
        if (power >= 25000) return "§6⚡ БОЖЕСТВЕННАЯ СИНГУЛЯРНОСТЬ ⚡ §8(" + power + ")";
        if (power >= 10000) return "§d✧✧ НЕПРЕВЗОЙДЁННАЯ ✧✧ §8(" + power + ")";
        if (power >= 5000) return "§5✦ ТРАНСЦЕНДЕНТНАЯ ✦ §8(" + power + ")";
        if (power >= 2500) return "§9⚜ СИНГУЛЯРНАЯ ⚜ §8(" + power + ")";
        if (power >= 1000) return "§3✦ БЕСКОНЕЧНАЯ ✦ §8(" + power + ")";
        if (power >= 500) return "§5✧✧ АБСОЛЮТНАЯ ✧✧ §8(" + power + ")";
        if (power >= 300) return "§5☯ КОСМИЧЕСКАЯ ☯ §8(" + power + ")";
        if (power >= 200) return "§d✦ ТИТАНИЧЕСКАЯ ✦ §8(" + power + ")";
        if (power >= 150) return "§d◈ ЛЕГЕНДАРНАЯ ◈ §8(" + power + ")";
        if (power >= 100) return "§c☆ НЕВЕРОЯТНАЯ ☆ §8(" + power + ")";
        if (power >= 75) return "§c♦ ЧРЕЗВЫЧАЙНАЯ ♦ §8(" + power + ")";
        if (power >= 50) return "§6★ ИСКЛЮЧИТЕЛЬНАЯ ★ §8(" + power + ")";
        if (power >= 30) return "§6⬆ ОЧЕНЬ СИЛЬНАЯ ⬆ §8(" + power + ")";
        if (power >= 20) return "§e⬆ СИЛЬНАЯ ⬆ §8(" + power + ")";
        if (power >= 12) return "§e⬆ ВЫШЕ СРЕДНЕГО ⬆ §8(" + power + ")";
        if (power >= 7) return "§a➤ СРЕДНЯЯ ➤ §8(" + power + ")";
        if (power >= 4) return "§7➤ НИЖЕ СРЕДНЕГО ➤ §8(" + power + ")";
        if (power >= 2) return "§7▸ СЛАБАЯ ▸ §8(" + power + ")";
        return "§7▸ ОЧЕНЬ СЛАБАЯ ▸ §8(" + power + ")";
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
            player.sendMessage("§cСначала нажмите SHIFT+ПКМ по рамке на магните!");
            return;
        }

        Location loc = pending.center();

        // =========================
        // VALIDATE — блок должен быть LODESTONE
        // =========================
        if (loc.getBlock().getType() != Material.LODESTONE) {
            player.sendMessage("§cМагнитный камень (LODESTONE) не найден!");
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // CHECK IF ALREADY ACTIVE
        // =========================
        if (MagnetManager.isActive(loc)) {
            player.sendMessage("§eМагнит уже активен на этом месте!");
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
        // ACTIVATE MAGNET — асинхронное сканирование структуры
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
            barrel.setCustomName(displayName);
            barrel.update();
        }
    }
}
