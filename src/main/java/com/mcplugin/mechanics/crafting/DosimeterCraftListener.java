package com.mcplugin.mechanics.crafting;

import com.mcplugin.infrastructure.core.Keys;
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

import java.util.List;

public class DosimeterCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "dosimeter"
        );

        // =========================
        // Удаляем старый датапаковский рецепт (minecraft:dosimeter),
        // чтобы не было конфликта с нашим плагиновым рецептом.
        // =========================
        Bukkit.removeRecipe(NamespacedKey.minecraft("dosimeter"));

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

        meta.displayName(MessageUtil.parse("<white>Дозиметр</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<gray>Показывает уровень радиации в Р/Ч</gray>"),
                MessageUtil.parse("<gray>при держании в руке.</gray>")
        ));

        // =========================
        // PDC via Keys (isDosimeter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.DOSIMETER,
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

        recipe.setIngredient('1', Material.COMPARATOR);
        recipe.setIngredient('2', Material.REDSTONE);
        recipe.setIngredient('3', Material.CLOCK);
        recipe.setIngredient('4', Material.COMPARATOR);
        recipe.setIngredient('5', Material.GOLD_INGOT);
        recipe.setIngredient('6', Material.REDSTONE_BLOCK);
        recipe.setIngredient('7', Material.BREEZE_ROD);
        recipe.setIngredient('8', Material.BREEZE_ROD);
        recipe.setIngredient('9', Material.REDSTONE_TORCH);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        plugin.getLogger().info("[DOSIMETER] Recipe registered with Keys system");
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

        ItemStack result = new ItemStack(Material.CLOCK);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<white>Дозиметр</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<gray>Показывает уровень радиации в Р/Ч</gray>"),
                MessageUtil.parse("<gray>при держании в руке.</gray>")
        ));

        // =========================
        // PDC via Keys (isDosimeter:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.DOSIMETER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}
