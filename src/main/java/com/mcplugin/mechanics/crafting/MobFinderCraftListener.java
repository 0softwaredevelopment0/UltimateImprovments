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

public class MobFinderCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "mob_finder");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Мобоискатель</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>ПКМ — сканировать чанк на мобов</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.MOB_FINDER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.shape(
                "BNB",
                "NIN",
                "BNB"
        );
        recipe.setIngredient('B', Material.BREEZE_ROD);
        recipe.setIngredient('N', Material.NETHERITE_SCRAP);
        recipe.setIngredient('I', Material.IRON_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        plugin.getLogger().info("[MOBFINDER] Recipe registered with Keys system");
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Мобоискатель</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>ПКМ — сканировать чанк на мобов</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.MOB_FINDER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        inv.setResult(result);
    }
}
