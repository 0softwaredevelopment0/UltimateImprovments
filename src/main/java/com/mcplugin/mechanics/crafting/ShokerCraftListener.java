package com.mcplugin.mechanics.crafting;

import com.mcplugin.energy.machines.assembler.AssemblerChecker;
import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public class ShokerCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "shoker"
        );

        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {

        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK);

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><aqua>Electro Shoker *</aqua>"));

        meta.lore(java.util.List.of(
                MessageUtil.parse("<i:false><gray>Stuns enemies with electricity.</gray>")
        ));

        // =========================
        // PDC via Keys
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.SHOCKER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());

        recipe.shape(
                "123",
                "456",
                "789"
        );

        recipe.setIngredient('1', Material.BLACK_CONCRETE);
        recipe.setIngredient('2', Material.YELLOW_CONCRETE);
        recipe.setIngredient('3', Material.BLAZE_ROD);

        recipe.setIngredient('4', Material.YELLOW_CONCRETE);
        recipe.setIngredient('5', Material.BLACK_CONCRETE);
        recipe.setIngredient('6', Material.BREEZE_ROD);

        recipe.setIngredient('7', Material.STICK);
        recipe.setIngredient('8', Material.NETHERITE_SCRAP);
        recipe.setIngredient('9', Material.NETHERITE_SCRAP);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        ConsoleLogger.info("[SHOCKER] Recipe registered with Keys system");
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

        ItemStack result = new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK);

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><aqua>Electro Shoker *</aqua>"));

        meta.lore(java.util.List.of(
                MessageUtil.parse("<i:false><gray>Stuns enemies with electricity.</gray>")
        ));

        // =========================
        // PDC via Keys
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.SHOCKER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}