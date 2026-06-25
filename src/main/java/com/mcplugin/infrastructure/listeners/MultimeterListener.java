package com.mcplugin.infrastructure.listeners;

import com.mcplugin.energy.generation.basic.GeneratorManager;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.transfer.cable.*;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.infrastructure.util.MessageUtil;
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

    @EventHandler
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
        // 🛠 ITEM ASSEMBLER (CRAFTER)
        // =========================
        if (type == Material.CRAFTER) {
            Location loc = LocationUtil.normalize(block.getLocation());
            if (EnergyWorkbenchManager.exists(loc)) {
                int buffer = EnergyWorkbenchManager.getBufferEnergy(loc);
                player.sendMessage(MessageUtil.parse("<gold>=== MULTIMETER ===</gold>"));
                player.sendMessage(MessageUtil.parse("<aqua>Type: Item Assembler</aqua>"));
                player.sendMessage(MessageUtil.parse("<aqua>Buffer: </aqua><white>" + buffer + "/100 ⚡</white>"));
                return;
            }
        }

        // =========================
        // ⚡ ГЕНЕРАТОР (BLAST_FURNACE)
        // =========================
        if (type == Material.BLAST_FURNACE) {
            boolean assembled = GeneratorManager.isAssembled(block.getLocation());
            player.sendMessage(MessageUtil.parse("<aqua>Type: Generator</aqua>"));
            player.sendMessage(MessageUtil.parse("<aqua>Assembled: </aqua><white>" + (assembled ? "yes" : "no") + "</white>"));

            if (block.getState() instanceof Furnace furnace) {
                ItemStack fuel = furnace.getInventory().getFuel();
                boolean hasFuel = fuel != null && !fuel.getType().isAir();
                player.sendMessage(MessageUtil.parse("<aqua>Fuel: </aqua><white>" + (hasFuel ? fuel.getType().name().toLowerCase().replace('_', ' ') + " x" + fuel.getAmount() : "none") + "</white>"));

                boolean isLit = false;
                if (block.getBlockData() instanceof org.bukkit.block.data.type.Furnace furnaceData) {
                    isLit = furnaceData.isLit();
                }
                player.sendMessage(MessageUtil.parse("<aqua>Burning: </aqua><white>" + (isLit ? "yes" : "no") + "</white>"));

                int cookTime = furnace.getCookTime();
                int cookTimeTotal = furnace.getCookTimeTotal();
                if (cookTimeTotal > 0) {
                    int progress = (cookTime * 100) / cookTimeTotal;
                    player.sendMessage(MessageUtil.parse("<aqua>Progress: </aqua><white>" + Math.min(progress, 100) + "%</white>"));
                }
            }

            // Redstone status
            boolean hasRedstone = block.isBlockPowered() || block.isBlockIndirectlyPowered();
            player.sendMessage(MessageUtil.parse("<aqua>Redstone: </aqua><white>" + (hasRedstone ? "yes" : "no") + "</white>"));

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
        if (type == Material.WAXED_COPPER_GRATE) {

            player.sendMessage(MessageUtil.parse("<aqua>Type: Battery</aqua>"));
            player.sendMessage(MessageUtil.parse("<aqua>Energy: </aqua><white>" + node.getEnergy() + "</white>"));
            player.sendMessage(MessageUtil.parse("<aqua>Connections: </aqua><white>" + node.getConnections().size() + "</white>"));
            return;
        }

        // =========================
        // CABLE
        // =========================
        if (type == Material.WAXED_LIGHTNING_ROD) {

            player.sendMessage(MessageUtil.parse("<yellow>Type: Cable</yellow>"));
            player.sendMessage(MessageUtil.parse("<yellow>Energy: </yellow><white>" + node.getEnergy() + "</white>"));
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
        if (type == Material.WAXED_CHISELED_COPPER) {

            player.sendMessage(MessageUtil.parse("<gold>Type: Junction</gold>"));
            player.sendMessage(MessageUtil.parse("<gold>Energy: </gold><white>" + node.getEnergy() + "</white>"));
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