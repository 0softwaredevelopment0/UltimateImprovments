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

public class OreFinderCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "ore_finder");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.COMPASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Рудоискатель</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>ПКМ — сканировать чанк на руды</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.ORE_FINDER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.shape(
                "IRI",
                "DGD",
                "IRI"
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('G', Material.GOLD_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        plugin.getLogger().info("[OREFINDER] Recipe registered with Keys system");
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.COMPASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Рудоискатель</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>ПКМ — сканировать чанк на руды</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.ORE_FINDER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        inv.setResult(result);
    }
}
