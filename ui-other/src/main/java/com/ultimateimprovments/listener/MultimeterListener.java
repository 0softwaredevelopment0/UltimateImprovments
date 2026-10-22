package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.*;
import com.ultimateimprovments.mechanics.features.structure.StructureIntegrityManager;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.MultimeterApi;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Generic multimeter readout: cables, batteries, junctions, particle-accelerator
 * blocks and structure-integrity blocks.
 * <p>
 * It is the LAST listener of the click ({@link EventPriority#HIGHEST}) and the
 * fallback of the whole multimeter feature: subsystem readouts (generator, CER,
 * DFC control sign) run earlier, cancel the event and mark the click as
 * measured through {@link MultimeterApi}. When this listener reaches a block it
 * cannot read it asks {@link MultimeterApi#finishMeasurement(Player)} — that
 * method sends the standard {@code [METER] Can't measure parameters of this
 * block.} error only when no other subsystem answered, and stays silent
 * otherwise.
 */
public class MultimeterListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Player player = e.getPlayer();
        Block block = e.getClickedBlock();

        // The item of THIS event's hand (the event fires per hand — with the
        // multimeter in the off hand the main-hand check would miss it).
        ItemStack item = e.getItem();

        // =========================
        // PDC CHECK (MULTIMETER ONLY)
        // =========================
        if (!MultimeterApi.isMultimeter(item)) return;

        // The multimeter is a SHIFT+RMB tool: a plain right-click keeps its
        // vanilla behaviour (furnace GUI, sign editor, block placement, ...).
        if (!player.isSneaking()) return;

        // Compatibility gate: a subsystem readout (generator, CER, DFC control
        // sign) may already have answered this click. Do not append a second
        // "nothing to read here" line on top of its parameters. This also
        // covers the off-hand event of one click, whose event is a separate
        // PlayerInteractEvent.
        if (MultimeterApi.wasMeasured(player)) return;

        // Click sound when measuring
        player.playSound(block.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        Material type = block.getType();

        // =========================
        // 🏗 STRUCTURE INTEGRITY (ENDER_CHEST)
        // =========================
        if (type == Material.ENDER_CHEST) {
            Location loc = LocationUtil.normalize(block.getLocation());
            StructureIntegrityManager sim = StructureIntegrityManager.getInstance();
            if (sim != null) {
                sim.showInfo(player, loc);
            } else {
                player.sendMessage(MessageUtil.parse("<red>❌ Structure Integrity system not available.</red>"));
            }
            // The branch owns the click even when the system is down — one
            // answer per click and hand is enough.
            MultimeterApi.markMeasured(player);
            return;
        }

        // =========================
        // ⚡ ACCELERATOR ENGINE (TUFF_BRICKS)
        // =========================
        if (CoreHooks.isParticleEngine(type) && StructureMarker.existsAt(LocationUtil.normalize(block.getLocation()))) {
            Location loc = LocationUtil.normalize(block.getLocation());
            int energy = CoreHooks.particleEngineEnergy(loc);
            boolean canAccel = CoreHooks.particleCanEngineAccelerate(loc);
            boolean hasRedstone = block.isBlockPowered() || block.isBlockIndirectlyPowered();

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
            player.sendMessage(MessageUtil.parse("<aqua>Type: </aqua><white>Двигатель ускорителя</white>"));
            String energyColor = energy >= 1000 ? "<green>" : "<yellow>";
            player.sendMessage(MessageUtil.parse("<aqua>Buffer: </aqua>" + energyColor + energy + "/1000 ⚡</" + (energy >= 1000 ? "green" : "yellow") + ">"));
            player.sendMessage(MessageUtil.parse("<aqua>Redstone: </aqua><white>" + (hasRedstone ? "<green>✔</green>" : "<red>✘</red>") + "</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Status: </aqua>" + (canAccel ? "<green>✔ Готов ускорить</green>" : "<yellow>⏳ Не готов (нужно 1000⚡ + редстоун)</yellow>")));
            MultimeterApi.markMeasured(player);
            return;
        }

        // =========================
        // ⚡ SPEED SENSOR (POLISHED_DIORITE)
        // =========================
        if (CoreHooks.isParticleSensor(type) && StructureMarker.existsAt(LocationUtil.normalize(block.getLocation()))) {
            Location loc = LocationUtil.normalize(block.getLocation());
            double lastSpeed = CoreHooks.particleSensorLastSpeed(loc);
            double speedPct = (lastSpeed / CoreHooks.particleMaxSpeed()) * 100.0;

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
            player.sendMessage(MessageUtil.parse("<aqua>Type: </aqua><white>Датчик скорости</white>"));
            if (lastSpeed > 0) {
                String pctColor = speedPct >= 90 ? "<red>" : speedPct >= 50 ? "<yellow>" : "<green>";
                player.sendMessage(MessageUtil.parse("<aqua>Last speed: </aqua><white>" + pctColor + String.format("%.3f", speedPct) + "%</white>"));
                player.sendMessage(MessageUtil.parse("<dark_gray>( " + String.format("%.4f", lastSpeed) + " blocks/tick )</dark_gray>"));
            } else {
                player.sendMessage(MessageUtil.parse("<aqua>Last speed: </aqua><gray>No particle has passed yet</gray>"));
            }
            MultimeterApi.markMeasured(player);
            return;
        }

        CableNode node = CableNetwork.getNode(block.getLocation());

        // =========================
        // BATTERY
        // =========================
        if (node != null && type == Materials.WAXED_COPPER_GRATE) {

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));

            int transferred = node.getAndResetTransferred();
            BatteryManager.BatteryCluster cluster = BatteryManager.getCluster(block.getLocation());
            int capacity = cluster != null ? cluster.capacity : node.getMaxEnergy();
            int blocks = cluster != null ? cluster.blockKeys.size() : 1;
            String mode = cluster != null ? cluster.getModeDisplay() : "<gray>?</gray>";
            int stored = node.getEnergy();
            double pct = capacity > 0 ? (double) stored / capacity * 100.0 : 0.0;

            player.sendMessage(MessageUtil.parse("<aqua>Type: Battery (multiblock)</aqua>"));
            player.sendMessage(MessageUtil.parse("<aqua>Energy: </aqua><white>" + stored + " / " + capacity
                    + "</white> <gray>(" + String.format("%.1f", Math.min(100.0, pct)) + "% of the whole structure)</gray>"));
            player.sendMessage(MessageUtil.parse("<aqua>Blocks: </aqua><white>" + blocks
                    + " <gray>(one shared pool, " + (blocks > 0 ? capacity / blocks : 0) + " ⚡ per block)</gray>"));
            player.sendMessage(MessageUtil.parse("<aqua>Mode: </aqua><white>" + mode + "</white> <gray>(redstone pulse cycles)</gray>"));
            player.sendMessage(MessageUtil.parse("<aqua>Transfer rate: </aqua><white>" + transferred + " ⚡/tick</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Connections: </aqua><white>" + node.getConnectionKeys().size() + "</white>"));
            MultimeterApi.markMeasured(player);
            return;
        }

        // =========================
        // CABLE
        // =========================
        if (node != null && type == Materials.WAXED_LIGHTNING_ROD) {

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));

            int transferred = node.getAndResetTransferred();
            boolean flowing = CableNetwork.isFlowing(LocationUtil.normalize(block.getLocation()));

            player.sendMessage(MessageUtil.parse("<yellow>Type: Cable</yellow>"));
            player.sendMessage(MessageUtil.parse("<yellow>Transfer speed: </yellow><white>" + transferred + " ⚡/tick</white>"));
            player.sendMessage(MessageUtil.parse("<yellow>Status: </yellow><white>" + (flowing ? "<green>⚡ FLOWING</green>" : "<gray>IDLE</gray>") + "</white>"));
            player.sendMessage(MessageUtil.parse("<yellow>Connections: </yellow><white>" + node.getConnectionKeys().size() + "</white>"));

            BlockData bd = block.getBlockData();

            if (bd instanceof Directional directional) {

                BlockFace face = directional.getFacing();

                String axis;

                if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                    axis = "NORTH/SOUTH";
                } else if (face == BlockFace.EAST || face == BlockFace.WEST) {
                    axis = "EAST/WEST";
                } else {
                    axis = "UP/DOWN";
                }

                player.sendMessage(MessageUtil.parse("<yellow>Axis: </yellow><white>" + axis + "</white>"));
            }

            MultimeterApi.markMeasured(player);
            return;
        }

        // =========================
        // JUNCTION
        // =========================
        if (node != null && type == Materials.WAXED_CHISELED_COPPER) {

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));

            int transferred = node.getAndResetTransferred();
            player.sendMessage(MessageUtil.parse("<gold>Type: Junction</gold>"));
            // A copper port can be a storage node: the compact nuclear
            // reactor's output buffer lives on its bottom port (the only
            // GENERATOR-typed junction in the family), so it reads like the
            // generator buffer instead of hiding the charge behind 0 ⚡/tick.
            if (node.getType() == NodeType.GENERATOR) {
                player.sendMessage(MessageUtil.parse("<gold>Buffer: </gold><white>"
                        + node.getEnergy() + "/" + node.getMaxEnergy() + " ⚡</white>"));
            }
            player.sendMessage(MessageUtil.parse("<gold>Transfer rate: </gold><white>" + transferred + " ⚡/tick</white>"));
            player.sendMessage(MessageUtil.parse("<gold>Connections: </gold><white>" + node.getConnectionKeys().size() + "</white>"));
            player.sendMessage(MessageUtil.parse("<gold>Mode: </gold><white>Omni-directional</white>"));
            MultimeterApi.markMeasured(player);
            return;
        }

        // Nothing here can be measured: the shared API reports it in chat, but
        // only when no other subsystem already answered this click.
        MultimeterApi.finishMeasurement(player);
    }
}
