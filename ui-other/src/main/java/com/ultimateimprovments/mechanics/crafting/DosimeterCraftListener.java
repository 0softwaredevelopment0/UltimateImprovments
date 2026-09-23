package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
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

/**
 * ⚠ Dosimeter — custom radiation-measuring item (clock base).
 * <p>
 * While held in the main hand OR off hand, shows an action bar:
 * {@code D: <dose> mSv  R: <rate> mSv/t}. Custom plugin recipe (medium
 * Custom plugin recipe (medium difficulty):
 * <pre>
 *   G S G      G = gold ingot (frame)
 *   R C R      S = Lead Ingot (sensor, custom item)
 *   G I G      R = redstone block (readout)
 *              C = clock (core)   I = iron ingot (base)
 * </pre>
 */
public class DosimeterCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "dosimeter");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createDosimeter());
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "GSG",
                "RCR",
                "GIG"
        );
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('C', Material.CLOCK);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        // Sensor: the custom Lead Ingot (ExactChoice — vanilla ingots don't match)
        recipe.setIngredient('S', new org.bukkit.inventory.RecipeChoice.ExactChoice(LeadShieldCraftListener.createLeadIngotStack()));
        recipe.setIngredient('I', Material.IRON_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }

    /** Builds the dosimeter item (clock base with PDC marker). */
    public static ItemStack createDosimeter() {
        ItemStack result = new ItemStack(Material.CLOCK);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Dosimeter *</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Measures absorbed dose (D) and"),
                MessageUtil.parse("<i:false><gray>current dose rate (R) while held.</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.DOSIMETER,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        return result;
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        e.getInventory().setResult(createDosimeter());
    }
}
