package com.mcplugin.mechanics.crafting;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;

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

public class ConcreteBucketCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "concrete_bucket");
        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.WATER_BUCKET);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Concrete Bucket *</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Water that hardens into concrete after 60 seconds</gray>"),
                MessageUtil.parse("<i:false><gray>Creates gray-tinted water (Pale Garden biome)</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.CONCRETE_BUCKET,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        // ════════════════════════════════════════
        // 🧊 RECIPE: GRAVEL (×8) + WATER_BUCKET = CONCRETE BUCKET
        // ════════════════════════════════════════
        // Recipe shape:
        //   G G G
        //   G B G
        //   G G G
        // G = GRAVEL, B = WATER_BUCKET
        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup("CB");
        recipe.shape(
                "GGG",
                "GBG",
                "GGG"
        );
        recipe.setIngredient('G', Material.GRAVEL);
        recipe.setIngredient('B', Material.WATER_BUCKET);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        ConsoleLogger.info("[ConcreteBucket] Recipe registered (8 Gravel + Water Bucket)");
    }

    // =========================
    // OVERRIDE RESULT — ставим PDC на результат крафта
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        ItemStack result = new ItemStack(Material.WATER_BUCKET);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Concrete Bucket *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Water that hardens into concrete after 60 seconds</gray>"),
                MessageUtil.parse("<i:false><gray>Creates gray-tinted water (Pale Garden biome)</gray>")
        ));
        meta.getPersistentDataContainer().set(
                Keys.CONCRETE_BUCKET,
                PersistentDataType.BYTE,
                (byte) 1
        );
        result.setItemMeta(meta);
        e.getInventory().setResult(result);
    }

    // =========================
    // ЗАЩИТА ОТ ИСПОЛЬЗОВАНИЯ В ДРУГИХ РЕЦЕПТАХ
    // =========================
    @EventHandler
    public void onUncraftProtection(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (recipe instanceof ShapedRecipe sr && sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null) return;

        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;
            if (ingredient.getType() != Material.WATER_BUCKET) continue;

            ItemMeta ingMeta = ingredient.getItemMeta();
            if (ingMeta == null) continue;

            if (ingMeta.getPersistentDataContainer().has(Keys.CONCRETE_BUCKET, PersistentDataType.BYTE)) {
                inv.setResult(null);
                return;
            }
        }
    }
}
