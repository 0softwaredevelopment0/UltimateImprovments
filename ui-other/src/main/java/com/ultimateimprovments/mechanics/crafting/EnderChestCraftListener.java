package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
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

import java.util.List;

/**
 * Ender chest crafting (Portable storage):
 * 8 netherite ingots + 1 Nether star → ender chest
 * <p>
 * Replaces the vanilla recipe (obsidian + eye of ender) and the removed datapack recipe.
 */
public class EnderChestCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "ender_chest");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        // Remove the vanilla ender chest recipe (8 obsidian + eye of ender)
        NamespacedKey vanillaKey = NamespacedKey.fromString("minecraft:ender_chest");
        if (vanillaKey != null) {
            Bukkit.removeRecipe(vanillaKey);
        }

        // Remove the old plugin recipe if any
        Bukkit.removeRecipe(RECIPE_KEY);

        ItemStack result = createEnderChestItem();

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "111",
                "121",
                "111"
        );
        recipe.setIngredient('1', Material.NETHERITE_INGOT);
        recipe.setIngredient('2', Material.NETHER_STAR);

        plugin.getServer().addRecipe(recipe);
    }

    /**
     * Creates the ender chest ItemStack with a custom name and lore.
     */
    private static ItemStack createEnderChestItem() {
        ItemStack result = new ItemStack(Material.ENDER_CHEST);
        var meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Портативное хранилище</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Поставьте и сломайте чтобы прочитать описание.</gray>")
        ));

        result.setItemMeta(meta);
        return result;
    }

    /**
     * On grid crafting — replaces the result with the custom ender chest.
     * Needed because PrepareItemCraftEvent may show an item without PDC/lore.
     */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        inv.setResult(createEnderChestItem());
    }
}
