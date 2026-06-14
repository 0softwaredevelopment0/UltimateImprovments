package com.mcplugin.core1;

import com.mcplugin.features.magnet.MagnetManager;
import com.mcplugin.features.magnet.MagnetStructure;
import com.mcplugin.util.LocationUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

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

public class ReactorListener implements Listener {

    // =========================
    // REACTOR BLOCKS (for monitoring)
    // =========================
    private static final Material[] KEY_BLOCKS = {
            Material.WAXED_COPPER_BULB,
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
    // ITEM FRAME INTERACT → CHAT MENU
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent e) {

        Entity clicked = e.getRightClicked();

        if (!(clicked instanceof ItemFrame frame)) {
            return;
        }

        Player player = e.getPlayer();

        // =========================
        // SHIFT+ПКМ — открыть меню сборки
        // =========================
        if (player.isSneaking()) {
            e.setCancelled(true);
            openAssemblyMenu(player, frame);
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

        // Подсказка
        player.sendMessage("§7Нажмите §fSHIFT+ПКМ§7 по рамке, чтобы открыть меню сборки");
    }

    // =========================
    // BLOCK BREAK — МАГНИТ (динамический пересчёт)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {

        Block block = e.getBlock();
        Location loc = LocationUtil.normalize(block.getLocation());

        // =========================
        // 🧲 МАГНИТ: LODESTONE в активном кластере → пересчёт
        // =========================
        if (block.getType() == Material.LODESTONE && MagnetManager.isActive(loc)) {
            MagnetManager.onBlockBroken(loc, e.getPlayer());
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
    // OPEN ASSEMBLY MENU
    // =========================
    private void openAssemblyMenu(Player player, ItemFrame frame) {

        // =========================
        // Блок, к которому прикреплена рамка (противоположно её лицу)
        // =========================
        Block attachedBlock = frame.getLocation().getBlock().getRelative(
                frame.getFacing().getOppositeFace()
        );
        Location attachedLoc = LocationUtil.normalize(attachedBlock.getLocation());

        // =========================
        // ПОИСК ЦЕНТРА РЕАКТОРА ПО АЛМАЗНОМУ БЛОКУ (без валидации)
        // =========================
        Location reactorCenter = ReactorStructure.locateCenter(frame.getLocation());

        // =========================
        // МЕНЮ СБОРКИ (валидация только при клике на кнопку)
        // =========================
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6═══════════════════════════════"));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("    §6⛏ Выберите механизм для сборки:"));
        player.sendMessage(Component.text(""));

        // =========================
        // РЕАКТОР (только если найден алмазный блок)
        // =========================
        if (reactorCenter != null) {
            ReactorManager.setPendingAssembly(player, reactorCenter, frame, "dark_synthesis");

            Component reactorOpt = Component.text("  ")
                    .append(Component.text("§8[§6Реактор тёмного синтеза§8]")
                            .clickEvent(ClickEvent.runCommand("/reactor assemble dark_synthesis"))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("§eРеактор тёмного синтеза\n§7Превращает алмаз и золото\n§7в древние обломки под давлением")
                            ))
                    )
                    .append(Component.text(" §7- Реактор"));

            player.sendMessage(reactorOpt);
            player.sendMessage(Component.text(""));
        }

        // =========================
        // МАГНИТ
        // =========================
        ReactorManager.setPendingAssembly(player, attachedLoc, frame, "magnet");

        Component magnetOpt = Component.text("  ")
                .append(Component.text("§8[§bМагнит§8]")
                        .clickEvent(ClickEvent.runCommand("/reactor assemble magnet"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("§bМагнит\n§7Плавно притягивает металлические предметы,\n§7игроков с металлом и мобов в металле\n§7Радиус: 9 блоков")
                        ))
                )
                .append(Component.text(" §7- Магнит"));

        player.sendMessage(magnetOpt);
        player.sendMessage(Component.text(""));

        player.sendMessage(Component.text("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(Component.text(""));
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

        // Structure is 7×7×7 from Y=-6 to Y=0 relative to frame
        return dx <= 3 && dy <= 6 && dz <= 3;
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
