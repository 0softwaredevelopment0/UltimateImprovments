package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.StructuresMessages;
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
            player.sendMessage(MessageUtil.parse(msg("pending_frame_reactor",
                    "<red>First press SHIFT+RMB on the reactor frame!")));
            return;
        }

        // =========================
        // VALIDATE STRUCTURE — full NBT template check (with detailed fixes)
        // =========================
        java.util.List<String> errors = validateReactorByTemplate(pending.center());
        if (!errors.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(MessageUtil.parse(msg("reactor_invalid_header",
                    "<dark_red>❌ <red>Reactor structure is invalid! What to fix:")));
            int shown = 0;
            for (String err : errors) {
                if (shown++ >= 15) {
                    player.sendMessage(MessageUtil.parse(msg("fixes_more",
                                    "<dark_gray> • <gray>...and %count% more fixes")
                            .replace("%count%", String.valueOf(errors.size() - shown + 1))));
                    break;
                }
                player.sendMessage(MessageUtil.parse("<dark_gray> • <gray>" + err));
            }
            player.sendMessage(MessageUtil.parse(msg("reactor_separator",
                    "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")));
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // CHECK IF ALREADY ACTIVE
        // =========================
        Location existing = reactor.getReactorLocation();
        if (existing != null) {
            if (existing.equals(pending.center())) {
                player.sendMessage(MessageUtil.parse(msg("reactor_already_active",
                        "<yellow>The reactor is already active at this place!")));
                ReactorManager.clearPendingAssembly(player);
                return;
            }
            player.sendMessage(MessageUtil.parse(msg("reactor_already_active",
                    "<yellow>The reactor is already active at this place!")));
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
        // NAME THE FUEL BARRELS (west = gold, east = diamond)
        // =========================
        nameBarrel(pending.center(), -4, -5, 0, "<yellow>Golden fuel");
        nameBarrel(pending.center(), 4, -5, 0, "<dark_aqua>Diamond fuel");

        player.sendMessage(MessageUtil.parse(msg("reactor_assembled",
                "<green>✔ <white>Dark Fusion Reactor assembled! <dark_gray>(ID: %id%)")
                .replace("%id%", String.valueOf(reactor.getReactorId()))));
        player.sendMessage(MessageUtil.parse(msg("reactor_info_line",
                "<dark_gray>┃ <gray>Core temperature: <white>%temp% C*")
                .replace("%temp%", String.valueOf(reactor.getCoreTemp()))));
        player.sendMessage(MessageUtil.parse(msg("reactor_info_pressure",
                "<dark_gray>┃ <gray>Shield pressure: <white>%press% MPa")
                .replace("%press%", String.format("%.3f", reactor.getShieldPress()))));
        player.sendMessage(MessageUtil.parse(msg("reactor_info_spin",
                "<dark_gray>┃ <gray>Core spin: <white>%spin% RPS")
                .replace("%spin%", String.format("%.2f", reactor.getCoreSpin()))));
        player.sendMessage(MessageUtil.parse(msg("reactor_info_shield",
                "<dark_gray>┃ <gray>Shell integrity: <white>%shield%%")
                .replace("%shield%", reactor.getCoreShInt() + "%")));
        player.sendMessage(MessageUtil.parse(msg("reactor_info_fuel",
                "<dark_gray>┃ <gray>Fuel: <yellow>gold ingots <gray>→ west barrel, <dark_aqua>diamonds <gray>→ east barrel")));

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
                com.ultimateimprovments.util.StructureTemplate.get("darkfusionreactor");

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

    /** Localized {@code structures.*} message with a hardcoded fallback. */
    private static String msg(String key, String def) {
        return StructuresMessages.get(key, def);
    }

    // =========================
    // 🏆 POWER TIER NAME (shared static)
    // =========================
    public static String getMagnetPowerTierStatic(int power) {
        if (power >= 10000000) return "<obfuscated>✧ <dark_red>✧✧ ABSOLUTE INFINITY ✧✧ <obfuscated>✧ <dark_gray>(" + power + ")";
        if (power >= 5000000) return "<dark_red>✧✧ INFINITE ABYSS ✧✧ <dark_gray>(" + power + ")";
        if (power >= 2500000) return "<red>✦ COSMIC CATASTROPHE ✦ <dark_gray>(" + power + ")";
        if (power >= 1000000) return "<light_purple>✧ PRIMORDIAL SINGULARITY ✧ <dark_gray>(" + power + ")";
        if (power >= 500000) return "<gold>☠ UNFATHOMABLE ☠ <dark_gray>(" + power + ")";
        if (power >= 250000) return "<dark_aqua>✦ GODLIKE ✦ <dark_gray>(" + power + ")";
        if (power >= 100000) return "<dark_red>✧✧✧ ALL-DEVOURING SINGULARITY ✧✧✧ <dark_gray>(" + power + ")";
        if (power >= 50000) return "<red>☠ ABSOLUTE SINGULARITY ☠ <dark_gray>(" + power + ")";
        if (power >= 25000) return "<gold>⚡ DIVINE SINGULARITY ⚡ <dark_gray>(" + power + ")";
        if (power >= 10000) return "<light_purple>✧✧ UNSURPASSED ✧✧ <dark_gray>(" + power + ")";
        if (power >= 5000) return "<dark_purple>✦ TRANSCENDENT ✦ <dark_gray>(" + power + ")";
        if (power >= 2500) return "<blue>⚜ SINGULAR ⚜ <dark_gray>(" + power + ")";
        if (power >= 1000) return "<dark_aqua>✦ INFINITE ✦ <dark_gray>(" + power + ")";
        if (power >= 500) return "<dark_purple>✧✧ ABSOLUTE ✧✧ <dark_gray>(" + power + ")";
        if (power >= 300) return "<dark_purple>☯ COSMIC ☯ <dark_gray>(" + power + ")";
        if (power >= 200) return "<light_purple>✦ TITANIC ✦ <dark_gray>(" + power + ")";
        if (power >= 150) return "<light_purple>◈ LEGENDARY ◈ <dark_gray>(" + power + ")";
        if (power >= 100) return "<red>☆ INCREDIBLE ☆ <dark_gray>(" + power + ")";
        if (power >= 75) return "<red>♦ EXTRAORDINARY ♦ <dark_gray>(" + power + ")";
        if (power >= 50) return "<gold>★ EXCEPTIONAL ★ <dark_gray>(" + power + ")";
        if (power >= 30) return "<gold>⬆ VERY STRONG ⬆ <dark_gray>(" + power + ")";
        if (power >= 20) return "<yellow>⬆ STRONG ⬆ <dark_gray>(" + power + ")";
        if (power >= 12) return "<yellow>⬆ ABOVE AVERAGE ⬆ <dark_gray>(" + power + ")";
        if (power >= 7) return "<green>➤ AVERAGE ➤ <dark_gray>(" + power + ")";
        if (power >= 4) return "<gray>➤ BELOW AVERAGE ➤ <dark_gray>(" + power + ")";
        if (power >= 2) return "<gray>▸ WEAK ▸ <dark_gray>(" + power + ")";
        return "<gray>▸ VERY WEAK ▸ <dark_gray>(" + power + ")";
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
            player.sendMessage(MessageUtil.parse(msg("pending_frame_magnet",
                    "<red>First press SHIFT+RMB on the magnet frame!")));
            return;
        }

        Location loc = pending.center();

        // =========================
        // VALIDATE — the block must be LODESTONE
        // =========================
        if (loc.getBlock().getType() != Material.LODESTONE) {
            player.sendMessage(MessageUtil.parse(msg("magnet_lodestone_missing",
                    "<red>The lodestone (LODESTONE) was not found!")));
            ReactorManager.clearPendingAssembly(player);
            return;
        }

        // =========================
        // CHECK IF ALREADY ACTIVE
        // =========================
        if (MagnetManager.isActive(loc)) {
            player.sendMessage(MessageUtil.parse("<yellow>A magnet is already active at this place!"));
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
