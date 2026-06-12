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

public class HealthMeterCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "healthmeter"
        );

        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {

        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§fИзмеритель здоровья");

        meta.setLore(List.of(
                "§7Показывает количество здоровья ближайшего моба."
        ));

        meta.setItemModel(
                NamespacedKey.fromString("terf:tool/shrink_ray")
        );

        // =========================
        // PDC via Keys (isHpMeter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.HP_METER,
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

        recipe.setIngredient('1', Material.LIGHTNING_ROD);
        recipe.setIngredient('2', Material.ENDER_PEARL);
        recipe.setIngredient('3', Material.SPYGLASS);
        recipe.setIngredient('4', Material.RECOVERY_COMPASS);
        recipe.setIngredient('5', Material.ECHO_SHARD);
        recipe.setIngredient('6', Material.ENDER_PEARL);
        recipe.setIngredient('7', Material.ECHO_SHARD);
        recipe.setIngredient('8', Material.BREEZE_ROD);
        recipe.setIngredient('9', Material.BREEZE_ROD);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        plugin.getLogger().info("[HEALTHMETER] Recipe registered with Keys system");
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

        ItemStack result = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§fИзмеритель здоровья");

        meta.setLore(List.of(
                "§7Показывает количество здоровья ближайшего моба."
        ));

        meta.setItemModel(
                NamespacedKey.fromString("terf:tool/shrink_ray")
        );

        // =========================
        // PDC via Keys (isHpMeter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.HP_METER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}
