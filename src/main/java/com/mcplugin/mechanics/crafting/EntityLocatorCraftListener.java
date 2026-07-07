package com.mcplugin.mechanics.crafting;

import com.mcplugin.energy.machines.assembler.AssemblerChecker;
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

public class EntityLocatorCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "entity_locator"
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

        meta.displayName(MessageUtil.parse("<i:false><white>Entity Locator *</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Shows distance to nearest entity</gray>")
        ));

        // =========================
        // PDC via Keys (isLocator:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.LOCATOR,
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

        recipe.setIngredient('1', Material.NETHERITE_SCRAP);
        recipe.setIngredient('2', Material.REDSTONE_TORCH);
        recipe.setIngredient('3', Material.RECOVERY_COMPASS);
        recipe.setIngredient('4', Material.REDSTONE);
        recipe.setIngredient('5', Material.REDSTONE_BLOCK);
        recipe.setIngredient('6', Material.COMPARATOR);
        recipe.setIngredient('7', Material.BREEZE_ROD);
        recipe.setIngredient('8', Material.BREEZE_ROD);
        recipe.setIngredient('9', Material.TRIPWIRE_HOOK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        ConsoleLogger.info("[ENTITYLOCATOR] Recipe registered with Keys system");
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

        ItemStack result = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><i:false><white>Entity Locator *</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><i:false><gray>Shows distance to nearest entity</gray>")
        ));

        // =========================
        // PDC via Keys (isLocator:1b)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.LOCATOR,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}
