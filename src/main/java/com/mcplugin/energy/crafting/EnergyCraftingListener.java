package com.mcplugin.energy.crafting;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class EnergyCraftingListener implements Listener {

    // =========================
    // PREVIEW
    // =========================
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {

        if (!(e.getView().getPlayer() instanceof Player player)) {
            return;
        }

        if (!isEnabled()) {
            return;
        }

        // =========================
        // ONLY REAL WORKBENCH
        // =========================
        if (e.getInventory().getType() != InventoryType.WORKBENCH) {
            return;
        }

        Location workbench = findWorkbench(player);

        if (workbench == null) {
            e.getInventory().setResult(null);
            return;
        }

        int cost = getCost();

        if (!hasNetworkEnergy(workbench, cost)) {
            e.getInventory().setResult(null);
        }
    }

    // =========================
    // FINAL CRAFT
    // =========================
    @EventHandler
    public void onCraft(CraftItemEvent e) {

        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!isEnabled()) {
            return;
        }

        // =========================
        // ONLY REAL WORKBENCH
        // =========================
        if (e.getInventory().getType() != InventoryType.WORKBENCH) {
            return;
        }

        Location workbench = findWorkbench(player);

        if (workbench == null) {

            e.setCancelled(true);

            player.sendMessage(MessageUtil.parse(getMsg()));

            return;
        }

        int cost = getCost();

        if (!hasNetworkEnergy(workbench, cost)) {

            e.setCancelled(true);

            player.sendMessage(MessageUtil.parse(getMsg()));

            return;
        }

        takeNetworkEnergy(workbench, cost);
    }

    // =========================
    // FIND WORKBENCH
    // =========================
    private Location findWorkbench(Player player) {

        Location base = player.getLocation();

        int radius = Main.getInstance()
                .getConfig()
                .getInt(
                        "energy_crafting.workbench_search_radius",
                        3
                );

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    Block block =
                            base.clone()
                                    .add(x, y, z)
                                    .getBlock();

                    Location loc =
                            LocationUtil.normalize(
                                    block.getLocation()
                            );

                    if (!EnergyWorkbenchManager.exists(loc)) {
                        continue;
                    }

                    if (!isConnectedToNetwork(loc)) {
                        continue;
                    }

                    return loc;
                }
            }
        }

        return null;
    }

    // =========================
    // NETWORK CHECK
    // =========================
    private boolean isConnectedToNetwork(Location workbench) {

        for (Location near : LocationUtil.getNeighbors(workbench)) {

            Location n = LocationUtil.normalize(near);

            if (CableNetwork.getNode(n) != null) {
                return true;
            }
        }

        return false;
    }

    // =========================
    // ENERGY CHECK
    // =========================
    private boolean hasNetworkEnergy(Location workbench, int amount) {

        CableNode start = findConnectedNode(workbench);

        if (start == null) {
            return false;
        }

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start.getLocation());

        int total = 0;

        while (!queue.isEmpty()) {

            CableNode node = queue.poll();

            if (node == null) {
                continue;
            }

            total += node.getEnergy();

            for (Location loc : node.getConnections()) {

                if (visited.contains(loc)) {
                    continue;
                }

                CableNode next =
                        CableNetwork.getNode(loc);

                if (next == null) {
                    continue;
                }

                visited.add(loc);
                queue.add(next);
            }
        }

        return total >= amount;
    }

    // =========================
    // ENERGY CONSUME
    // =========================
    private void takeNetworkEnergy(Location workbench, int amount) {

        CableNode start = findConnectedNode(workbench);

        if (start == null) {
            return;
        }

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start.getLocation());

        int remaining = amount;

        while (!queue.isEmpty() && remaining > 0) {

            CableNode node = queue.poll();

            if (node == null) {
                continue;
            }

            int energy = node.getEnergy();

            if (energy > 0) {

                int take = Math.min(
                        energy,
                        remaining
                );

                node.setEnergy(energy - take);

                remaining -= take;
            }

            for (Location loc : node.getConnections()) {

                if (visited.contains(loc)) {
                    continue;
                }

                CableNode next =
                        CableNetwork.getNode(loc);

                if (next == null) {
                    continue;
                }

                visited.add(loc);
                queue.add(next);
            }
        }
    }

    // =========================
    // FIND START NODE
    // =========================
    private CableNode findConnectedNode(Location workbench) {

        for (Location near : LocationUtil.getNeighbors(workbench)) {

            Location norm =
                    LocationUtil.normalize(near);

            CableNode node =
                    CableNetwork.getNode(norm);

            if (node != null) {
                return node;
            }
        }

        return null;
    }

    // =========================
    // CONFIG
    // =========================
    private boolean isEnabled() {

        return Main.getInstance()
                .getConfig()
                .getBoolean(
                        "energy_crafting.enabled",
                        true
                );
    }

    private int getCost() {

        return Main.getInstance()
                .getConfig()
                .getInt(
                        "energy_crafting.energy_per_craft",
                        100
                );
    }

    private String getMsg() {
        return MessagesManager.getString(
                "energy_crafting.messages.no_energy",
                "<dark_red>❌</dark_red> <red>Error: <gray>Not enough energy for craft!</gray></red>"
        );
    }
}