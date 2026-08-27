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
 * Custom craft for the Electric Trident — a trident that strikes lightning
 * at whatever entity it hits.
 * <p>
 * Follows the standard custom-item pattern: the recipe is registered globally
 * (so it appears in the recipe book / crafting preview), the result carries the
 * {@code is_electric_trident} PDC, and the actual craft is finalized inside any
 * Crafter block (via {@link AssemblerChecker}). Max durability is raised to 512
 * via the {@code max_damage} component ({@link Damageable}).
 */
public class ElectricTridentCraftListener implements Listener {

    /** Max durability of the Electric Trident (vanilla trident = 250). */
    private static final int MAX_DURABILITY = 512;

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "electric_trident");
        registerRecipe();
    }

    // =========================
    // CREATE THE ELECTRIC TRIDENT ITEM
    // =========================
    private static ItemStack createTrident() {
        ItemStack result = new ItemStack(Material.TRIDENT);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Eletric trident</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>It shoots a bolt of lightning at the player it hits.</gray>")
        ));

        // =========================
        // PDC via Keys
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.ELECTRIC_TRIDENT,
                PersistentDataType.BYTE,
                (byte) 1
        );

        // =========================
        // Max durability 512
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

        ItemStack result = createTrident();

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());

        // Complex 3x3 trident-shaped recipe:
        //   L D L   — three prongs (lightning rods + netherite scrap)
        //   C R C   — shaft with a redstone core
        //     S     — stick handle
        recipe.shape(
                "LDL",
                "CRC",
                " S "
        );

        recipe.setIngredient('L', Material.LIGHTNING_ROD);
        recipe.setIngredient('D', Material.NETHERITE_SCRAP);
        recipe.setIngredient('C', Material.COPPER_INGOT);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('S', Material.STICK);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }

    // =========================
    // OVERRIDE RESULT — finalize the trident with PDC + durability
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();
        inv.setResult(createTrident());
    }
}
