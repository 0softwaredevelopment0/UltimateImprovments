package com.mcplugin.crafting;

import com.mcplugin.Keys;
import com.mcplugin.Main;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class AntimatterCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "antimatter"
        );

        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {

        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§fКолба с антиматерией");

        meta.setLore(List.of(
                "§7При броске создаёт мощный взрыв."
        ));

        meta.setColor(Color.fromRGB(197379));
        meta.clearCustomEffects();

        // =========================
        // PDC via Keys (isAntimatter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.ANTIMATTER,
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

        recipe.setIngredient('1', Material.NETHERITE_SCRAP);
        recipe.setIngredient('2', Material.NETHERITE_SCRAP);
        recipe.setIngredient('3', Material.NETHERITE_SCRAP);
        recipe.setIngredient('4', Material.NETHERITE_SCRAP);
        recipe.setIngredient('5', Material.GLASS_BOTTLE);
        recipe.setIngredient('6', Material.NETHERITE_SCRAP);
        recipe.setIngredient('7', Material.NETHERITE_SCRAP);
        recipe.setIngredient('8', Material.NETHERITE_SCRAP);
        recipe.setIngredient('9', Material.NETHERITE_SCRAP);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        plugin.getLogger().info("[ANTIMATTER] Recipe registered with Keys system");
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

        ItemStack result = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§fКолба с антиматерией");

        meta.setLore(List.of(
                "§7При броске создаёт мощный взрыв."
        ));

        meta.setColor(Color.fromRGB(197379));
        meta.clearCustomEffects();

        // =========================
        // PDC via Keys (isAntimatter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.ANTIMATTER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}
