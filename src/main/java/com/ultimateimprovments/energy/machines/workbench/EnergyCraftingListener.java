package com.ultimateimprovments.energy.machines.workbench;

import com.ultimateimprovments.mechanics.crafting.RecipeRegistry;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

/**
 * Gates custom recipes.
 * <p>
 * The old "Item Assembler" structure (assembled Crafter + energy buffer) was
 * removed. Custom recipes now craft directly in any vanilla Crafter block,
 * while the regular workbench / 2x2 grid only shows the recipe book preview
 * (the result slot is cleared, so nothing can be crafted there).
 */
public class EnergyCraftingListener implements Listener {

    // =========================
    // PREVIEW — block custom recipes outside the Crafter block
    // (workbench & 2x2 keep the recipe book preview, but the result is null)
    // =========================
    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareCraftBlockOutside(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;

        NamespacedKey recipeKey = keyed.getKey();
        if (!RecipeRegistry.getCustomRecipes().contains(recipeKey)) return;

        // Custom recipes craft in the vanilla Crafter block — everywhere else
        // (WORKBENCH, CRAFTING 2x2) the result is cleared (preview only).
        if (e.getInventory().getType() != InventoryType.CRAFTER) {
            e.getInventory().setResult(null);
        }
    }

    // =========================
    // FINAL CRAFT
    // =========================
    @EventHandler
    public void onCraft(CraftItemEvent e) {
        boolean inCrafter = e.getInventory().getType() == InventoryType.CRAFTER;
        if (inCrafter) {
            // Allowed — vanilla Crafter block
            return;
        }

        // Block custom recipes outside the Crafter block
        Recipe recipe = e.getRecipe();
        if (recipe instanceof Keyed keyed) {
            NamespacedKey recipeKey = keyed.getKey();
            if (RecipeRegistry.getCustomRecipes().contains(recipeKey)) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player player) {
                    player.sendMessage(MessageUtil.parse(
                            "<gold>✧</gold> <gray>This item can only be crafted in a</gray> <aqua>Crafter</aqua><gray>!</gray>"));
                }
            }
        }
    }
}
