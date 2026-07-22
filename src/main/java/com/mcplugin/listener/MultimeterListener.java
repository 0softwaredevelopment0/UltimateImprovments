package com.mcplugin.listener;

import com.mcplugin.energy.generation.basic.GeneratorManager;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.core.Main;
import com.mcplugin.energy.machines.assembler.AssemblerManager;
import com.mcplugin.energy.transfer.cable.*;
import com.mcplugin.mechanics.particle.ParticleAcceleratorManager;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.Materials;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.mechanics.features.structure.StructureIntegrityManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MultimeterListener implements Listener {

    private static final NamespacedKey KEY =
            new NamespacedKey(Main.getInstance(), "is_multimeter");

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {

        if (e.getClickedBlock() == null) return;

        Player player = e.getPlayer();
        Block block = e.getClickedBlock();

        ItemStack item = player.getInventory().getItemInMainHand();

        // =========================
        // PDC CHECK (MULTIMETER ONLY)
        // =========================
        if (!is_multimeter(item)) return;

        // Звук клика при измерении
        player.playSound(block.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        Material type = block.getType();

        // =========================
        // 🛠 СОЗДАТЕЛЬ ПРЕДМЕТОВ (CRAFTER)
        // =========================
        if (type == Material.CRAFTER) {
            Location loc = LocationUtil.normalize(block.getLocation());
            if (EnergyWorkbenchManager.exists(loc) || AssemblerManager.isAssembled(loc)) {
                int buffer = EnergyWorkbenchManager.getBufferEnergy(loc);
                boolean hasRedstone = block.isBlockPowered() || block.isBlockIndirectlyPowered();
                player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
                player.sendMessage(MessageUtil.parse("<aqua>Type: </aqua><white>Создатель предметов</white>"));
                player.sendMessage(MessageUtil.parse("<aqua>Buffer: </aqua>"
                        + (buffer >= 100 ? "<green>" : "<yellow>")
                        + buffer + "/100 ⚡</" + (buffer >= 100 ? "green" : "yellow") + ">"));
                player.sendMessage(MessageUtil.parse("<aqua>Redstone: </aqua><white>" + (hasRedstone ? "<green>✔</green>" : "<red>✘</red>") + "</white>"));
                player.sendMessage(MessageUtil.parse("<dark_gray>Требуется: 100⚡ + редстоун-сигнал для крафта</dark_gray>"));
                return;
            }
        }

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
            return;
        }

        // =========================
        // ⚡ ДВИГАТЕЛЬ УСКОРИТЕЛЯ (TUFF_BRICKS)
        // =========================
        if (type == ParticleAcceleratorManager.ENGINE && StructureMarker.existsAt(LocationUtil.normalize(block.getLocation()))) {
            Location loc = LocationUtil.normalize(block.getLocation());
            int energy = ParticleAcceleratorManager.getEngineEnergy(loc);
            boolean canAccel = ParticleAcceleratorManager.canEngineAccelerate(loc);
            boolean hasRedstone = block.isBlockPowered() || block.isBlockIndirectlyPowered();

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
            player.sendMessage(MessageUtil.parse("<aqua>Type: </aqua><white>Двигатель ускорителя</white>"));
            String energyColor = energy >= 1000 ? "<green>" : "<yellow>";
            player.sendMessage(MessageUtil.parse("<aqua>Buffer: </aqua>" + energyColor + energy + "/1000 ⚡</" + (energy >= 1000 ? "green" : "yellow") + ">"));
            player.sendMessage(MessageUtil.parse("<aqua>Redstone: </aqua><white>" + (hasRedstone ? "<green>✔</green>" : "<red>✘</red>") + "</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Status: </aqua>" + (canAccel ? "<green>✔ Готов ускорить</green>" : "<yellow>⏳ Не готов (нужно 1000⚡ + редстоун)</yellow>")));
            return;
        }

        // =========================
        // ⚡ ДАТЧИК СКОРОСТИ (POLISHED_DIORITE)
        // =========================
        if (type == ParticleAcceleratorManager.SENSOR && StructureMarker.existsAt(LocationUtil.normalize(block.getLocation()))) {
            Location loc = LocationUtil.normalize(block.getLocation());
            double lastSpeed = ParticleAcceleratorManager.getSensorLastSpeed(loc);
            double speedPct = (lastSpeed / ParticleAcceleratorManager.MAX_SPEED) * 100.0;

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
            player.sendMessage(MessageUtil.parse("<aqua>Type: </aqua><white>Датчик скорости</white>"));
            if (lastSpeed > 0) {
                String pctColor = speedPct >= 90 ? "<red>" : speedPct >= 50 ? "<yellow>" : "<green>";
                player.sendMessage(MessageUtil.parse("<aqua>Last speed: </aqua><white>" + pctColor + String.format("%.3f", speedPct) + "%</white>"));
                player.sendMessage(MessageUtil.parse("<dark_gray>( " + String.format("%.4f", lastSpeed) + " blocks/tick )</dark_gray>"));
            } else {
                player.sendMessage(MessageUtil.parse("<aqua>Last speed: </aqua><gray>No particle has passed yet</gray>"));
            }
            return;
        }

        // =========================
        // ⚡ ГЕНЕРАТОР (BLAST_FURNACE)
        // =========================
        if (type == Materials.BLAST_FURNACE) {
            boolean assembled = GeneratorManager.isAssembled(block.getLocation());

            player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
            player.sendMessage(MessageUtil.parse("<aqua>Type: </aqua><white>Генератор</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Registered: </aqua><white>" + (assembled ? "<green>✔</green>" : "<red>✘</red>") + "</white>"));

            if (block.getState() instanceof Furnace furnace) {
                ItemStack fuel = furnace.getInventory().getFuel();
                boolean hasFuel = fuel != null && !fuel.getType().isAir();
                player.sendMessage(MessageUtil.parse("<aqua>Fuel: </aqua><white>" + (hasFuel ? fuel.getType().name().toLowerCase().replace('_', ' ') + " x" + fuel.getAmount() : "<gray>none</gray>") + "</white>"));

                boolean isLit = false;
                if (block.getBlockData() instanceof org.bukkit.block.data.type.Furnace furnaceData) {
                    isLit = furnaceData.isLit();
                }
                player.sendMessage(MessageUtil.parse("<aqua>Burning: </aqua><white>" + (isLit ? "<green>✔</green>" : "<red>✘</red>") + "</white>"));

                int cookTime = furnace.getCookTime();
                int cookTimeTotal = furnace.getCookTimeTotal();
                if (cookTimeTotal > 0) {
                    int progress = (cookTime * 100) / cookTimeTotal;
                    player.sendMessage(MessageUtil.parse("<aqua>Progress: </aqua><white>" + Math.min(progress, 100) + "%</white>"));
                }
            }

            // Redstone status
            boolean hasRedstone = block.isBlockPowered() || block.isBlockIndirectlyPowered();
            player.sendMessage(MessageUtil.parse("<aqua>Redstone: </aqua><white>" + (hasRedstone ? "<green>✔</green>" : "<red>✘</red>") + "</white>"));

            return;
        }

        CableNode node = CableNetwork.getNode(block.getLocation());

        player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));

        if (node == null) {
            player.sendMessage(MessageUtil.parse("<gray>No energy node at this block.</gray>"));
            return;
        }

        // =========================
        // BATTERY
        // =========================
        if (type == Materials.WAXED_COPPER_GRATE) {

            int transferred = node.getAndResetTransferred();
            player.sendMessage(MessageUtil.parse("<aqua>Type: Battery</aqua>"));
            player.sendMessage(MessageUtil.parse("<aqua>Energy: </aqua><white>" + node.getEnergy() + "</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Transfer rate: </aqua><white>" + transferred + " ⚡/tick</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Connections: </aqua><white>" + node.getConnections().size() + "</white>"));
            return;
        }

        // =========================
        // CABLE
        // =========================
        if (type == Materials.WAXED_LIGHTNING_ROD) {

            int transferred = node.getAndResetTransferred();
            boolean flowing = CableNetwork.isFlowing(LocationUtil.normalize(block.getLocation()));

            player.sendMessage(MessageUtil.parse("<yellow>Type: Cable</yellow>"));
            player.sendMessage(MessageUtil.parse("<yellow>Transfer speed: </yellow><white>" + transferred + " ⚡/tick</white>"));
            player.sendMessage(MessageUtil.parse("<yellow>Status: </yellow><white>" + (flowing ? "<green>⚡ FLOWING</green>" : "<gray>IDLE</gray>") + "</white>"));
            player.sendMessage(MessageUtil.parse("<yellow>Connections: </yellow><white>" + node.getConnections().size() + "</white>"));

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

            return;
        }

        // =========================
        // JUNCTION
        // =========================
        if (type == Materials.WAXED_CHISELED_COPPER) {

            int transferred = node.getAndResetTransferred();
            player.sendMessage(MessageUtil.parse("<gold>Type: Junction</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>Transfer rate: </gold><white>" + transferred + " ⚡/tick</white>"));
            player.sendMessage(MessageUtil.parse("<gold>Connections: </gold><white>" + node.getConnections().size() + "</white>"));
            player.sendMessage(MessageUtil.parse("<gold>Mode: </gold><white>Omni-directional</white>"));
            return;
        }

        player.sendMessage(MessageUtil.parse("<gray>This block is not part of energy network.</gray>"));
    }

    // =========================
    // PDC CHECK
    // =========================
    private boolean is_multimeter(ItemStack item) {

        if (item == null || item.getType() == Material.AIR) return false;

        var meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer data =
                meta.getPersistentDataContainer();

        Byte val = data.get(KEY, PersistentDataType.BYTE);

        return val != null && val == (byte) 1;
    }
}