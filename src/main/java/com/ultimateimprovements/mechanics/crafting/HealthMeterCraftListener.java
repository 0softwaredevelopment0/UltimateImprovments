package com.ultimateimprovements.mechanics.crafting;

import com.ultimateimprovements.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovements.core.Keys;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.util.ConsoleLogger;
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

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "health_meter");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Health Meter *</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>RMB — check entity health</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.HEALTH_METER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "ILI",
                "LHL",
                "ILI"
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('L', Material.LAPIS_LAZULI);
        recipe.setIngredient('H', Material.HEART_OF_THE_SEA);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        ConsoleLogger.info("[HEALTHMETER] Recipe registered with Keys system");
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Health Meter *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>RMB — check entity health</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.HEALTH_METER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        inv.setResult(result);
    }
}
