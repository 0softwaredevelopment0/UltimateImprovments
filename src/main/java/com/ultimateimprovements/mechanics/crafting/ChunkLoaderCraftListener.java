package com.ultimateimprovements.mechanics.crafting;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.mechanics.features.world.ChunkLoaderItemListener;
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
 * Рецепт чанклоадера — очень сложный крафт.
 * <pre>
 *   ╔═══════════╗
 *   ║ N ◆ D ◆ N ║
 *   ║ ◆ E ◆ E ◆ ║
 *   ║ N ◆ D ◆ N ║
 *   ╚═══════════╝
 *   N = Незеритовый блок
 *   D = Алмазный блок
 *   E = Изумрудный блок
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

        recipe.setIngredient('N', Material.NETHERITE_BLOCK);
        recipe.setIngredient('B', Material.DIAMOND_BLOCK);
        recipe.setIngredient('E', Material.EMERALD_BLOCK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        ConsoleLogger.info("[CHUNK-LOADER] Recipe registered: 4 Netherite Block + 4 Diamond Block + 1 Emerald Block");
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
