package com.mcplugin.mechanics.crafting;

import com.mcplugin.energy.machines.assembler.AssemblerChecker;
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

public class PortableRadarCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "portable_radar");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Портативный радар</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>ПКМ — найти ближайшую сущность (64 блока)</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.RADAR,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.shape(
                "NEN",
                "EBE",
                "NEN"
        );
        recipe.setIngredient('N', Material.NETHERITE_SCRAP);
        recipe.setIngredient('E', Material.ENDER_EYE);
        recipe.setIngredient('B', Material.REDSTONE_BLOCK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        plugin.getLogger().info("[RADAR] Recipe registered with Keys system");
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Портативный радар</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>ПКМ — найти ближайшую сущность (64 блока)</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.RADAR,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        inv.setResult(result);
    }
}
