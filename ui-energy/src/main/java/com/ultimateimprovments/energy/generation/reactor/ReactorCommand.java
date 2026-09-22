package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Static assembly helpers for the reactor and magnet structures.
 * <p>
 * Historically this class was also the executor of the standalone
 * {@code /reactor} command; that command has been removed — structure
 * assembly now lives exclusively behind {@code /ui structures ...}.
 */
public final class ReactorCommand {

    private ReactorCommand() {}

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
        // VALIDATE STRUCTURE — full NBT template check (with detailed fixes)
        // =========================
        java.util.List<String> errors = validateReactorByTemplate(pending.center());
        if (!errors.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Структура реактора собрана неверно! <gray>Что нужно исправить:"));
            int shown = 0;
            for (String err : errors) {
                if (shown++ >= 15) {
                    player.sendMessage(MessageUtil.parse("<dark_gray> • <gray>...и ещё " + (errors.size() - shown + 1) + " исправлений"));
                    break;
                }
                player.sendMessage(MessageUtil.parse("<dark_gray> • <gray>" + err));
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
    // NBT TEMPLATE VALIDATION
    // =========================
    /**
     * Validate the reactor against the full NBT template (every solid AND air cell).
     * Falls back to the legacy key-blocks validator when the template is not loaded
     * or contains no cells.
     */
    private static java.util.List<String> validateReactorByTemplate(Location center) {
        com.ultimateimprovments.util.StructureTemplate tmpl =
                com.ultimateimprovments.util.StructureTemplate.get("reactor");

        if (tmpl == null || tmpl.totalCells() == 0) {
            // Template unavailable → legacy validation (key blocks only)
            return ReactorStructure.getValidationErrors(center);
        }

        java.util.List<String> errors = new java.util.ArrayList<>();
        for (var fix : tmpl.checkAt(center).fixes()) {
            errors.add(com.ultimateimprovments.util.StructureTemplate.formatFix(fix, center));
        }
        return errors;
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
