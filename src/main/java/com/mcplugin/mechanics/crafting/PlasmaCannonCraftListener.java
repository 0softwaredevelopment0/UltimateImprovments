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
import com.mcplugin.infrastructure.util.MessageUtil;

import java.util.List;

public class PlasmaCannonCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {

        RECIPE_KEY = new NamespacedKey(
                Main.getInstance(),
                "plasma_cannon"
        );

        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {

        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<white>Photon cannon</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<gray>Shoots with echo_shard.</gray>")
        ));

        // =========================
        // PDC via Keys (FIXED)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.PLASMA,
                org.bukkit.persistence.PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);

        recipe.shape(
                "ABC",
                "DEF",
                "GHA"
        );

        recipe.setIngredient('A', Material.PURPUR_BLOCK);
        recipe.setIngredient('B', Material.GLASS_PANE);
        recipe.setIngredient('C', Material.NETHER_STAR);

        recipe.setIngredient('D', Material.ECHO_SHARD);
        recipe.setIngredient('E', Material.HEART_OF_THE_SEA);
        recipe.setIngredient('F', Material.GLASS_PANE);

        recipe.setIngredient('G', Material.BREEZE_ROD);
        recipe.setIngredient('H', Material.ECHO_SHARD);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);

        plugin.getLogger().info("[PHOTONCANNON] Recipe registered (Keys system)");
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

        ItemStack result = new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<white>Photon cannon</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<gray>Shoots with echo_shard.</gray>")
        ));

        // =========================
        // PDC via Keys (FIXED)
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.PLASMA,
                org.bukkit.persistence.PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        inv.setResult(result);
    }
}