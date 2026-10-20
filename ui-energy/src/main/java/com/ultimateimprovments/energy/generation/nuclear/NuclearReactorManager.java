package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.StructureTemplate;
import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry, assembly and persistence of the compact nuclear reactors.
 * <p>
 * A reactor is a 3×3×3 NBT structure ({@code nuclearreactor.nbt}) assembled
 * with an item frame, like every other multi-block structure. One reactor
 * instance exists per anchor (the top-center fuel barrel), so several reactors
 * can coexist in the same world; the anchor is stored in the structure marker
 * registry (SQLite) and restored on startup.
 * <p>
 * This class owns no energy logic — {@link NuclearReactorTask} does the tick.
 */
public final class NuclearReactorManager {

    /** NBT template name (file name without extension). */
    public static final String TEMPLATE_NAME = "nuclearreactor";

    /** Structure marker type written to the SQLite registry. */
    public static final String MARKER_TYPE = "nuclearreactor";

    private static final Map<Location, NuclearReactor> reactors = new ConcurrentHashMap<>();

    private NuclearReactorManager() {}

    // =========================
    // LIFECYCLE
    // =========================

    /** Rebuilds the in-memory reactor list from the structure marker registry. */
    public static void init() {
        reactors.clear();

        for (Map.Entry<String, StructureMarker.StructureData> entry
                : StructureMarker.getAllEntries()) {
            StructureMarker.StructureData data = entry.getValue();
            if (data == null || !MARKER_TYPE.equals(data.type())) continue;

            String fk = entry.getKey();
            World world;
            try {
                world = Bukkit.getWorld(UUID.fromString(StructureMarker.parseWorldUid(fk)));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (world == null) continue;

            Location anchor;
            try {
                anchor = LocationUtil.normalize(new Location(
                        world,
                        StructureMarker.parseX(fk),
                        StructureMarker.parseY(fk),
                        StructureMarker.parseZ(fk)));
            } catch (RuntimeException e) {
                continue;
            }
            if (anchor == null) continue;

            reactors.put(anchor, new NuclearReactor(anchor));
        }

        ConsoleLogger.info("[NuclearReactor] Restored " + reactors.size() + " reactor(s)");
    }

    public static void shutdown() {
        reactors.clear();
    }

    public static Collection<NuclearReactor> getReactors() {
        return reactors.values();
    }

    public static NuclearReactor getAt(Location anchor) {
        Location norm = LocationUtil.normalize(anchor);
        return norm == null ? null : reactors.get(norm);
    }

    /**
     * Removes the reactor and its persisted marker — used when the anchor
     * (the top fuel barrel) is broken, so a dismantled reactor does not leave
     * a ghost marker behind forever.
     */
    public static void disassemble(Location anchor) {
        Location norm = LocationUtil.normalize(anchor);
        if (norm == null) return;

        NuclearReactor removed = reactors.remove(norm);
        StructureMarker.removeAt(norm);
        if (removed != null) {
            ConsoleLogger.info("[NuclearReactor] Disassembled at " + coords(norm));
        }
    }

    // =========================
    // LOOKUP BY STRUCTURE BLOCK / FRAME
    // =========================

    /**
     * Finds the reactor whose template occupies {@code loc}. Every cell of the
     * 3×3×3 cube is a template block, so the bounds check is exact.
     */
    public static NuclearReactor getByBlockLocation(Location loc) {
        Location norm = LocationUtil.normalize(loc);
        if (norm == null) return null;

        for (NuclearReactor reactor : reactors.values()) {
            Location anchor = reactor.getAnchor();
            if (anchor == null || anchor.getWorld() == null
                    || !anchor.getWorld().equals(norm.getWorld())) {
                continue;
            }
            int dx = norm.getBlockX() - anchor.getBlockX();
            int dy = norm.getBlockY() - anchor.getBlockY();
            int dz = norm.getBlockZ() - anchor.getBlockZ();
            if (dx >= -1 && dx <= 1 && dy >= -2 && dy <= 0 && dz >= -1 && dz <= 1) {
                return reactor;
            }
        }
        return null;
    }

    /**
     * Breaks the trigger item frame and drops it (plus any displayed item).
     * The frame is a one-shot assembly trigger, exactly like on the other
     * structures — it must not stay behind and block the barrel.
     */
    public static void dropTriggerFrame(ItemFrame frame) {
        if (frame == null || frame.isDead() || !frame.isValid()) return;

        Location loc = frame.getLocation();
        World world = frame.getWorld();
        if (world == null) return;

        ItemStack displayed = frame.getItem();
        if (displayed != null && !displayed.getType().isAir()) {
            world.dropItemNaturally(loc, displayed);
        }
        Material frameItem = frame instanceof org.bukkit.entity.GlowItemFrame
                ? Material.GLOW_ITEM_FRAME
                : Material.ITEM_FRAME;
        world.dropItemNaturally(loc, new ItemStack(frameItem));
        frame.remove();
    }

    /**
     * First cable node in the six neighbours of the bottom energy port.
     * The port itself is the waxed chiseled copper at {@code (0,−2,0)}; its
     * bottom face is the only exposed one, so the cable sits directly below
     * the structure.
     */
    public static CableNode findPortNode(Location port) {
        if (port == null) return null;

        for (Location near : LocationUtil.getNeighbors(port)) {
            Location norm = LocationUtil.normalize(near);
            if (norm == null) continue;

            CableNode node = CableNetwork.getNode(norm);
            if (node != null && LocationUtil.isFullyConnected(port, norm)) {
                return node;
            }
        }
        return null;
    }

    // =========================
    // ASSEMBLY
    // =========================

    /**
     * Registers a reactor at {@code center} (the template origin). The caller
     * has already matched the NBT template; this method re-checks it and
     * refuses duplicates at the same anchor.
     *
     * @return the reactor (existing or newly created), or {@code null} when the
     *         structure does not match or the template is unavailable
     */
    public static NuclearReactor assemble(Location center, Player player) {
        Location anchor = LocationUtil.normalize(center);
        if (anchor == null || anchor.getWorld() == null) return null;

        StructureTemplate template = StructureTemplate.get(TEMPLATE_NAME);
        if (template == null) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse(msg("no_template",
                        "<dark_red>❌ <red>The nuclear reactor template is not loaded — check the console!")));
            }
            return null;
        }

        if (!template.checkAt(anchor).matched()) {
            return null;
        }

        NuclearReactor existing = reactors.get(anchor);
        if (existing != null) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse(msg("already_active",
                        "<yellow>A nuclear reactor is already active at this place!")));
            }
            return existing;
        }

        NuclearReactor reactor = new NuclearReactor(anchor);
        reactors.put(anchor, reactor);
        StructureMarker.place(anchor, MARKER_TYPE, UUID.randomUUID());

        ConsoleLogger.info("[NuclearReactor] Assembled at " + coords(anchor));
        if (player != null) {
            player.sendMessage(MessageUtil.parse(msg("assembled",
                            "<green>✔ <white>Nuclear reactor assembled! <dark_gray>(%coords%)")
                    .replace("%coords%", coords(anchor))));
        }
        return reactor;
    }

    // =========================
    // MULTIMETER READOUT (SHIFT+RMB on any reactor block)
    // =========================

    /**
     * Full CER data pack. Requires a multimeter in either hand; without it the
     * player gets the localized "multimeter required" hint, exactly like the
     * coal generator and the DFC control signs.
     */
    public static void sendReadout(Player player, NuclearReactor reactor) {
        if (player == null || reactor == null) return;

        if (!holdsMultimeter(player)) {
            player.sendMessage(MessageUtil.parse(prefix() + msg("multimeter_required",
                    "<red>A multimeter is required to read the data!")));
            return;
        }

        String p = prefix();
        Location anchor = reactor.getAnchor();

        player.sendMessage(MessageUtil.parse(p + msg("meter_header",
                        "<gold>=== %prefix% ===</gold>")
                .replace("%prefix%", prefixWord())));

        player.sendMessage(MessageUtil.parse(p + msg("meter_position",
                        "<aqua>Position: </aqua><white>%coords%</white>")
                .replace("%coords%", coords(anchor))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_status",
                        "<aqua>Status: </aqua>%status%")
                .replace("%status%", statusLabel(reactor.getStatus()))));

        Block center = reactor.getCenterBlock();
        Block barrel = reactor.getBarrelBlock();
        boolean powered = center.isBlockPowered()
                || center.isBlockIndirectlyPowered()
                || barrel.isBlockPowered()
                || barrel.isBlockIndirectlyPowered();
        player.sendMessage(MessageUtil.parse(p + msg("meter_power",
                        "<aqua>Redstone: </aqua>%power%")
                .replace("%power%", powered
                        ? msg("power_on", "<green>✔ On</green>")
                        : msg("power_off", "<red>✘ Off</red>"))));

        Material fuel = fuelMaterial();
        player.sendMessage(MessageUtil.parse(p + msg("meter_fuel",
                        "<aqua>Fuel: </aqua><white>%fuel%</white> <gray>%item%</gray>")
                .replace("%fuel%", String.valueOf(reactor.countFuel(fuel)))
                .replace("%item%", materialName(fuel))));

        int burnSeconds = (int) Math.ceil(reactor.getFuelTicksLeft() / 20.0);
        player.sendMessage(MessageUtil.parse(p + msg("meter_burn",
                        "<aqua>Current fuel: </aqua><white>%sec%s left</white>")
                .replace("%sec%", String.valueOf(burnSeconds))));

        boolean hasCable = findPortNode(reactor.getEnergyPortLocation()) != null;
        player.sendMessage(MessageUtil.parse(p + msg("meter_cable",
                        "<aqua>Cable: </aqua>%state%")
                .replace("%state%", hasCable
                        ? msg("cable_connected", "<green>✔ Connected</green>")
                        : msg("cable_missing", "<red>✘ Not connected</red>"))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_output",
                        "<aqua>Output: </aqua><white>%energy% ⚡/s</white>")
                .replace("%energy%", String.valueOf(energyPerSecond()))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_structure",
                        "<aqua>Structure: </aqua>%state%")
                .replace("%state%", reactor.getStatus() == NuclearReactor.Status.DAMAGED
                        ? msg("structure_damaged", "<dark_red>✖ Damaged</dark_red>")
                        : msg("structure_ok", "<green>✔ Intact</green>"))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_hint",
                "<dark_gray>Emeralds go into the barrel, a lever on top of the barrel, cable under the bottom port</dark_gray>")));
    }

    /** {@code [CER]} chat prefix — the word is configurable. */
    private static String prefix() {
        return "<dark_gray>[<yellow>" + prefixWord() + "<dark_gray>] ";
    }

    private static String prefixWord() {
        return msg("prefix", "CER");
    }

    private static boolean holdsMultimeter(Player player) {
        return isMultimeter(player.getInventory().getItemInMainHand())
                || isMultimeter(player.getInventory().getItemInOffHand());
    }

    /** PDC tag check for the custom multimeter item (base: clock). */
    private static boolean isMultimeter(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte value = meta.getPersistentDataContainer().get(Keys.MULTIMETER, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private static String statusLabel(NuclearReactor.Status status) {
        if (status == null) return "?";
        return switch (status) {
            case RUNNING -> msg("status_running", "<green>✔ Running");
            case UNPOWERED -> msg("status_unpowered", "<red>❌ No redstone signal on the center block");
            case NO_FUEL -> msg("status_no_fuel", "<yellow>⚠ No emeralds in the barrel");
            case NO_CABLE -> msg("status_no_cable", "<yellow>⚠ No cable under the bottom port");
            case OUTPUT_BLOCKED -> msg("status_output_blocked", "<yellow>⚠ The network cannot accept energy");
            case DAMAGED -> msg("status_damaged", "<dark_red>✖ Structure damaged");
        };
    }

    private static String materialName(Material material) {
        if (material == null) return "?";
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // =========================
    // CONFIG HELPERS
    // =========================

    /** Configured fuel item ({@code nuclear_reactor.fuel_item}, EMERALD fallback). */
    public static Material fuelMaterial() {
        Main main = Main.getInstance();
        if (main == null) return Material.EMERALD;

        String raw = main.getConfig().getString("nuclear_reactor.fuel_item", "EMERALD");
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            return Material.EMERALD;
        }
        return material;
    }

    /** Configured output rate in energy per second. */
    public static int energyPerSecond() {
        Main main = Main.getInstance();
        if (main == null) return 1000;
        return Math.max(0, main.getConfig().getInt("nuclear_reactor.energy_per_second", 1000));
    }

    // =========================
    // MESSAGE + COORDS HELPERS
    // =========================

    private static String msg(String key, String def) {
        return StructuresMessages.get("nuclear_reactor." + key, def);
    }

    private static String coords(Location loc) {
        if (loc == null) return "? ? ?";
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }
}
