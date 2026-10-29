package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.world.ConcreteBucketManager;
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

public class ConcreteBucketCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "concrete_bucket");
        registerRecipe();
    }

    // =========================
    // ITEM
    // =========================
    /**
     * Builds the concrete bucket item. The hardening delay is read from
     * {@code concrete_bucket.harden_delay_seconds} (see {@link ConcreteBucketManager}),
     * so the lore always matches the configured timing.
     */
    private static ItemStack buildBucketItem() {
        ItemStack item = new ItemStack(Material.WATER_BUCKET);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parse("<i:false><white>Concrete Bucket *</white>"));

            meta.lore(List.of(
                    MessageUtil.parse("<i:false><gray>Water that hardens into concrete after "
                            + ConcreteBucketManager.delaySeconds() + " seconds</gray>"),
                    MessageUtil.parse("<i:false><gray>Every block it flows into hardens too</gray>")
            ));

            meta.getPersistentDataContainer().set(
                    Keys.CONCRETE_BUCKET,
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        });
        return item;
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        Bukkit.removeRecipe(RECIPE_KEY);

        // ════════════════════════════════════════
        // 🧊 RECIPE: CLAY · SMOOTH_BASALT · TUFF
        //              BLACKSTONE · WATER_BUCKET · RED_SAND
        //              GRINDSTONE · SOUL_SAND · PACKED_MUD
        // ════════════════════════════════════════
        // Recipe shape:
        //   C S T
        //   B W R
        //   G D M
        // C = CLAY (block of clay), S = SMOOTH_BASALT, T = TUFF,
        // B = BLACKSTONE, W = WATER_BUCKET, R = RED_SAND,
        // G = GRINDSTONE, D = SOUL_SAND, M = PACKED_MUD
        // Crafter-only: RecipeRegistry.registerRecipe() below gates the result
        // outside InventoryType.CRAFTER (and blocks the final CraftItemEvent).
        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, buildBucketItem());
        recipe.setGroup("CB");
        recipe.shape(
                "CST",
                "BWR",
                "GDM"
        );
        recipe.setIngredient('C', Material.CLAY);
        recipe.setIngredient('S', Material.SMOOTH_BASALT);
        recipe.setIngredient('T', Material.TUFF);
        recipe.setIngredient('B', Material.BLACKSTONE);
        recipe.setIngredient('W', Material.WATER_BUCKET);
        recipe.setIngredient('R', Material.RED_SAND);
        recipe.setIngredient('G', Material.GRINDSTONE);
        recipe.setIngredient('D', Material.SOUL_SAND);
        recipe.setIngredient('M', Material.PACKED_MUD);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }

    // =========================
    // OVERRIDE RESULT — put PDC on the craft result
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        e.getInventory().setResult(buildBucketItem());
    }

    // =========================
    // PROTECTION FROM USE IN OTHER RECIPES
    // =========================
    @EventHandler
    public void onUncraftProtection(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (recipe instanceof ShapedRecipe sr && sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null) return;

        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;
            if (ingredient.getType() != Material.WATER_BUCKET) continue;

            ItemMeta ingMeta = ingredient.getItemMeta();
            if (ingMeta == null) continue;

            if (ingMeta.getPersistentDataContainer().has(Keys.CONCRETE_BUCKET, PersistentDataType.BYTE)) {
                inv.setResult(null);
                return;
            }
        }
    }
}
