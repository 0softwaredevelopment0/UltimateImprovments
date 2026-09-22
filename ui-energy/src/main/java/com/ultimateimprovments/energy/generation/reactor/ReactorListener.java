package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovments.mechanics.environment.lightning.LightningStructure;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetStructure;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.StructureTemplate;
import com.ultimateimprovments.util.StructuresMessages;
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
            Materials.WAXED_COPPER_BLOCK,
            Materials.WAXED_CUT_COPPER,
            Materials.WAXED_CHISELED_COPPER,
            Materials.WAXED_COPPER_GRATE,
            Materials.WAXED_COPPER_BARS,
            Material.BARREL,
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
            player.sendMessage(MessageUtil.parse(msg("reactor_stats_click",
                    "<dark_gray>[<red>Р.Т.С<dark_gray>] <gray>ID: <white>%id% <dark_gray>| <white>T=%temp% <dark_gray>| <white>P=%press% mPa <dark_gray>| <white>S=%spin% RPS <dark_gray>| <white>I=%shield%%")
                    .replace("%id%", String.valueOf(reactor.getReactorId()))
                    .replace("%temp%", String.valueOf(reactor.getCoreTemp()))
                    .replace("%press%", String.format("%.3f", reactor.getShieldPress()))
                    .replace("%spin%", String.format("%.2f", reactor.getCoreSpin()))
                    .replace("%shield%", reactor.getCoreShInt() + "%")));
            return;
        }

        // Check: an active magnet?
        if (MagnetStructure.isActive(clicked.getLocation())) {
            player.sendMessage(MessageUtil.parse(msg("magnet_already_active",
                    "<yellow>Магнит уже активен на этом месте!")));
            return;
        }

        // Check: an active lightning structure?
        Location lightningCenter = LightningStructure.findCenter(clicked.getLocation());
        if (lightningCenter != null && LightningManager.isActive(lightningCenter)) {
            boolean enabled = LightningManager.isEnabled(lightningCenter);
            String status = MessageUtil.legacy(enabled
                    ? msg("lightning_status_on", "<green>✔ On")
                    : msg("lightning_status_off", "<red>❌ Off"));
            player.sendMessage(MessageUtil.parse(msg("lightning_status_click",
                    "<dark_gray>[<yellow>⚡ Молнии<dark_gray>] <gray>Активна <dark_gray>| <white>%coords% <dark_gray>[%status%<dark_gray>]")
                    .replace("%coords%", coords(lightningCenter))
                    .replace("%status%", status)));
            player.sendMessage(MessageUtil.parse(msg("lightning_status_hint",
                    "<dark_gray>┃ <gray>SHIFT+ПКМ по рамке — включить/выключить")));
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
                player.sendMessage(MessageUtil.parse(msg("lightning_broken",
                        "<yellow>⚡ Структура молний разрушена и деактивирована! <dark_gray>[<gray>%coords%<dark_gray>]")
                        .replace("%coords%", coords(lightningCenter))));
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
            player.sendMessage(MessageUtil.parse(msg("reactor_broken",
                    "<red>❕ Реактор разрушен и деактивирован! <dark_gray>[<gray>%coords%<dark_gray>]")
                    .replace("%coords%", coords(reactorLoc))));
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
            player.sendMessage(MessageUtil.parse(msg("frame_error",
                    "<dark_red>❌ <red>Не удалось определить позицию рамки!")));
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
        StructureTemplate reactorTmpl = StructureTemplate.get("darkfusionreactor");

        String lightningErr = StructureTemplate.getTemplateError("lightning");
        String reactorErr = StructureTemplate.getTemplateError("darkfusionreactor");

        if (lightningErr != null || reactorErr != null) {
            player.sendMessage(MessageUtil.parse(msg("template_errors_header",
                    "<dark_red>⚠ <red>Ошибка загрузки NBT-шаблонов структур:")));
            if (lightningErr != null)
                player.sendMessage(MessageUtil.parse(msg("template_error_line",
                                "  <dark_gray>• <white>%structure%<dark_gray>: <red>%error%")
                        .replace("%structure%", StructuresMessages.structureName("lightning"))
                        .replace("%error%", lightningErr)));
            if (reactorErr != null)
                player.sendMessage(MessageUtil.parse(msg("template_error_line",
                                "  <dark_gray>• <white>%structure%<dark_gray>: <red>%error%")
                        .replace("%structure%", StructuresMessages.structureName("darkfusionreactor"))
                        .replace("%error%", reactorErr)));
            player.sendMessage(MessageUtil.parse(msg("template_errors_hint",
                    "<gray>Проверьте консоль сервера для деталей.")));
        }

        // =========================
        // 1a. Lightning template
        // =========================
        if (lightningTmpl != null) {
            Location center = lightningTmpl.findMatch(frameLoc, 5);
            if (center == null) {
                // Retry close to the frame: the anchor block of the template may sit
                // slightly outside the strict scan grid of findMatch.
                var best = lightningTmpl.bestMatch(frameLoc, 5);
                if (best != null && best.matched()) {
                    center = best.center();
                }
            }
            if (center != null) {
                if (LightningManager.isActive(center)) {
                    boolean enabled = LightningManager.isEnabled(center);
                    LightningManager.setEnabled(center, !enabled);
                    player.sendMessage(MessageUtil.parse(msg(enabled
                            ? "lightning_toggled_off" : "lightning_toggled_on",
                            enabled ? "<red>❌ <white>Структура молний выключена!"
                                    : "<green>✔ <white>Структура молний включена!")));
                    return;
                }
                player.sendMessage(MessageUtil.parse(msg("lightning_detected",
                        "<dark_gray>[<yellow>⚡ Молнии<dark_gray>] <gray>Обнаружена структура молний — сборка...")));
                LightningManager.assemble(center, frame, player);
                return;
            }
        }

        // =========================
        // 1b. Reactor template
        // =========================
        if (reactorTmpl != null) {
            Location center = reactorTmpl.findMatch(frameLoc, 5);
            if (center == null) {
                var best = reactorTmpl.bestMatch(frameLoc, 5);
                if (best != null && best.matched()) {
                    center = best.center();
                }
            }
            if (center != null) {
                ReactorManager reactor = ReactorManager.getInstance();
                if (reactor != null) {
                    Location existing = reactor.getReactorLocation();
                    if (existing != null && existing.equals(center)) {
                        player.sendMessage(MessageUtil.parse(msg("reactor_already_active",
                                "<yellow>Реактор уже активен на этом месте!")));
                        return;
                    }
                }
                ReactorManager.setPendingAssembly(player, center, frame, "dark_synthesis");
                player.sendMessage(MessageUtil.parse(msg("reactor_detected",
                        "<dark_gray>[<red>Реактор<dark_gray>] <gray>Обнаружен реактор — сборка...")));
                ReactorCommand.assembleDarkSynthesis(player);
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
                player.sendMessage(MessageUtil.parse(msg("magnet_already_active",
                        "<yellow>Магнит уже активен на этом месте!")));
                return;
            }
            ReactorManager.setPendingAssembly(player, attachedLoc, frame, "magnet");
            player.sendMessage(MessageUtil.parse(msg("magnet_detected",
                    "<dark_gray>[<aqua>Магнит<dark_gray>] <gray>Обнаружен магнит — сборка...")));
            ReactorCommand.assembleMagnet(player);
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
                player.sendMessage(MessageUtil.parse(msg("battery_already_active",
                        "<yellow>Батарея уже собрана на этом месте!")));
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
                player.sendMessage(MessageUtil.parse(msg("lamp_already_active",
                        "<yellow>Лампочка уже собрана на этом месте!")));
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
                    player.sendMessage(MessageUtil.parse(msg("generator_already_active",
                            "<yellow>Генератор уже собран на этом месте!")));
                    return;
                }
                GeneratorManager.assembleFromFrame(player, generatorLoc);
                return;
            } else {
                player.sendMessage(MessageUtil.parse(msg("generator_no_cable",
                        "<dark_red>❌ <red>Нет кабеля рядом с плавильной печью!")));
                return;
            }
        }

        // =========================
        // 6. NOTHING RECOGNIZED — show the closest structure and how to fix it
        // =========================
        StructureTemplate.BestCandidate best = StructureTemplate.findBestCandidate(frameLoc, 5);

        player.sendMessage("");
        player.sendMessage(MessageUtil.parse(msg("not_recognized",
                "<red>❌ Error: Structure not recognized!")));

        if (best == null) {
            player.sendMessage(MessageUtil.parse(msg("not_loaded",
                    "<gray>NBT-шаблоны структур не загружены — проверьте консоль сервера.")));
            return;
        }

        player.sendMessage(MessageUtil.parse(msg("closest_match",
                        "<gray>Больше всего похоже на: <yellow>%name% <gray>— совпадение <yellow>%percent%%")
                .replace("%name%", best.template().getDisplayName())
                .replace("%percent%", String.valueOf(best.result().percent()))));

        if (best.result().fixes().isEmpty()) {
            player.sendMessage(MessageUtil.parse(msg("no_fixes_needed",
                    "<gray>Критичных отличий не найдено — проверьте положение рамки.")));
            return;
        }

        int limit = Math.min(best.result().fixes().size(), 15);
        player.sendMessage(MessageUtil.parse(msg("fixes_header",
                        "<gray>Чтобы собрать, нужно (%count% шт.):")
                .replace("%count%", String.valueOf(best.result().fixes().size()))));
        for (StructureTemplate.Fix fix : best.result().fixes().subList(0, limit)) {
            player.sendMessage(MessageUtil.parse("<dark_gray> • <gray>" + StructureTemplate.formatFix(fix, best.result().center())));
        }
        if (best.result().fixes().size() > limit) {
            player.sendMessage(MessageUtil.parse(msg("fixes_more",
                            "<dark_gray> • <gray>...и ещё %count% исправлений")
                    .replace("%count%", String.valueOf(best.result().fixes().size() - limit))));
        }
    }

    // =========================
    // LOCALIZED MESSAGE + COORDS HELPERS
    // =========================
    /** Localized {@code structures.*} message with a hardcoded fallback. */
    private static String msg(String key, String def) {
        return StructuresMessages.get(key, def);
    }

    /** "x y z" string of a location (for %coords% placeholders). */
    private static String coords(Location loc) {
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
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

        // DFC is 10×11×9: X −5..4, Y −9..0, Z −4..4 relative to the anchor
        // (the frame cell above the central top bulb)
        return dx <= 5 && dy <= 9 && dz <= 4;
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
        ReactorStatsDisplay.sendStats(player);
    }

    // =========================
    // GLASS PLACED INSIDE THE STRUCTURE → AUTO-REPAIR CHECK
    // The player repairs the case manually: any glass block placed within the
    // structure bounds while the case is broken is checked; when every glass
    // position is filled again, the case repairs itself.
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlassPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (!e.getBlockPlaced().getType().isSolid() || !e.getBlockPlaced().getType().name().endsWith("GLASS")) {
            return;
        }
        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null || !reactor.isValid() || !reactor.isCaseBroken()) return;

        Location reactorLoc = reactor.getReactorLocation();
        if (reactorLoc == null) return;
        Location placed = e.getBlockPlaced().getLocation();
        if (!placed.getWorld().equals(reactorLoc.getWorld())) return;
        if (!isWithinStructure(reactorLoc, placed)) return;

        // Let the case system decide whether the repair is complete
        reactor.getCase().checkAutoRepair(reactorLoc);
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
