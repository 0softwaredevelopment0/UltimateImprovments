package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.energy.machines.assembler.AssemblerChecker;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Custom craft for the Blazing Sword — a golden sword that deals extra lava
 * damage and ignites its target.
 * <p>
 * Follows the standard custom-item pattern: the recipe is registered globally
 * (so it appears in the recipe book / crafting preview), the result carries the
 * {@code is_blazing_sword} PDC, and the actual craft is finalized only inside
 * the Item Assembler (Crafter). Max durability is raised to 1024 via the
 * {@code max_damage} component ({@link Damageable}).
 */
public class BlazingSwordCraftListener implements Listener {

    /** Max durability of the Blazing Sword (vanilla golden sword = 32). */
    private static final int MAX_DURABILITY = 1024;

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "blazing_sword");
        registerRecipe();
    }

    // =========================
    // CREATE THE BLAZING SWORD ITEM
    // =========================
    private static ItemStack createSword() {
        ItemStack result = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Blazing sword</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Deals additional damage as if from lava.</gray>")
        ));

        // =========================
        // PDC via Keys
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.BLAZING_SWORD,
                PersistentDataType.BYTE,
                (byte) 1
        );

        // =========================
        // Max durability 1024
        // =========================
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(MAX_DURABILITY);
            damageable.setDamage(0);
        }

        result.setItemMeta(meta);
        return result;
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = createSword();

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());

        // Complex 3x3 sword-shaped recipe: netherite scraps + gold ingots + stick.
        recipe.shape(
                "BGB",
                "GGG",
                "BSB"
        );

        recipe.setIngredient('B', Material.NETHERITE_SCRAP);
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('S', Material.STICK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }

    // =========================
    // OVERRIDE RESULT — finalize the sword with PDC + durability
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();
        inv.setResult(createSword());
    }
}
