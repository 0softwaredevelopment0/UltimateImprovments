package com.mcplugin.mechanics.crafting;

import com.mcplugin.core.Main;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.ConsoleLogger;
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
 * Крафт эндер-сундука (Портативное хранилище):
 * 8 незеритовых слитков + 1 звезда Нижнего мира → эндер-сундук
 * <p>
 * Заменяет ванильный рецепт (обсидиан + око Эндера) и удалённый datapack-рецепт.
 */
public class EnderChestCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "ender_chest");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        // Удаляем ванильный рецепт эндер-сундука (8 обсидиана + око Эндера)
        NamespacedKey vanillaKey = NamespacedKey.fromString("minecraft:ender_chest");
        if (vanillaKey != null) {
            Bukkit.removeRecipe(vanillaKey);
        }

        // Удаляем старый рецепт плагина, если был
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
        ConsoleLogger.info("[ENDERCHEST] Ender chest recipe registered (replaced vanilla).");
    }

    /**
     * Создаёт ItemStack эндер-сундука с кастомным именем и лором.
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
     * При крафте в сетке — подменяет результат на кастомный эндер-сундук.
     * Это нужно, так как PrepareItemCraftEvent может показать предмет без PDC/лора.
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
