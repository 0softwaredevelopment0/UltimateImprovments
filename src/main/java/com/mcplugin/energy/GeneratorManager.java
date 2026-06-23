package com.mcplugin.energy;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.util.LocationUtil;

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
    // SCAN EXISTING (on startup)
    // =========================
    private static void scanExistingGenerators() {
        // Scan all loaded chunks for blast furnaces with item frames
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!(entity instanceof ItemFrame frame)) continue;
                if (!frame.isValid() || frame.isDead()) continue;

                Block attached = frame.getLocation().getBlock()
                        .getRelative(frame.getFacing().getOppositeFace());
                Location attachedLoc = LocationUtil.normalize(attached.getLocation());

                if (attachedLoc != null
                        && attachedLoc.getBlock().getType() == Material.BLAST_FURNACE
                        && GeneratorStructure.isValid(attachedLoc)) {
                    // Check if cable is connected nearby
                    if (hasNearbyCable(attachedLoc)) {
                        activeGenerators.put(attachedLoc, true);
                        Main.getInstance().getLogger().info(
                                "[Generator] Auto-detected at " + attachedLoc);
                    }
                }
            }
        }
        Main.getInstance().getLogger().info(
                "[Generator] Auto-detected " + activeGenerators.size() + " generators");
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
    @EventHandler(priority = EventPriority.NORMAL)
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
            boolean wasEnabled = activeGenerators.remove(loc);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            }

            player.sendMessage("§e⚡ Генератор разобран!"
                    + " §8[§7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "§8]");

            Main.getInstance().getLogger().info(
                    "[Generator] Disassembled at " + loc + " by " + player.getName());
        } else {
            event.setCancelled(true);
            // ASSEMBLE
            activeGenerators.put(loc, true);

            World world = loc.getWorld();
            if (world != null) {
                world.strikeLightningEffect(loc.clone().add(0.5, 1.5, 0.5));
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
            }

            player.sendMessage("§a✅ Генератор собран!"
                    + " §8[§7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "§8]");
            player.sendMessage("§8┃ §7Положите топливо в плавильную печь — энергия пойдёт в сеть!");

            Main.getInstance().getLogger().info(
                    "[Generator] Assembled at " + loc + " by " + player.getName());
        }
    }

    // =========================
    // CABLE NEARBY CHECK
    // =========================
    private static boolean hasNearbyCable(Location generatorLoc) {
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
    // CLEANUP
    // =========================
    public static void clearAll() {
        activeGenerators.clear();
    }
}
