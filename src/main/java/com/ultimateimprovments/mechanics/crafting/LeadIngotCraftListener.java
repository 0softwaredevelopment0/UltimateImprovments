package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Crafter;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class LeadIngotCraftListener implements Listener {

    private static NamespacedKey RECIPE_KEY;

    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "lead_ingot");
        registerRecipe();
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe() {
        Main plugin = Main.getInstance();

        ItemStack result = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Lead Ingot *</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Used to craft a Lead Shield</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.LEAD_INGOT,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());
        recipe.shape(
                "III",
                "INI",
                "III"
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
    }

    // =========================
    // OVERRIDE RESULT — set PDC on the lead ingot
    // =========================
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;
        if (!AssemblerChecker.isAssemblerCraft(e)) return;

        CraftingInventory inv = e.getInventory();

        ItemStack result = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Lead Ingot *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Used to craft a Lead Shield</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.LEAD_INGOT,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        inv.setResult(result);
    }

    // =========================
    // UNCRAFT PROTECTION
    // If any ingredient has the isLeadIngot PDC and the recipe is NOT lead_ingot → block
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUncraftProtection(PrepareItemCraftEvent e) {
        // Skip our own recipe — it is legitimate
        Recipe recipe = e.getRecipe();
        if (recipe instanceof ShapedRecipe sr && sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null) return;

        // Check all matrix slots (may be a 2×2 craft too)
        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;
            if (ingredient.getType() != Material.NETHERITE_INGOT) continue;

            ItemMeta ingMeta = ingredient.getItemMeta();
            if (ingMeta == null) continue;

            if (ingMeta.getPersistentDataContainer().has(Keys.LEAD_INGOT, PersistentDataType.BYTE)) {
                // Found a lead ingot in an illegal recipe — block it
                inv.setResult(null);
                return;
            }
        }
    }

    // =========================
    // ADDITIONAL UNCRAFT PROTECTION VIA THE VANILLA CRAFTER
    // The vanilla Crafter on auto-craft (by redstone) does NOT fire
    // PrepareItemCraftEvent — only CrafterCraftEvent.
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent e) {
        // Check all Crafter matrix slots for the LEAD_INGOT PDC
        if (!(e.getBlock().getState() instanceof Crafter crafter)) return;
        Inventory inv = crafter.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() != Material.NETHERITE_INGOT) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (meta.getPersistentDataContainer().has(Keys.LEAD_INGOT, PersistentDataType.BYTE)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
