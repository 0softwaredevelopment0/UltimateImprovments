package com.mcplugin.crafting;

import com.mcplugin.Keys;
import com.mcplugin.Main;

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

import java.util.List;

public class LeadShieldCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "lead_shield"
        );

        // =========================
        // Удаляем старый датапаковский рецепт (minecraft:lead_shield),
        // чтобы не было конфликта с нашим плагиновым рецептом.
        // =========================
        Bukkit.removeRecipe(NamespacedKey.minecraft("lead_shield"));

        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {

        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.SHIELD);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§fСвинцовый щит");

        meta.setLore(List.of(
                "§7Защищает от радиации при держании в руке."
        ));

        // =========================
        // PDC via Keys (isLeadShield:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.LEAD_SHIELD,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);

        recipe.shape(
                "123",
                "456",
                "789"
        );

        recipe.setIngredient('1', Material.NETHERITE_INGOT);
        recipe.setIngredient('2', Material.NETHERITE_INGOT);
        recipe.setIngredient('3', Material.NETHERITE_INGOT);
        recipe.setIngredient('4', Material.NETHERITE_INGOT);
        recipe.setIngredient('5', Material.SHIELD);
        recipe.setIngredient('6', Material.NETHERITE_INGOT);
        recipe.setIngredient('7', Material.NETHERITE_INGOT);
        recipe.setIngredient('8', Material.NETHERITE_INGOT);
        recipe.setIngredient('9', Material.NETHERITE_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        plugin.getLogger().info("[LEAD_SHIELD] Recipe registered with Keys system");
    }

    // =========================
    // OVERRIDE RESULT
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {

        Recipe recipe = e.getRecipe();

        if (!(recipe instanceof ShapedRecipe sr)) return;

        if (!sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.SHIELD);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§fСвинцовый щит");

        meta.setLore(List.of(
                "§7Защищает от радиации при держании в руке."
        ));

        // =========================
        // PDC via Keys (isLeadShield:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.LEAD_SHIELD,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}
