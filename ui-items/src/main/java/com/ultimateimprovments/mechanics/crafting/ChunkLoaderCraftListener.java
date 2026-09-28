package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.features.world.ChunkLoaderItemListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;

/**
 * Chunk loader recipe — a very complex craft.
 * <pre>
 *   ╔═══════════╗
 *   ║ N ◆ D ◆ N ║
 *   ║ ◆ E ◆ E ◆ ║
 *   ║ N ◆ D ◆ N ║
 *   ╚═══════════╝
 *   N = Netherite block
 *   D = Diamond block
 *   E = Emerald block
 * </pre>
 */
public class ChunkLoaderCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "chunk_loader");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = ChunkLoaderItemListener.createChunkLoaderItem();

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());

        recipe.shape(
                "NBN",
                "BEB",
                "NBN"
        );

        recipe.setIngredient('N', Material.NETHERITE_SCRAP);
        recipe.setIngredient('B', Material.DIAMOND_BLOCK);
        recipe.setIngredient('E', Material.EMERALD_BLOCK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        inv.setResult(ChunkLoaderItemListener.createChunkLoaderItem());
    }
}
