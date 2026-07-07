package com.mcplugin.energy.machines.workbench;

import com.mcplugin.core.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.mechanics.crafting.RecipeRegistry;
import com.mcplugin.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

public class EnergyCraftingListener implements Listener {

    private static final Component ASSEMBLER_TITLE = Component.text("Item assembler");

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
        // ONLY ITEM ASSEMBLER (CRAFTER)
        // =========================
        if (e.getInventory().getType() != InventoryType.CRAFTER) {
            return;
        }

        if (!ASSEMBLER_TITLE.equals(e.getView().title())) {
            return;
        }

        Location workbench = findWorkbench(player);

        if (workbench == null) {
            e.getInventory().setResult(null);
            return;
        }

        int cost = getCost();

        if (!EnergyWorkbenchManager.hasBufferEnergy(workbench, cost)) {
            e.getInventory().setResult(null);
        }
    }

    // =========================
    // БЛОКИРОВКА КАСТОМНЫХ РЕЦЕПТОВ ВНЕ ITEM ASSEMBLER
    // Блокирует PrepareItemCraftEvent для кастомных рецептов
    // в обычном верстаке (WORKBENCH), ванильном Crafter и 2x2 крафте.
    // =========================
    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareCraftBlockOutside(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;

        NamespacedKey recipeKey = keyed.getKey();
        if (!RecipeRegistry.getCustomRecipes().contains(recipeKey)) return;

        // Разрешаем только в Item Assembler GUI (CRAFTER + "Item assembler" title)
        boolean isAssembler = (e.getInventory().getType() == InventoryType.CRAFTER)
                && ASSEMBLER_TITLE.equals(e.getView().title());

        if (!isAssembler) {
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
        // ONLY ITEM ASSEMBLER (CRAFTER)
        // =========================
        boolean inAssembler = (e.getInventory().getType() == InventoryType.CRAFTER)
                && ASSEMBLER_TITLE.equals(e.getView().title());

        if (inAssembler) {
            // Energy-based crafting in the Item Assembler
            Location workbench = findWorkbench(player);

            if (workbench == null) {
                e.setCancelled(true);
                player.sendMessage(MessageUtil.parse(getMsg()));
                return;
            }

            int cost = getCost();

            if (!EnergyWorkbenchManager.hasBufferEnergy(workbench, cost)) {
                e.setCancelled(true);
                player.sendMessage(MessageUtil.parse(getMsg()));
                return;
            }

            EnergyWorkbenchManager.consumeBufferEnergy(workbench, cost);
        } else {
            // =========================
            // Не в Assembler — блокируем кастомные рецепты
            // =========================
            Recipe recipe = e.getRecipe();
            if (recipe instanceof Keyed keyed) {
                NamespacedKey recipeKey = keyed.getKey();
                if (RecipeRegistry.getCustomRecipes().contains(recipeKey)) {
                    e.setCancelled(true);
                    player.sendMessage(MessageUtil.parse("<gold>✧</gold> <gray>This item can only be crafted in the</gray> <aqua>Item Assembler</aqua><gray>!</gray>"));
                }
            }
        }
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

                    return loc;
                }
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