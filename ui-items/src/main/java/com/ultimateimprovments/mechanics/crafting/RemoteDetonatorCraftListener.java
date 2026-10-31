package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.items.RemoteDetonatorItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Recipe of the Remote Detonator.
 * <p>
 * Registered like every other custom item of this addon — through
 * {@link RecipeRegistry}, which makes the recipe Crafter-only.
 * <pre>
 *   G T G     G = gold ingot
 *   I R I     T = redstone torch
 *   I L I     R = redstone block
 *             L = lever
 *             I = iron ingot
 * </pre>
 */
public class RemoteDetonatorCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "remote_detonator");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, RemoteDetonatorItem.createItem());
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "GTG",
                "IRI",
                "ILI"
        );
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('T', Material.REDSTONE_TORCH);
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('L', Material.LEVER);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        // Crafter-only: the RecipeRegistry gate (HIGHEST) would clear the result
        // in a workbench anyway, but the preview must never show the item there
        // even for a single tick — so the result is set for the Crafter only.
        if (inv.getType() != InventoryType.CRAFTER) {
            inv.setResult(null);
            return;
        }
        inv.setResult(RemoteDetonatorItem.createItem());
    }
}
