package com.mcplugin.mechanics.crafting;

import com.mcplugin.energy.machines.assembler.AssemblerChecker;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;

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

import org.bukkit.persistence.PersistentDataType;

public class MultimeterCraftListener implements Listener {

    private static NamespacedKey KEY;
    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        KEY = new NamespacedKey(Main.getInstance(), "is_multimeter");
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "multimeter");

        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {

        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.CLOCK);
        ItemMeta meta = result.getItemMeta();

        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Multimeter</white>"));

        // =========================
        // PDC TAG (isMultimeter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                KEY,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);

        recipe.shape(
                "IDI",
                "DCD",
                "IDI"
        );

        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('C', Material.CLOCK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        plugin.getLogger().info("[MULTIMETER] Recipe registered with ITEM MODEL");
    }

    // =========================
    // OVERRIDE RESULT
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {

        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;

        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.CLOCK);
        ItemMeta meta = result.getItemMeta();

        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Multimeter</white>"));

        // =========================
        // PDC AGAIN (isMultimeter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                KEY,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}