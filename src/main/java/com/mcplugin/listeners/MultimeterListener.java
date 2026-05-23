package com.mcplugin.listeners;

import com.mcplugin.Main;
import com.mcplugin.cable.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

        CableNode node = CableNetwork.getNode(block.getLocation());

        player.sendMessage(ChatColor.GOLD + "=== MULTIMETER ===");

        if (node == null) {
            player.sendMessage(ChatColor.GRAY + "No energy node at this block.");
            return;
        }

        Material type = block.getType();

        // =========================
        // BATTERY
        // =========================
        if (type == Material.WAXED_COPPER_GRATE) {

            player.sendMessage(ChatColor.AQUA + "Type: Battery");
            player.sendMessage(ChatColor.AQUA + "Energy: " + ChatColor.WHITE + node.getEnergy());
            player.sendMessage(ChatColor.AQUA + "Connections: " + ChatColor.WHITE + node.getConnections().size());
            return;
        }

        // =========================
        // CABLE
        // =========================
        if (type == Material.WAXED_LIGHTNING_ROD) {

            player.sendMessage(ChatColor.YELLOW + "Type: Cable");
            player.sendMessage(ChatColor.YELLOW + "Energy: " + ChatColor.WHITE + node.getEnergy());
            player.sendMessage(ChatColor.YELLOW + "Connections: " + ChatColor.WHITE + node.getConnections().size());

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

                player.sendMessage(ChatColor.YELLOW + "Axis: " + ChatColor.WHITE + axis);
            }

            return;
        }

        // =========================
        // JUNCTION
        // =========================
        if (type == Material.WAXED_CHISELED_COPPER) {

            player.sendMessage(ChatColor.GOLD + "Type: Junction");
            player.sendMessage(ChatColor.GOLD + "Energy: " + ChatColor.WHITE + node.getEnergy());
            player.sendMessage(ChatColor.GOLD + "Connections: " + ChatColor.WHITE + node.getConnections().size());
            player.sendMessage(ChatColor.GOLD + "Mode: " + ChatColor.WHITE + "Omni-directional");
            return;
        }

        player.sendMessage(ChatColor.GRAY + "This block is not part of energy network.");
    }

    // =========================
    // PDC CHECK
    // =========================
    private boolean is_multimeter(ItemStack item) {

        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer data =
                item.getItemMeta().getPersistentDataContainer();

        Byte val = data.get(KEY, PersistentDataType.BYTE);

        return val != null && val == (byte) 1;
    }
}