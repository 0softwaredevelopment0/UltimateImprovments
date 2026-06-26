package com.mcplugin.energy.generation.basic;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.structure.StructureMarker;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.infrastructure.util.LocationUtil;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages assembled generator structures.
 * Handles assembly/disassembly via shift+RMB on BLAST_FURNACE with item frame on top.
 * Only assembled generators produce energy (checked by GeneratorTask).
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
        Main.getInstance().getLogger().info("[Generator] Manager initialized.");
    }

    public static GeneratorManager getInstance() {
        return instance;
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
                if (furnaceLoc.getBlock().getType() != Material.BLAST_FURNACE) continue;
                if (!GeneratorStructure.isValid(furnaceLoc, false)) continue;
                if (hasNearbyCable(furnaceLoc)) {
                    activeGenerators.put(furnaceLoc, true);
                    count++;
                }
                break;
            }
        }
        Main.getInstance().getLogger().info(
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
    // SHIFT+RMB → ASSEMBLE / DISASSEMBLE
    // =========================
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (clicked.getType() != Material.BLAST_FURNACE) return;

        Location loc = LocationUtil.normalize(clicked.getLocation());
        if (loc == null) return;

        // Check structure: blast furnace + item frame on top
        if (!GeneratorStructure.isValid(loc)) {
            List<String> errors = GeneratorStructure.getValidationErrors(loc);
            if (!errors.isEmpty()) {
                player.sendMessage("§4❌ §cСтруктура генератора повреждена! §7Ошибки:");
                for (String err : errors) {
                    player.sendMessage("§8 • §f" + err);
                }
            }
            return;
        }

        // Check cable nearby
        if (!hasNearbyCable(loc)) {
            player.sendMessage("§4❌ §cНет кабеля рядом с плавильной печью!"
                    + " §7Подключите кабель к соседнему блоку.");
            return;
        }

        if (activeGenerators.containsKey(loc)) {
            event.setCancelled(true);
            // DISASSEMBLE
            removeGenerator(loc);
            player.sendMessage("§e⚡ Генератор разобран!"
                    + " §8[§7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "§8]");
            Main.getInstance().getLogger().info(
                    "[Generator] Disassembled at " + loc + " by " + player.getName());
        } else {
            event.setCancelled(true);
            // ASSEMBLE
            removeFrameOnTop(loc);
            activeGenerators.put(loc, true);
            // Marker entity для переноса мира
            StructureMarker.place(loc, "generator", UUID.randomUUID());

            World world = loc.getWorld();
            if (world != null) {
                world.strikeLightningEffect(loc.clone().add(0.5, 1.5, 0.5));
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
            }

            player.sendMessage("§a✔ Генератор собран!"
                    + " §8[§7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "§8]");
            player.sendMessage("§8┃ §7Положите топливо в плавильную печь — энергия пойдёт в сеть!");

            Main.getInstance().getLogger().info(
                    "[Generator] Assembled at " + loc + " by " + player.getName());
        }
    }

    // =========================
    // CABLE NEARBY CHECK
    // =========================
    // =========================
    // REMOVE GENERATOR (вызывается при ломании BLAST_FURNACE / shift+ПКМ)
    // =========================
    public static boolean removeGenerator(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        Boolean was = activeGenerators.remove(loc);
        if (was != null) {
            // Удаляем Marker entity
            StructureMarker.removeAt(loc);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            }
            Main.getInstance().getLogger().info(
                    "[Generator] Removed at " + loc);
        }
        return was != null;
    }

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
    // REMOVE FRAME ON TOP (ломает рамку над печью и дропает)
    // =========================
    private static void removeFrameOnTop(Location furnaceLoc) {
        World world = furnaceLoc.getWorld();
        if (world == null) return;

        double targetX = furnaceLoc.getX() + 0.5;
        double targetY = furnaceLoc.getY() + 1.0;
        double targetZ = furnaceLoc.getZ() + 0.5;

        for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
            double dx = Math.abs(frame.getLocation().getX() - targetX);
            double dz = Math.abs(frame.getLocation().getZ() - targetZ);
            double dy = Math.abs(frame.getLocation().getY() - targetY);

            if (dx < 0.6 && dz < 0.6 && dy < 0.6) {
                if (frame.isValid() && !frame.isDead()) {
                    frame.getWorld().dropItemNaturally(frame.getLocation(), new ItemStack(Material.ITEM_FRAME));
                    frame.remove();
                }
                return;
            }
        }
    }

    // =========================
    // ASSEMBLE FROM FRAME (вызывается из ReactorListener)
    // =========================
    public static void assembleFromFrame(Player player, Location furnaceLoc) {
        furnaceLoc = LocationUtil.normalize(furnaceLoc);
        if (furnaceLoc == null) return;

        removeFrameOnTop(furnaceLoc);
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

        player.sendMessage("§a✔ Генератор собран!"
                + " §8[§7" + furnaceLoc.getBlockX() + " " + furnaceLoc.getBlockY() + " " + furnaceLoc.getBlockZ() + "§8]");
        player.sendMessage("§8┃ §7Положите топливо в плавильную печь — энергия пойдёт в сеть!");

        Main.getInstance().getLogger().info(
                "[Generator] Assembled at " + furnaceLoc + " by " + player.getName());
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
