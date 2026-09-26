package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.environment.radiation.HazmatManager;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CrafterInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * ☢ Hazmat suit recipes — replacement for the removed Lead Shield.
 * <p>
 * Each piece mirrors the vanilla GOLD-armor shape, but the material is
 * leather and exactly ONE slot is the custom Lead Ingot (ExactChoice):
 * <pre>
 *   Helmet      LPL / L L          (4 leather + 1 lead ingot)
 *   Chestplate  L.L / LPL / LLL    (7 leather + 1 lead ingot)
 *   Leggings    LPL / L.L / L.L    (6 leather + 1 lead ingot)
 *   Boots       P.L / L.L          (3 leather + 1 lead ingot)
 * </pre>
 * (P = Lead Ingot, L = leather, . = empty)
 * <p>
 * CRAFT ONLY IN THE CRAFTER: the recipes are registered globally so they
 * show in the recipe book, but {@link PrepareItemCraftEvent} sets the result
 * to AIR in a regular workbench (same pattern as {@code ProtectionItem}).
 * The vanilla Crafter is allowed.
 * <p>
 * The recipes legitimately consume a Lead Ingot, so all four keys are
 * whitelisted in {@code LeadIngotCraftListener.LEAD_INGOT_CONSUMERS} — the
 * anti-uncraft protection must NOT trigger for them (neither in the
 * workbench nor in the Crafter).
 */
public class HazmatCraftListener implements Listener {

    private static NamespacedKey HELMET_KEY;
    private static NamespacedKey CHESTPLATE_KEY;
    private static NamespacedKey LEGGINGS_KEY;
    private static NamespacedKey BOOTS_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {
        Main plugin = Main.getInstance();

        HELMET_KEY = new NamespacedKey(plugin, "hazmat_helmet");
        CHESTPLATE_KEY = new NamespacedKey(plugin, "hazmat_chestplate");
        LEGGINGS_KEY = new NamespacedKey(plugin, "hazmat_leggings");
        BOOTS_KEY = new NamespacedKey(plugin, "hazmat_boots");

        // Helmet (gold-armor shape: 2 rows) — lead in the top middle
        registerRecipe(HELMET_KEY, HazmatManager.createPiece(Material.LEATHER_HELMET),
                new String[]{"LPL", "L L"});

        // Chestplate (gold-armor shape: 3 rows, empty top middle) — lead in the center
        registerRecipe(CHESTPLATE_KEY, HazmatManager.createPiece(Material.LEATHER_CHESTPLATE),
                new String[]{"L L", "LPL", "LLL"});

        // Leggings (gold-armor shape: 3 rows, empty middle column) — lead in the top middle
        registerRecipe(LEGGINGS_KEY, HazmatManager.createPiece(Material.LEATHER_LEGGINGS),
                new String[]{"LPL", "L L", "L L"});

        // Boots (gold-armor shape: 2 rows, empty middle column) — lead in the top left
        registerRecipe(BOOTS_KEY, HazmatManager.createPiece(Material.LEATHER_BOOTS),
                new String[]{"PL ", "L L"});

        Bukkit.getPluginManager().registerEvents(new HazmatCraftListener(), plugin);
        ConsoleLogger.info("[HazmatCraft] ✔ Hazmat suit recipes registered (Crafter only).");
    }

    private static void registerRecipe(NamespacedKey key, ItemStack result, String[] shape) {
        if (result == null) {
            ConsoleLogger.warn("[HazmatCraft] Failed to build result item for " + key.getKey());
            return;
        }
        try {
            Bukkit.removeRecipe(key);
        } catch (Exception ignored) { }

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.setGroup(key.getKey());
        recipe.shape(shape);
        // Lead slot: the custom Lead Ingot (ExactChoice — a plain netherite
        // ingot does NOT match, the PDC-tagged item is required).
        recipe.setIngredient('P', new RecipeChoice.ExactChoice(LeadIngotCraftListener.createLeadIngotStack()));
        recipe.setIngredient('L', Material.LEATHER);

        Bukkit.addRecipe(recipe);
        RecipeRegistry.registerRecipe(key);
    }

    private static boolean isOurRecipe(NamespacedKey key) {
        return key.equals(HELMET_KEY) || key.equals(CHESTPLATE_KEY)
                || key.equals(LEGGINGS_KEY) || key.equals(BOOTS_KEY);
    }

    // =========================
    // CRAFTER-ONLY GATE
    // =========================
    // The recipe stays registered globally, so in a regular workbench the
    // player sees it in the recipe book but the result slot is set to AIR —
    // only the vanilla Crafter can craft the hazmat suit.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof ShapedRecipe sr)) return;
        if (!isOurRecipe(sr.getKey())) return;

        if (e.getInventory() instanceof CrafterInventory) return; // Crafter: allow

        e.getInventory().setResult(new ItemStack(Material.AIR));
    }
}
