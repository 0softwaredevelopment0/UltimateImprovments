package com.ultimateimprovments.energy.generation.basic;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import org.bukkit.block.TileState;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.StructuresMessages;

import java.util.UUID;

import com.ultimateimprovments.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages generator blocks.
 * Generators are single-block (BLAST_FURNACE with PDC tag).
 * No more item frame required.
 */
public class GeneratorManager implements Listener {

    private static GeneratorManager instance;

    // Active generators: blast furnace location → enabled flag
    private static final Map<Location, Boolean> activeGenerators = new ConcurrentHashMap<>();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new GeneratorManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        scanExistingGenerators();
    }

    public static GeneratorManager getInstance() {
        return instance;
    }

    // =========================
    // SAVE PDC TO BLOCK STATE (called from BlockPlaceListener after placement)
    // =========================
    public static void savePdcToBlock(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;
        org.bukkit.block.BlockState state = loc.getBlock().getState();
        if (state instanceof org.bukkit.block.TileState tileState) {
            tileState.getPersistentDataContainer().set(Keys.GENERATOR, PersistentDataType.BYTE, (byte) 1);
            state.update();
        }
    }

    /** Creates a generator item (BLAST_FURNACE with PDC). */
    public static ItemStack createGeneratorItem() {
        ItemStack item = new ItemStack(Materials.BLAST_FURNACE);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parse(msg("item_name", "<!italic><gold>✦ Generator ✦</gold>")));
            meta.lore(List.of(
                    MessageUtil.parse(msg("item_lore_1", "<!italic><gray>Burns fuel to produce energy</gray>")),
                    MessageUtil.parse(msg("item_lore_2", "<!italic><gray>Place it and connect a cable</gray>"))
            ));
            meta.getPersistentDataContainer().set(Keys.GENERATOR, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Localized {@code structures.generator.*} message with a hardcoded EN fallback. */
    private static String msg(String key, String def) {
        return StructuresMessages.get("generator." + key, def);
    }

    // =========================
    // SCAN EXISTING — rebuild from the structure registry (SQLite)
    // =========================
    public static void scanExistingGenerators() {
        activeGenerators.clear();
        int count = 0;
        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"generator".equals(entry.getValue().type())) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            for (World world : Bukkit.getWorlds()) {
                if (!world.getUID().toString().equals(worldUid)) continue;
                Location furnaceLoc = LocationUtil.normalize(new Location(world, x, y, z));
                if (furnaceLoc == null) continue;
                if (furnaceLoc.getBlock().getType() != Materials.BLAST_FURNACE) continue;
                // Check PDC — make sure it is really a generator, not a regular furnace
                org.bukkit.block.BlockState state = furnaceLoc.getBlock().getState();
                if (!(state instanceof TileState tileState)) continue;
                if (!tileState.getPersistentDataContainer().has(Keys.GENERATOR, PersistentDataType.BYTE)) continue;
                activeGenerators.put(furnaceLoc, true);
                ensureGeneratorNode(furnaceLoc);
                count++;
                break;
            }
        }
        ConsoleLogger.info(
                "[Generator] Auto-detected " + count + " generators from the structure registry (SQLite)");
    }

    // =========================
    // STATE
    // =========================
    public static boolean isAssembled(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        return activeGenerators.getOrDefault(loc, false);
    }

    public static Collection<Location> getActiveGenerators() {
        return activeGenerators.keySet();
    }

    // =========================
    // MULTIMETER READOUT — SHIFT+RMB on an assembled generator's furnace.
    // The full data pack: position, redstone gate, fuel, burn, buffer,
    // output rate, state, connections. The readout opens ONLY on SHIFT+RMB —
    // every other click does nothing plugin-side (plain RMB keeps the
    // vanilla furnace GUI). Without a multimeter in either hand the readout
    // refuses with a localized hint. Prefix word + every line live in
    // messages[_en].structures.generator.* (UI-Other.toml).
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGeneratorMeterClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return; // once per click
        if (!e.getPlayer().isSneaking()) return;       // SHIFT+RMB only

        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Materials.BLAST_FURNACE) return;
        Location loc = LocationUtil.normalize(block.getLocation());
        if (loc == null || !isAssembled(loc)) return;  // plain furnaces untouched

        Player player = e.getPlayer();
        // The measurement owns the click: no furnace GUI on SHIFT+RMB
        e.setCancelled(true);

        if (!holdsMultimeter(player)) {
            player.sendMessage(MessageUtil.parse(prefix() + msg("multimeter_required",
                    "<red>A multimeter is required to read the data!")));
            return;
        }

        sendReadout(player, block, loc);
    }

    /** The [GEN] chat prefix — the word is configurable (structures.generator.prefix). */
    private static String prefix() {
        return "<dark_gray>[<yellow>" + msg("prefix", "GEN") + "<dark_gray>] ";
    }

    private static boolean holdsMultimeter(Player player) {
        return isMultimeter(player.getInventory().getItemInMainHand())
                || isMultimeter(player.getInventory().getItemInOffHand());
    }

    private static boolean isMultimeter(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte val = meta.getPersistentDataContainer().get(Keys.MULTIMETER, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    /** Full generator data pack (localized, GEN-prefixed). */
    private static void sendReadout(Player player, Block block, Location loc) {
        String prefixWord = msg("prefix", "GEN");

        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_header",
                "<gold>=== %prefix% ===</gold>").replace("%prefix%", prefixWord)));

        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_position",
                "<aqua>Position: </aqua><white>%coords%</white>")
                .replace("%coords%", loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ())));

        boolean powered = block.isBlockPowered() || block.isBlockIndirectlyPowered();
        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_power",
                "<aqua>Redstone: </aqua>%power%")
                .replace("%power%", powered ? msg("power_on", "<green>✔ On</green>")
                        : msg("power_off", "<red>✘ Off</red>"))));

        org.bukkit.block.Furnace furnace =
                block.getState() instanceof org.bukkit.block.Furnace f ? f : null;
        String fuelText = msg("fuel_none", "<gray>none</gray>");
        boolean hasFuel = false;
        if (furnace != null) {
            ItemStack fuel = furnace.getInventory().getFuel();
            if (fuel != null && !fuel.getType().isAir()) {
                hasFuel = true;
                fuelText = "<white>" + fuel.getType().name().toLowerCase(java.util.Locale.ROOT)
                        .replace('_', ' ') + " ×" + fuel.getAmount() + "</white>";
            }
        }
        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_fuel",
                "<aqua>Fuel: </aqua>%fuel%").replace("%fuel%", fuelText)));

        int burnTicks = GeneratorTask.burnTicksLeft(loc);
        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_burn",
                "<aqua>Burn: </aqua><white>%ticks% ticks left</white>")
                .replace("%ticks%", String.valueOf(burnTicks))));

        // Buffer + state — the same gates the task itself uses
        ensureGeneratorNode(loc);
        CableNode node = CableNetwork.getNode(loc);
        int energy = node != null ? node.getEnergy() : 0;
        int cap = node != null ? node.getMaxEnergy() : 0;
        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_buffer",
                "<aqua>Buffer: </aqua><white>%energy%/%max% ⚡</white>")
                .replace("%energy%", String.valueOf(energy))
                .replace("%max%", String.valueOf(cap))));

        boolean hasCable = findConnectedNode(loc) != null;
        int rate = 0;
        if (hasCable) {
            int perFuel = Math.max(1, Main.getInstance().getConfig()
                    .getInt("energy.generator.energy_per_fuel", 100));
            int burnDuration = Math.max(1, Main.getInstance().getConfig()
                    .getInt("energy.generator.fuel_burn_ticks", 200));
            rate = (int) Math.ceil((double) perFuel * GeneratorTask.PASS_TICKS / burnDuration);
        }
        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_output",
                "<aqua>Output: </aqua><white>%rate% ⚡/2s</white>")
                .replace("%rate%", String.valueOf(rate))));

        String state;
        if (!hasCable) {
            state = msg("status_no_cable", "<yellow>⚠ No cable</yellow>");
        } else if (!powered) {
            state = msg("status_no_power", "<yellow>⚠ No redstone</yellow>");
        } else if (cap > 0 && cap - energy < rate) {
            state = msg("state_frozen",
                    "<aqua>❄ Frozen (buffer full — waiting for a consumer)</aqua>");
        } else if (burnTicks > 0) {
            state = msg("status_working", "<green>✔ Working</green>");
        } else if (!hasFuel) {
            state = msg("state_idle", "<gray>Idle (no fuel)</gray>");
        } else {
            state = msg("state_ready", "<green>✔ Ready (has fuel)</green>");
        }
        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_state",
                "<aqua>State: </aqua>%state%").replace("%state%", state)));

        player.sendMessage(MessageUtil.parse(prefix() + msg("meter_connections",
                "<aqua>Connections: </aqua><white>%count%</white>")
                .replace("%count%", String.valueOf(node != null ? node.getConnectionKeys().size() : 0))));
    }

    // =========================
    // REGISTER ON PLACE (called from BlockPlaceListener)
    // =========================
    public static void onGeneratorPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;

        if (activeGenerators.containsKey(loc)) return;

        // No cable requirement here: registration is harmless, generation
        // itself skips generators without a connected node (the task checks
        // it every run). A cable placed later connects the node automatically.
        activeGenerators.put(loc, true);
        StructureMarker.place(loc, "generator", UUID.randomUUID());
        ensureGeneratorNode(loc);

        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    loc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0);
            world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        }

        ConsoleLogger.info("[Generator] Registered at " + loc);
    }

    // =========================
    // UNREGISTER ON BREAK (called from BlockBreakListener)
    // =========================
    public static boolean removeGenerator(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        Boolean was = activeGenerators.remove(loc);
        if (was != null) {
            StructureMarker.removeAt(loc);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            }
            ConsoleLogger.info("[Generator] Removed at " + loc);
        }
        return was != null;
    }

    // =========================
    // SHIFT+RMB → info / status (no more assembly via interact)
    // =========================
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (clicked.getType() != Materials.BLAST_FURNACE) return;

        Location loc = LocationUtil.normalize(clicked.getLocation());
        if (loc == null) return;

        // If it's a generator — show info, don't cancel the event (let GUI open)
        if (activeGenerators.containsKey(loc)) {
            boolean hasCable = hasNearbyCable(loc);
            boolean powered = clicked.isBlockPowered() || clicked.isBlockIndirectlyPowered();
            String status = hasCable
                    ? (powered ? msg("status_working", "<green>✔ Working</green>")
                               : msg("status_no_power", "<yellow>⚠ No redstone</yellow>"))
                    : msg("status_no_cable", "<yellow>⚠ No cable</yellow>");
            String power = powered
                    ? msg("power_on", "<green>✔ On</green>")
                    : msg("power_off", "<red>✘ Off</red>");
            player.sendMessage(MessageUtil.parse(msg("info_header", "<gold>=== Generator ===</gold>")));
            player.sendMessage(MessageUtil.parse(msg("info_status", "<aqua>Status: </aqua>%status%")
                    .replace("%status%", status)));
            player.sendMessage(MessageUtil.parse(msg("info_power", "<aqua>Power: </aqua>%power%")
                    .replace("%power%", power)));
            player.sendMessage(MessageUtil.parse(msg("info_hint",
                    "<dark_gray>Put fuel into the furnace — energy goes to the network.</dark_gray>")));
            // Don't cancel — let the furnace GUI open
            return;
        }
    }

    // =========================
    // ASSEMBLE FROM FRAME (called from ReactorListener — kept for compatibility)
    // =========================
    public static void assembleFromFrame(Player player, Location furnaceLoc) {
        furnaceLoc = LocationUtil.normalize(furnaceLoc);
        if (furnaceLoc == null) return;

        activeGenerators.put(furnaceLoc, true);
        StructureMarker.place(furnaceLoc, "generator", UUID.randomUUID());
        ensureGeneratorNode(furnaceLoc);

        World world = furnaceLoc.getWorld();
        if (world != null) {
            world.strikeLightningEffect(furnaceLoc.clone().add(0.5, 1.5, 0.5));
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    furnaceLoc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
            world.playSound(furnaceLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            world.playSound(furnaceLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        }

        player.sendMessage(MessageUtil.parse(msg("assembled", "<green>✔ Generator assembled!</green>")
                + " <dark_gray>[" + furnaceLoc.getBlockX() + " " + furnaceLoc.getBlockY() + " " + furnaceLoc.getBlockZ() + "]</dark_gray>"));
        player.sendMessage(MessageUtil.parse(msg("assembled_hint",
                "<dark_gray>Put fuel into the blast furnace — energy goes to the network!</dark_gray>")));

        ConsoleLogger.info("[Generator] Assembled via frame at " + furnaceLoc + " by " + player.getName());
    }

    // =========================
    // CABLE NEARBY CHECK
    // =========================
    public static boolean hasNearbyCable(Location generatorLoc) {
        generatorLoc = LocationUtil.normalize(generatorLoc);
        if (generatorLoc == null) return false;

        for (Location near : LocationUtil.getNeighbors(generatorLoc)) {
            Location norm = LocationUtil.normalize(near);
            if (norm != null && CableNetwork.getNode(norm) != null) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // GENERATOR NODE — the furnace itself is a GENERATOR-typed network node
    // holding a small leftover buffer. EnergyFlow.push charges batteries
    // first; whatever no battery takes lands here, and any consumer pulling
    // through the network (reactor junctions, accelerator, ...) drains it —
    // direct generator → consumer flow works without a battery. The node is
    // RAM-only (rebuilt lazily by the task after a restart).
    // =========================
    public static void ensureGeneratorNode(Location furnaceLoc) {
        Location norm = LocationUtil.normalize(furnaceLoc);
        if (norm == null) return;
        CableNetwork.ensureNode(norm, NodeType.GENERATOR);
        CableNode node = CableNetwork.getNode(norm);
        if (node != null) {
            node.setMaxEnergy(bufferCapacity());
        }
    }

    /** Leftover buffer capacity of one generator (config: energy.generator.buffer_capacity). */
    public static int bufferCapacity() {
        return Math.max(1, Main.getInstance().getConfig()
                .getInt("energy.generator.buffer_capacity", 2000));
    }

    // =========================
    // FIND CONNECTED CABLE NODE
    // =========================
    public static CableNode findConnectedNode(Location generatorLoc) {
        generatorLoc = LocationUtil.normalize(generatorLoc);
        if (generatorLoc == null) return null;

        for (Location near : LocationUtil.getNeighbors(generatorLoc)) {
            Location norm = LocationUtil.normalize(near);
            if (norm == null) continue;
            CableNode node = CableNetwork.getNode(norm);
            if (node != null
                    && LocationUtil.isFullyConnected(generatorLoc, norm)) {
                return node;
            }
        }
        return null;
    }

    // =========================
    // SHUTDOWN (for hot-disable)
    // =========================
    public static void shutdown() {
        if (instance != null) {
            org.bukkit.event.HandlerList.unregisterAll(instance);
            instance = null;
        }
        clearAll();
    }

    // =========================
    // CLEANUP
    // =========================
    public static void clearAll() {
        activeGenerators.clear();
    }
}
