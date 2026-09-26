package com.ultimateimprovments.mechanics.crafting;

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
    // CREATE LEAD INGOT ITEMSTACK (for ExactChoice in dependent recipes:
    // Hazmat suit pieces)
    // =========================
    public static ItemStack createLeadIngotStack() {
        ItemStack ingot = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta ingotMeta = ingot.getItemMeta();
        if (ingotMeta == null) return ingot;
        ingotMeta.displayName(MessageUtil.parse("<i:false><white>Lead Ingot *</white>"));
        ingotMeta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Used to craft the Hazmat suit.</gray>")
        ));
        ingotMeta.getPersistentDataContainer().set(
                Keys.LEAD_INGOT,
                PersistentDataType.BYTE,
                (byte) 1
        );
        ingot.setItemMeta(ingotMeta);
        return ingot;
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
                MessageUtil.parse("<i:false><gray>Used to craft the Hazmat suit.</gray>")
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

        CraftingInventory inv = e.getInventory();

        // Defense in depth: verify the matrix actually matches the full
        // pattern (8 iron ring + 1 netherite center, nothing missing).
        // PrepareItemCraftEvent only fires for full matches, but a malicious
        // client or another plugin could present a stale/partial grid —
        // never hand out the result for anything but the exact layout.
        if (!isFullLeadIngotMatrix(inv.getMatrix())) {
            inv.setResult(null);
            return;
        }

        ItemStack result = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.displayName(MessageUtil.parse("<i:false><white>Lead Ingot *</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Used to craft the Hazmat suit.</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.LEAD_INGOT,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        inv.setResult(result);
    }

    /**
     * True when the matrix is exactly the lead-ingot pattern: netherite ingot
     * in the center, one iron ingot in each of the 8 ring slots, no extra items.
     */
    private static boolean isFullLeadIngotMatrix(ItemStack[] matrix) {
        if (matrix == null || matrix.length < 9) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack item = matrix[i];
            boolean isCenter = (i == 4);
            if (item == null || item.getType() == Material.AIR) return false;
            Material expected = isCenter ? Material.NETHERITE_INGOT : Material.IRON_INGOT;
            if (item.getType() != expected) return false;
        }
        return true;
    }

    // =========================
    // UNCRAFT PROTECTION
    // If any ingredient has the isLeadIngot PDC and the recipe does NOT
    // legitimately consume lead ingots → block (uncrafting a lead ingot into
    // its netherite value must be impossible).
    // LEGITIMATE consumers are listed in LEAD_INGOT_CONSUMERS — recipes that
    // use the Lead Ingot as a designed ingredient (the Hazmat suit pieces).
    // =========================
    /**
     * Recipe keys that may consume a Lead Ingot without being blocked:
     * the four Hazmat suit pieces.
     */
    private static final java.util.Set<String> LEAD_INGOT_CONSUMERS = java.util.Set.of(
            "hazmat_helmet",
            "hazmat_chestplate",
            "hazmat_leggings",
            "hazmat_boots"
    );

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUncraftProtection(PrepareItemCraftEvent e) {
        // Skip our own recipe — it is legitimate
        Recipe recipe = e.getRecipe();
        if (recipe instanceof ShapedRecipe sr) {
            if (sr.getKey().equals(RECIPE_KEY)) return;
            if (LEAD_INGOT_CONSUMERS.contains(sr.getKey().getKey())) return;
        }

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
        // Legitimate consumers are allowed to auto-craft in the Crafter
        Recipe recipe = e.getRecipe();
        if (recipe instanceof org.bukkit.Keyed keyed
                && LEAD_INGOT_CONSUMERS.contains(keyed.getKey().getKey())) {
            return;
        }

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
