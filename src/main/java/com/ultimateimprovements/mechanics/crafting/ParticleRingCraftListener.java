package com.ultimateimprovements.mechanics.crafting;

import com.ultimateimprovements.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import com.ultimateimprovements.mechanics.particle.ParticleAcceleratorManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ParticleRingCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "particle_ring");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        Bukkit.removeRecipe(RECIPE_KEY);

        ItemStack result = createRingItem();

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "TCT",
                "C C",
                "TCT"
        );
        recipe.setIngredient('T', Material.TUFF);
        recipe.setIngredient('C', Material.COPPER_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        ConsoleLogger.info("[ParticleRing] Recipe registered (Item Assembler only).");
    }

    public static ItemStack createRingItem() {
        ItemStack result = new ItemStack(ParticleAcceleratorManager.RING);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Particle Ring *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Guides particles along the accelerator path.</gray>")
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

        e.getInventory().setResult(createRingItem());
    }
}
