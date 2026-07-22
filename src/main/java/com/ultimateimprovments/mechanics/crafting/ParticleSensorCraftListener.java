package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
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

public class ParticleSensorCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "particle_sensor");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        Bukkit.removeRecipe(RECIPE_KEY);

        ItemStack result = createSensorItem();

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "QDQ",
                "DCD",
                "QDQ"
        );
        recipe.setIngredient('Q', Material.QUARTZ);
        recipe.setIngredient('D', Material.DAYLIGHT_DETECTOR);
        recipe.setIngredient('C', Material.COMPARATOR);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        ConsoleLogger.info("[ParticleSensor] Recipe registered (Item Assembler only).");
    }

    public static ItemStack createSensorItem() {
        ItemStack result = new ItemStack(Material.POLISHED_DIORITE);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Particle Speed Sensor *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Measures particle speed (0-99.999% light speed).</gray>")
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

        e.getInventory().setResult(createSensorItem());
    }
}
