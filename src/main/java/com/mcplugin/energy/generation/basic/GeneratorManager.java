package com.mcplugin.energy.generation.basic;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;

import java.util.UUID;

import com.mcplugin.util.Materials;
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
 * Generators are now single-block (BLAST_FURNACE with PDC tag), crafted in Item Creator.
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
        ConsoleLogger.info("[Generator] Manager initialized.");
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

    /** Creates a generator item (BLAST_FURNACE with PDC) for the Item Creator recipe. */
    public static ItemStack createGeneratorItem() {
        ItemStack item = new ItemStack(Materials.BLAST_FURNACE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><gold>✦ Генератор ✦</gold>"));
            meta.lore(List.of(
                    MessageUtil.parse("<!italic><gray>Сжигает топливо для выработки энергии</gray>"),
                    MessageUtil.parse("<!italic><gray>Поставьте и подключите кабель</gray>")
            ));
            meta.getPersistentDataContainer().set(Keys.GENERATOR, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    // =========================
    // SCAN EXISTING — rebuild from Marker entities
    // =========================
    private static void scanExistingGenerators() {
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
                if (hasNearbyCable(furnaceLoc)) {
                    activeGenerators.put(furnaceLoc, true);
                    count++;
                }
                break;
            }
        }
        ConsoleLogger.info(
                "[Generator] Auto-detected " + count + " generators from Marker entities");
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
    // REGISTER ON PLACE (called from BlockPlaceListener)
    // =========================
    public static void onGeneratorPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return;

        if (activeGenerators.containsKey(loc)) return;
        if (!hasNearbyCable(loc)) {
            ConsoleLogger.info("[Generator] Placed at " + loc + " but no cable nearby — not activating.");
            return;
        }

        activeGenerators.put(loc, true);
        StructureMarker.place(loc, "generator", UUID.randomUUID());

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
            player.sendMessage(MessageUtil.parse("<gold>=== Генератор ===</gold>"));
            player.sendMessage(MessageUtil.parse("<aqua>Status: </aqua>" + (hasCable ? "<green>✔ Работает</green>" : "<yellow>⚠ Нет кабеля</yellow>")));
            player.sendMessage(MessageUtil.parse("<dark_gray>Положите топливо в печь — энергия пойдёт в сеть.</dark_gray>"));
            // Don't cancel — let the furnace GUI open
            return;
        }
    }

    // =========================
    // ASSEMBLE FROM FRAME (called from ReactorListener — сохраняем для совместимости)
    // =========================
    public static void assembleFromFrame(Player player, Location furnaceLoc) {
        furnaceLoc = LocationUtil.normalize(furnaceLoc);
        if (furnaceLoc == null) return;

        activeGenerators.put(furnaceLoc, true);
        StructureMarker.place(furnaceLoc, "generator", UUID.randomUUID());

        World world = furnaceLoc.getWorld();
        if (world != null) {
            world.strikeLightningEffect(furnaceLoc.clone().add(0.5, 1.5, 0.5));
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    furnaceLoc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
            world.playSound(furnaceLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            world.playSound(furnaceLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        }

        player.sendMessage(MessageUtil.parse("<green>✔ Генератор собран!</green>"
                + " <dark_gray>[" + furnaceLoc.getBlockX() + " " + furnaceLoc.getBlockY() + " " + furnaceLoc.getBlockZ() + "]</dark_gray>"));
        player.sendMessage(MessageUtil.parse("<dark_gray>Положите топливо в плавильную печь — энергия пойдёт в сеть!</dark_gray>"));

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
