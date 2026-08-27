package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovments.mechanics.environment.lightning.LightningStructure;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetStructure;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.StructureTemplate;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorStructure;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.consumption.light.LightManager;

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
    // TEMPLATE LOADING FLAG (prevents repeated attempts on load errors)
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
        // SHIFT+right-click — auto-detect and assemble (no menu)
        // =========================
        if (player.isSneaking()) {
            e.setCancelled(true);
            autoDetectAndAssemble(player, frame);
            return;
        }

        // =========================
        // Normal right-click — show info
        // =========================
        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null) return;

        // Check: part of a reactor?
        Location reactorCenter = ReactorStructure.findCenter(clicked.getLocation());
        if (reactorCenter != null && reactor.getReactorLocation() != null) {
            player.sendMessage(MessageUtil.parse(
                    "<dark_gray>[<red>Р.Т.С<dark_gray>] <gray>ID: <white>" + reactor.getReactorId()
                            + " <dark_gray>| <white>T=" + reactor.getCoreTemp()
                            + " <dark_gray>| <white>P=" + reactor.getCorePress()
                            + " <dark_gray>| <white>I=" + reactor.getCoreShInt() + "%"
            ));
            return;
        }

        // Check: an active magnet?
        if (MagnetStructure.isActive(clicked.getLocation())) {
            player.sendMessage(MessageUtil.parse("<dark_gray>[<aqua>Магнит<dark_gray>] <gray>Уже активен"));
            return;
        }

        // Check: an active lightning structure?
        Location lightningCenter = LightningStructure.findCenter(clicked.getLocation());
        if (lightningCenter != null && LightningManager.isActive(lightningCenter)) {
            player.sendMessage(MessageUtil.parse("<dark_gray>[<yellow>⚡ Молнии<dark_gray>] <gray>Активна <dark_gray>| <white>"
                    + lightningCenter.getBlockX() + " " + lightningCenter.getBlockY() + " " + lightningCenter.getBlockZ()));
            return;
        }

    }

    // =========================
    // BLOCK BREAK — MAGNET (dynamic recompute)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {

        Block block = e.getBlock();
        Location loc = LocationUtil.normalize(block.getLocation());
        Player player = e.getPlayer();

        // =========================
        // 🧲 MAGNET: LODESTONE in an active cluster → recompute
        // =========================
        if (block.getType() == Material.LODESTONE && MagnetManager.isActive(loc)) {
            MagnetManager.onBlockBroken(loc, player);
            return;
        }

        // =========================
        // ⚡ LIGHTNING: any block of an active structure → disassemble
        // =========================
        Location lightningCenter = LightningManager.getCenterForBlock(loc);
        if (lightningCenter != null) {
            LightningManager.disassemble(lightningCenter);
            if (player != null) {
                player.sendMessage(MessageUtil.parse("<yellow>⚡ Структура молний разрушена и деактивирована!"
                        + " <dark_gray>[<gray>" + lightningCenter.getBlockX() + " " + lightningCenter.getBlockY() + " " + lightningCenter.getBlockZ() + "<dark_gray>]"));
            }
            return;
        }

        // =========================
        // ⚛ REACTOR: check reactor blocks
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
            player.sendMessage(MessageUtil.parse("<red>❕ Реактор разрушен и деактивирован!"
                    + " <dark_gray>[<gray>" + reactorLoc.getBlockX() + " " + reactorLoc.getBlockY() + " " + reactorLoc.getBlockZ() + "<dark_gray>]"));
        }
    }

    // =========================
    // BLOCK PLACE — MAGNET (dynamic expansion)
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
        // Player must use SHIFT+RMB on the item frame to open the assembly menu.
    }



    // =========================
    // AUTO-DETECT STRUCTURE TYPE & ASSEMBLE
    // Scans a 5-block radius from the frame and compares against NBT templates.
    // =========================
    private void autoDetectAndAssemble(Player player, ItemFrame frame) {

        Location frameLoc = LocationUtil.normalize(frame.getLocation());
        if (frameLoc == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Ошибка определения позиции рамки!"));
            return;
        }

        // =========================
        // 1. SCANNING BY NBT TEMPLATES
        // Load the templates on the first call (once)
        // =========================
        if (!templatesLoaded) {
            StructureTemplate.initAll();
            templatesLoaded = true;
        }

        // Check: were there template loading errors
        StructureTemplate lightningTmpl = StructureTemplate.get("lightning");
        StructureTemplate reactorTmpl = StructureTemplate.get("reactor");

        String lightningErr = StructureTemplate.getTemplateError("lightning");
        String reactorErr = StructureTemplate.getTemplateError("reactor");

        if (lightningErr != null || reactorErr != null) {
            player.sendMessage(MessageUtil.parse("<dark_red>⚠ <red>Ошибка загрузки NBT-шаблонов структур:"));
            if (lightningErr != null)
                player.sendMessage(MessageUtil.parse("  <dark_gray>• <yellow>Молнии<dark_gray>: <red>" + lightningErr));
            if (reactorErr != null)
                player.sendMessage(MessageUtil.parse("  <dark_gray>• <red>Реактор<dark_gray>: <red>" + reactorErr));
            player.sendMessage(MessageUtil.parse("<gray>Проверьте консоль сервера для деталей."));
        }

        // =========================
        // 1a. Lightning template
        // =========================
        if (lightningTmpl != null) {
            Location center = lightningTmpl.findMatch(frameLoc, 5);
            if (center != null) {
                if (LightningManager.isActive(center)) {
                    player.sendMessage(MessageUtil.parse("<yellow>⚡ Структура молний уже собрана!"));
                    player.sendMessage(MessageUtil.parse("<gray>Команды: <white>/ui str lightning enable<gray>/<red>disable"));
                    return;
                }
                player.sendMessage(MessageUtil.parse("<dark_gray>[<yellow>⚡ Молнии<dark_gray>] <gray>Обнаружена структура молний — сборка..."));
                LightningManager.assemble(center, frame, player);
                return;
            }
        }

        // =========================
        // 1b. Reactor template
        // =========================
        if (reactorTmpl != null) {
            Location center = reactorTmpl.findMatch(frameLoc, 5);
            if (center != null) {
                ReactorManager reactor = ReactorManager.getInstance();
                if (reactor != null) {
                    Location existing = reactor.getReactorLocation();
                    if (existing != null && existing.equals(center)) {
                        player.sendMessage(MessageUtil.parse("<yellow>Реактор уже активен на этом месте!"));
                        return;
                    }
                }
                ReactorManager.setPendingAssembly(player, center, frame, "dark_synthesis");
                player.sendMessage(MessageUtil.parse("<dark_gray>[<red>Реактор<dark_gray>] <gray>Обнаружен реактор — сборка..."));
                player.performCommand("reactor assemble dark_synthesis");
                return;
            }
        }

        // =========================
        // 2. CHECK: MAGNET (LODESTONE — not NBT, but multi-block)
        // =========================
        Location attachedLoc = LocationUtil.normalize(
                frame.getLocation().getBlock().getRelative(
                        frame.getFacing().getOppositeFace()
                ).getLocation()
        );
        if (attachedLoc != null && attachedLoc.getBlock().getType() == Material.LODESTONE) {
            if (MagnetManager.isActive(attachedLoc)) {
                player.sendMessage(MessageUtil.parse("<yellow>Магнит уже активен на этом месте!"));
                return;
            }
            ReactorManager.setPendingAssembly(player, attachedLoc, frame, "magnet");
            player.sendMessage(MessageUtil.parse("<dark_gray>[<aqua>Магнит<dark_gray>] <gray>Обнаружен магнит — сборка..."));
            player.performCommand("reactor assemble magnet");
            return;
        }

        // =========================
        // 3. CHECK: BATTERY (WAXED_COPPER_GRATE + frame)
        // =========================
        Location attachedLoc2 = LocationUtil.normalize(
                frame.getLocation().getBlock().getRelative(
                        frame.getFacing().getOppositeFace()
                ).getLocation()
        );
        if (attachedLoc2 != null && attachedLoc2.getBlock().getType() == Materials.WAXED_COPPER_GRATE) {
            if (BatteryManager.isActive(attachedLoc2)) {
                player.sendMessage(MessageUtil.parse("<yellow>Батарея уже собрана на этом месте!"));
                return;
            }
            BatteryManager.assemble(attachedLoc2, player);
            return;
        }

        // =========================
        // 4. CHECK: LAMP (REDSTONE_LAMP + frame)
        // =========================
        if (attachedLoc2 != null && attachedLoc2.getBlock().getType() == Material.REDSTONE_LAMP) {
            if (LightManager.isActive(attachedLoc2)) {
                player.sendMessage(MessageUtil.parse("<yellow>Лампочка уже собрана на этом месте!"));
                return;
            }
            LightManager.assemble(attachedLoc2, player);
            return;
        }

        // =========================
        // 5. CHECK: GENERATOR (BLAST_FURNACE + frame on top)
        // =========================
        // The block under the frame
        Location generatorLoc = LocationUtil.normalize(
                frame.getLocation().clone().add(0, -1, 0)
        );
        if (generatorLoc != null
                && generatorLoc.getBlock().getType() == Materials.BLAST_FURNACE
                && GeneratorStructure.isValid(generatorLoc)) {
            // Check for a cable nearby
            if (GeneratorManager.hasNearbyCable(generatorLoc)) {
                if (GeneratorManager.isAssembled(generatorLoc)) {
                    player.sendMessage(MessageUtil.parse("<yellow>Генератор уже собран на этом месте!"));
                    return;
                }
                GeneratorManager.assembleFromFrame(player, generatorLoc);
                return;
            } else {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Нет кабеля рядом с плавильной печью!"));
                return;
            }
        }

        // =========================
        // 4. NOTHING FOUND
        // =========================
        player.sendMessage(MessageUtil.parse("<red>❌ Структура не распознана!"));
        player.sendMessage(MessageUtil.parse("<gray>Убедитесь, что все блоки структуры соответствуют NBT-шаблону."));
        player.sendMessage(MessageUtil.parse("<gray>Поддерживаемые структуры: громоотвод (молнии), LODESTONE (магнит),"));
        player.sendMessage(MessageUtil.parse("<gray>реактор (алмазная/золотая бочка с рамкой), BLAST_FURNACE + рамка (генератор),"));
        player.sendMessage(MessageUtil.parse("<gray>WAXED_COPPER_GRATE (батарея), REDSTONE_LAMP (лампочка)"));
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
        player.performCommand("ui str dfc stats");
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
