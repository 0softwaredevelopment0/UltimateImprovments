package com.ultimateimprovements.mechanics.crafting;

import com.ultimateimprovements.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.MessageUtil;
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

public class StructureIntegrityCraftListener implements Listener {

    private static NamespacedKey KEY;
    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        KEY = new NamespacedKey(Main.getInstance(), "is_structure_integrity_indicator");
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "structure_integrity_indicator");
        registerRecipe();
    }

    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Structure Integrity Indicator*</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Shift+RMB on a structure to inspect</gray>"),
                MessageUtil.parse("<i:false><gray>its stress, degradation, and integrity.</gray>"),
                MessageUtil.parse(""),
                MessageUtil.parse("<i:false><dark_gray>Currently supports: Ender Chest</dark_gray>")
        ));

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                " I ",
                "SGS",
                " I "
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.STONE);
        recipe.setIngredient('G', Material.GLASS);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        ConsoleLogger.info("[STRUCTURE_INTEGRITY] Recipe registered.");
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

        meta.displayName(MessageUtil.parse("<i:false><white>Structure Integrity Indicator*</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Shift+RMB on a structure to inspect</gray>"),
                MessageUtil.parse("<i:false><gray>its stress, degradation, and integrity.</gray>"),
                MessageUtil.parse(""),
                MessageUtil.parse("<i:false><dark_gray>Currently supports: Ender Chest</dark_gray>")
        ));

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        result.setItemMeta(meta);
        inv.setResult(result);
    }

    public static NamespacedKey getKey() {
        return KEY;
    }
}
