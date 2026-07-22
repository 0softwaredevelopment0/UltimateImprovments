package com.ultimateimprovements.mechanics.crafting;

import com.ultimateimprovements.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.Materials;
import com.ultimateimprovements.util.MessageUtil;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ParticleEngineCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "particle_engine");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        Bukkit.removeRecipe(RECIPE_KEY);

        ItemStack result = createEngineItem();

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "IRI",
                "RBR",
                "IRI"
        );
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('B', Materials.BLAST_FURNACE);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        ConsoleLogger.info("[ParticleEngine] Recipe registered (Item Assembler only).");
    }

    public static ItemStack createEngineItem() {
        ItemStack result = new ItemStack(Material.TUFF_BRICKS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Particle Engine *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Accelerates particles. Requires 500⚡ buffer.</gray>"),
                MessageUtil.parse("<i:false><gray>Connect to cables to charge.</gray>")
        ));

        result.setItemMeta(meta);
        return result;
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        e.getInventory().setResult(createEngineItem());
    }
}
