package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Heavy Core — plugin-side recipe (moved out of the datapack).
 * <p>
 * Uses the <b>same key</b> as the old datapack recipe ({@code ui:heavy_core}),
 * so the datapack version is removed and replaced by this one, which goes
 * through the {@link RecipeRegistry} Crafter-only gate.
 * <pre>
 *   S I S      S = netherite scrap
 *   I N I      I = netherite ingot
 *   S I S      N = nether star
 * </pre>
 * Why it matters: the datapack recipe was NOT registered in
 * {@link RecipeRegistry}, so it was craftable in a regular workbench —
 * combined with client recipe-book packets this allowed partial-grid abuse
 * ("check the craft, place 1 ingot, take 4"). Crafter-only gating plus the
 * recipe being plugin-side closes that hole.
 */
public class HeavyCoreCraftListener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        // Same namespace+key as the datapack recipe → removeRecipe wipes it
        RECIPE_KEY = NamespacedKey.fromString("ui:heavy_core");
        registerRecipe();
    }

    private static void registerRecipe() {
        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, new ItemStack(Material.HEAVY_CORE));
        recipe.setGroup("heavy_core");
        recipe.shape(
                "SIS",
                "INI",
                "SIS"
        );
        recipe.setIngredient('S', Material.NETHERITE_SCRAP);
        recipe.setIngredient('I', Material.NETHERITE_INGOT);
        recipe.setIngredient('N', Material.NETHER_STAR);

        Bukkit.addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }
}
