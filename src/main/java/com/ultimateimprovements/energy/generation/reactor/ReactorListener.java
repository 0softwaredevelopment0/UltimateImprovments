package com.ultimateimprovements.energy.generation.reactor;

import com.ultimateimprovements.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovements.mechanics.environment.lightning.LightningStructure;
import com.ultimateimprovements.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovements.mechanics.environment.magnet.MagnetStructure;
import com.ultimateimprovements.util.Materials;
import com.ultimateimprovements.util.StructureTemplate;
import com.ultimateimprovements.util.LocationUtil;
import com.ultimateimprovements.energy.generation.basic.GeneratorManager;
import com.ultimateimprovements.energy.generation.basic.GeneratorStructure;
import com.ultimateimprovements.energy.storage.battery.BatteryManager;
import com.ultimateimprovements.energy.consumption.light.LightManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ReactorListener implements Listener {

    // =========================
    // TEMPLATE LOADING FLAG (предотвращает повторные попытки при ошибке загрузки)
    // =========================
    private static boolean templatesLoaded = false;

    // =========================
    // REACTOR BLOCKS (for monitoring)
    // =========================
    private static final Material[] KEY_BLOCKS = {
            Materials.WAXED_COPPER_BULB,
            Material.DIAMOND_BLOCK,
            Material.GOLD_BLOCK,
            Material.OAK_SIGN, Material.OAK_WALL_SIGN,
            Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN,
            Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
            Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN,
            Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN,
            Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN,
            Material.CHERRY_SIGN, Material.CHERRY_WALL_SIGN,
            Material.MANGROVE_SIGN, Material.MANGROVE_WALL_SIGN,
            Material.CRIMSON_SIGN, Material.CRIMSON_WALL_SIGN,
            Material.WARPED_SIGN, Material.WARPED_WALL_SIGN,
            Material.PALE_OAK_SIGN, Material.PALE_OAK_WALL_SIGN
    };

    // =========================
    // ITEM FRAME INTERACT → AUTO-DETECT + ASSEMBLE
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent e) {

        Entity clicked = e.getRightClicked();

        if (!(clicked instanceof ItemFrame frame)) {
            return;
        }

        Player player = e.getPlayer();

        // =========================
        // SHIFT+ПКМ — авто-определение и сборка (без меню)
        // =========================
        if (player.isSneaking()) {
            e.setCancelled(true);
            autoDetectAndAssemble(player, frame);
            return;
        }

        // =========================
        // Обычный ПКМ — показать информацию
        // =========================
        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null) return;

        // Проверка: часть реактора?
        Location reactorCenter = ReactorStructure.findCenter(clicked.getLocation());
        if (reactorCenter != null && reactor.getReactorLocation() != null) {
            player.sendMessage(
                    "§8[§cР.Т.С§8] §7ID: §f" + reactor.getReactorId()
                            + " §8| §fT=" + reactor.getCoreTemp()
                            + " §8| §fP=" + reactor.getCorePress()
                            + " §8| §fI=" + reactor.getCoreShInt() + "%"
            );
            return;
        }

        // Проверка: активный магнит?
        if (MagnetStructure.isActive(clicked.getLocation())) {
            player.sendMessage("§8[§bМагнит§8] §7Уже активен");
            return;
        }

        // Проверка: активная структура молний?
        Location lightningCenter = LightningStructure.findCenter(clicked.getLocation());
        if (lightningCenter != null && LightningManager.isActive(lightningCenter)) {
            player.sendMessage("§8[§e⚡ Молнии§8] §7Активна §8| §f"
                    + lightningCenter.getBlockX() + " " + lightningCenter.getBlockY() + " " + lightningCenter.getBlockZ());
            return;
        }

    }

    // =========================
    // BLOCK BREAK — МАГНИТ (динамический пересчёт)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {

        Block block = e.getBlock();
        Location loc = LocationUtil.normalize(block.getLocation());
        Player player = e.getPlayer();

        // =========================
        // 🧲 МАГНИТ: LODESTONE в активном кластере → пересчёт
        // =========================
        if (block.getType() == Material.LODESTONE && MagnetManager.isActive(loc)) {
            MagnetManager.onBlockBroken(loc, player);
            return;
        }

        // =========================
        // ⚡ МОЛНИИ: любой блок активной структуры → disassemble
        // =========================
        Location lightningCenter = LightningManager.getCenterForBlock(loc);
        if (lightningCenter != null) {
            LightningManager.disassemble(lightningCenter);
            if (player != null) {
                player.sendMessage("§e⚡ Структура молний разрушена и деактивирована!"
                        + " §8[§7" + lightningCenter.getBlockX() + " " + lightningCenter.getBlockY() + " " + lightningCenter.getBlockZ() + "§8]");
            }
            return;
        }

        // =========================
        // ⚛ РЕАКТОР: проверка блоков реактора
        // =========================
        if (!isReactorBlock(block.getType())) {
            return;
        }

        ReactorManager reactor = ReactorManager.getInstance();

        if (reactor == null) return;

        Location reactorLoc = reactor.getReactorLocation();

        if (reactorLoc == null) return;

        // Check if broken block is within reactor structure
        if (!isWithinStructure(reactorLoc, loc)) {
            return;
        }

        reactor.setReactorLocation(null);
        if (player != null) {
            player.sendMessage("§c❕ Реактор разрушен и деактивирован!"
                    + " §8[§7" + reactorLoc.getBlockX() + " " + reactorLoc.getBlockY() + " " + reactorLoc.getBlockZ() + "§8]");
        }
    }

    // =========================
    // BLOCK PLACE — МАГНИТ (динамическое расширение)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMagnetBlockPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.LODESTONE) {
            MagnetManager.onBlockPlaced(
                    LocationUtil.normalize(e.getBlock().getLocation())
            );
        }
    }

    // =========================
    // BLOCK PLACE
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {

        Block block = e.getBlock();
        Location loc = LocationUtil.normalize(block.getLocation());

        if (!isReactorBlock(block.getType())) {
            return;
        }

        ReactorManager reactor = ReactorManager.getInstance();

        if (reactor == null) return;

        // If we already have a valid reactor, re-validate the structure
        if (reactor.getReactorLocation() != null) {
            reactor.validateStructure();
        }
        // Note: Reactor is no longer auto-activated on block place.
        // Player must use SHIFT+ПКМ on item frame to open assembly menu.
    }



    // =========================
    // AUTO-DETECT STRUCTURE TYPE & ASSEMBLE
    // Сканирует радиус 5 блоков от рамки и сравнивает с NBT-шаблонами.
    // =========================
    private void autoDetectAndAssemble(Player player, ItemFrame frame) {

        Location frameLoc = LocationUtil.normalize(frame.getLocation());
        if (frameLoc == null) {
            player.sendMessage("§4❌ §cОшибка определения позиции рамки!");
            return;
        }

        // =========================
        // 1. СКАНИРОВАНИЕ ПО NBT-ШАБЛОНАМ
        // Загружаем шаблоны при первом вызове (однократно)
        // =========================
        if (!templatesLoaded) {
            StructureTemplate.initAll();
            templatesLoaded = true;
        }

        // Проверка: были ли ошибки загрузки шаблонов
        StructureTemplate lightningTmpl = StructureTemplate.get("lightning");
        StructureTemplate reactorTmpl = StructureTemplate.get("reactor");

        String lightningErr = StructureTemplate.getTemplateError("lightning");
        String reactorErr = StructureTemplate.getTemplateError("reactor");

        if (lightningErr != null || reactorErr != null) {
            player.sendMessage("§4⚠ §cОшибка загрузки NBT-шаблонов структур:");
            if (lightningErr != null)
                player.sendMessage("  §8• §eМолнии§8: §c" + lightningErr);
            if (reactorErr != null)
                player.sendMessage("  §8• §cРеактор§8: §c" + reactorErr);
            player.sendMessage("§7Проверьте консоль сервера для деталей.");
        }

        // =========================
        // 1a. Шаблон молний (lightning)
        // =========================
        if (lightningTmpl != null) {
            Location center = lightningTmpl.findMatch(frameLoc, 5);
            if (center != null) {
                if (LightningManager.isActive(center)) {
                    player.sendMessage("§e⚡ Структура молний уже собрана!");
                    player.sendMessage("§7Команды: §f/mp str lightning enable§7/§cdisable");
                    return;
                }
                player.sendMessage("§8[§e⚡ Молнии§8] §7Обнаружена структура молний — сборка...");
                LightningManager.assemble(center, frame, player);
                return;
            }
        }

        // =========================
        // 1b. Шаблон реактора (reactor)
        // =========================
        if (reactorTmpl != null) {
            Location center = reactorTmpl.findMatch(frameLoc, 5);
            if (center != null) {
                ReactorManager reactor = ReactorManager.getInstance();
                if (reactor != null) {
                    Location existing = reactor.getReactorLocation();
                    if (existing != null && existing.equals(center)) {
                        player.sendMessage("§eРеактор уже активен на этом месте!");
                        return;
                    }
                }
                ReactorManager.setPendingAssembly(player, center, frame, "dark_synthesis");
                player.sendMessage("§8[§cРеактор§8] §7Обнаружен реактор — сборка...");
                player.performCommand("reactor assemble dark_synthesis");
                return;
            }
        }

        // =========================
        // 2. ПРОВЕРКА: МАГНИТ (LODESTONE — не NBT, а мульти-блочный)
        // =========================
        Location attachedLoc = LocationUtil.normalize(
                frame.getLocation().getBlock().getRelative(
                        frame.getFacing().getOppositeFace()
                ).getLocation()
        );
        if (attachedLoc != null && attachedLoc.getBlock().getType() == Material.LODESTONE) {
            if (MagnetManager.isActive(attachedLoc)) {
                player.sendMessage("§eМагнит уже активен на этом месте!");
                return;
            }
            ReactorManager.setPendingAssembly(player, attachedLoc, frame, "magnet");
            player.sendMessage("§8[§bМагнит§8] §7Обнаружен магнит — сборка...");
            player.performCommand("reactor assemble magnet");
            return;
        }

        // =========================
        // 3. ПРОВЕРКА: БАТАРЕЯ (WAXED_COPPER_GRATE + рамка)
        // =========================
        Location attachedLoc2 = LocationUtil.normalize(
                frame.getLocation().getBlock().getRelative(
                        frame.getFacing().getOppositeFace()
                ).getLocation()
        );
        if (attachedLoc2 != null && attachedLoc2.getBlock().getType() == Materials.WAXED_COPPER_GRATE) {
            if (BatteryManager.isActive(attachedLoc2)) {
                player.sendMessage("§eБатарея уже собрана на этом месте!");
                return;
            }
            BatteryManager.assemble(attachedLoc2, player);
            return;
        }

        // =========================
        // 4. ПРОВЕРКА: ЛАМПОЧКА (REDSTONE_LAMP + рамка)
        // =========================
        if (attachedLoc2 != null && attachedLoc2.getBlock().getType() == Material.REDSTONE_LAMP) {
            if (LightManager.isActive(attachedLoc2)) {
                player.sendMessage("§eЛампочка уже собрана на этом месте!");
                return;
            }
            LightManager.assemble(attachedLoc2, player);
            return;
        }

        // =========================
        // 5. ПРОВЕРКА: ГЕНЕРАТОР (BLAST_FURNACE + рамка сверху)
        // =========================
        // Блок под рамкой
        Location generatorLoc = LocationUtil.normalize(
                frame.getLocation().clone().add(0, -1, 0)
        );
        if (generatorLoc != null
                && generatorLoc.getBlock().getType() == Materials.BLAST_FURNACE
                && GeneratorStructure.isValid(generatorLoc)) {
            // Проверяем кабель рядом
            if (GeneratorManager.hasNearbyCable(generatorLoc)) {
                if (GeneratorManager.isAssembled(generatorLoc)) {
                    player.sendMessage("§eГенератор уже собран на этом месте!");
                    return;
                }
                GeneratorManager.assembleFromFrame(player, generatorLoc);
                return;
            } else {
                player.sendMessage("§4❌ §cНет кабеля рядом с плавильной печью!");
                return;
            }
        }

        // =========================
        // 4. НИЧЕГО НЕ НАЙДЕНО
        // =========================
        player.sendMessage("§c❌ Структура не распознана!");
        player.sendMessage("§7Убедитесь, что все блоки структуры соответствуют NBT-шаблону.");
        player.sendMessage("§7Поддерживаемые структуры: громоотвод (молнии), LODESTONE (магнит),");
        player.sendMessage("§7реактор (алмазная/золотая бочка с рамкой), BLAST_FURNACE + рамка (генератор),");
        player.sendMessage("§7WAXED_COPPER_GRATE (батарея), REDSTONE_LAMP (лампочка)");
    }

    // =========================
    // IS REACTOR BLOCK
    // =========================
    private boolean isReactorBlock(Material material) {

        for (Material m : KEY_BLOCKS) {
            if (m == material) return true;
        }

        return false;
    }

    // =========================
    // IS WITHIN STRUCTURE
    // =========================
    private boolean isWithinStructure(Location reactorLoc, Location checkLoc) {

        if (!reactorLoc.getWorld().equals(checkLoc.getWorld())) {
            return false;
        }

        int dx = Math.abs(reactorLoc.getBlockX() - checkLoc.getBlockX());
        int dy = Math.abs(reactorLoc.getBlockY() - checkLoc.getBlockY());
        int dz = Math.abs(reactorLoc.getBlockZ() - checkLoc.getBlockZ());

        // Structure is 5x6x6 from Y=-5 to Y=0 relative to frame
        return dx <= 3 && dy <= 5 && dz <= 3;
    }

    // =========================
    // SIGN CLICK → STATS
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent e) {

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        if (!isAnyWallSign(type)) return;

        Player player = e.getPlayer();
        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null || !reactor.isValid()) return;

        Location signLoc = block.getLocation();
        Location reactorLoc = reactor.getReactorLocation();
        if (reactorLoc == null) return;
        if (!signLoc.getWorld().equals(reactorLoc.getWorld())) return;

        // Check if sign is within reactor structure bounds
        if (!isWithinStructure(reactorLoc, signLoc)) return;

        // Prevent sign editor from opening
        e.setCancelled(true);

        // Open reactor stats
        player.performCommand("mcplugin str dfc stats");
    }

    // =========================
    // IS ANY WALL SIGN
    // =========================
    private boolean isAnyWallSign(Material mat) {
        return mat == Material.OAK_WALL_SIGN
            || mat == Material.DARK_OAK_WALL_SIGN
            || mat == Material.BIRCH_WALL_SIGN
            || mat == Material.SPRUCE_WALL_SIGN
            || mat == Material.JUNGLE_WALL_SIGN
            || mat == Material.ACACIA_WALL_SIGN
            || mat == Material.CHERRY_WALL_SIGN
            || mat == Material.MANGROVE_WALL_SIGN
            || mat == Material.CRIMSON_WALL_SIGN
            || mat == Material.WARPED_WALL_SIGN
            || mat == Material.PALE_OAK_WALL_SIGN;
    }
}
