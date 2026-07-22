package com.mcplugin.energy.machines.assembler;

import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;

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
 * Manages assembled Item Creator (Создатель предметов) blocks.
 * Activated by placing a CRAFTER block — shift+RMB to assemble/disassemble.
 * Only assembled item creators auto-craft items (checked by {@link AssemblerTask}).
 */
public class AssemblerManager implements Listener {

    private static AssemblerManager instance;

    // Active item creators: crafter location → enabled flag
    private static final Map<Location, Boolean> activeAssemblers = new ConcurrentHashMap<>();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new AssemblerManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        scanExistingAssemblers();
        ConsoleLogger.info("[Assembler] Manager initialized.");
    }

    public static AssemblerManager getInstance() {
        return instance;
    }

    // =========================
    // SCAN EXISTING — rebuild from Marker entities
    // =========================
    private static void scanExistingAssemblers() {
        int count = 0;
        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            String type = entry.getValue().type();
            if (!"assembler".equals(type) && !"item_creator".equals(type)) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            for (World world : Bukkit.getWorlds()) {
                if (!world.getUID().toString().equals(worldUid)) continue;
                Location crafterLoc = LocationUtil.normalize(new Location(world, x, y, z));
                if (crafterLoc == null) continue;
                if (crafterLoc.getBlock().getType() != Material.CRAFTER) continue;
                activeAssemblers.put(crafterLoc, true);
                count++;
                break;
            }
        }
        ConsoleLogger.info(
                "[Assembler] Auto-detected " + count + " item creators from Marker entities");
    }

    // =========================
    // STATE
    // =========================
    public static boolean isAssembled(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        return activeAssemblers.getOrDefault(loc, false);
    }

    public static Collection<Location> getActiveAssemblers() {
        return activeAssemblers.keySet();
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
        if (clicked.getType() != Material.CRAFTER) return;

        Location loc = LocationUtil.normalize(clicked.getLocation());
        if (loc == null) return;

        if (loc.getBlock().getType() != Material.CRAFTER) return;

        if (activeAssemblers.containsKey(loc)) {
            event.setCancelled(true);
            // DISASSEMBLE
            removeAssembler(loc);
            player.sendMessage(MessageUtil.parse("<yellow>⚡ Создатель предметов разобран!</yellow>"
                    + " <dark_gray>[" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "]</dark_gray>"));
            ConsoleLogger.info(
                    "[Assembler] Disassembled at " + loc + " by " + player.getName());
        } else {
            event.setCancelled(true);
            // ASSEMBLE
            activeAssemblers.put(loc, true);
            StructureMarker.place(loc, "item_creator", UUID.randomUUID());
            // Регистрируем в EnergyWorkbenchManager, чтобы AssemblerListener мог открыть GUI
            EnergyWorkbenchManager.add(loc);

            World world = loc.getWorld();
            if (world != null) {
                world.strikeLightningEffect(loc.clone().add(0.5, 1.5, 0.5));
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
            }

            player.sendMessage(MessageUtil.parse("<green>✔ Создатель предметов собран!</green>"
                    + " <dark_gray>[" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "]</dark_gray>"));
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ </dark_gray><gray>Разместите предметы в сетке 3x3 по рецепту.</gray>"));
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ </dark_gray><gray>Подайте <yellow>100⚡</yellow> энергии и редстоун-сигнал для крафта.</gray>"));

            ConsoleLogger.info(
                    "[Assembler] Item Creator assembled at " + loc + " by " + player.getName());
        }
    }

    // =========================
    // REMOVE ASSEMBLER
    // =========================
    public static boolean removeAssembler(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        Boolean was = activeAssemblers.remove(loc);
        // Удаляем из EnergyWorkbenchManager, чтобы не пытаться открыть GUI для разобранного CRAFTER
        EnergyWorkbenchManager.remove(loc);
        if (was != null) {
            StructureMarker.removeAt(loc);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            }
            ConsoleLogger.info(
                    "[Assembler] Removed at " + loc);
        }
        return was != null;
    }

    // =========================
    // REMOVE FRAME ON TOP
    // =========================

    // =========================
    // SHUTDOWN
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
        activeAssemblers.clear();
    }
}
