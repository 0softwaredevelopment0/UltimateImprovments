package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.energy.machines.assembler.AssemblerChecker;
import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Custom craft for the Glass Sword — a fragile one-shot blade that deals
 * 19 attack damage but has only 1 durability point.
 * <p>
 * Follows the same custom-item pattern as the Blazing Sword: the recipe is
 * registered globally (so it appears in the recipe book / crafting preview),
 * the result carries the {@code is_glass_sword} PDC, and the actual craft is
 * finalized only inside the Item Assembler (Crafter). Attack damage is raised
 * to 20 via an {@link Attribute#ATTACK_DAMAGE} modifier and durability is
 * dropped to 1 via {@link Damageable}.
 */
public class GlassSwordCraftListener implements Listener {

    /** Glass Sword durability — a single point, so it shatters after one hit. */
    private static final int MAX_DURABILITY = 1;

    /**
     * Base material attack damage (diamond sword = 7). The modifier below
     * brings the total to {@link #ATTACK_DAMAGE} (19 = 9.5 hearts).
     */
    private static final double BASE_ATTACK_DAMAGE = 7.0;

    /** Total attack damage the sword should deal per hit. */
    private static final double ATTACK_DAMAGE = 19.0;

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init() {
        RECIPE_KEY = new NamespacedKey(Main.getInstance(), "glass_sword");
        registerRecipe();
    }

    // =========================
    // CREATE THE GLASS SWORD ITEM
    // =========================
    private static ItemStack createSword() {
        ItemStack result = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        meta.displayName(MessageUtil.parse("<i:false><white>Glass sword</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Deals 19 damage but shatters after one hit.</gray>")
        ));

        // =========================
        // PDC via Keys
        // =========================
        meta.getPersistentDataContainer().set(
                Keys.GLASS_SWORD,
                PersistentDataType.BYTE,
                (byte) 1
        );

        // =========================
        // 19 attack damage (diamond sword base = 7, +12 → 19)
        // =========================
        NamespacedKey damageKey = new NamespacedKey(Main.getInstance(), "glass_sword_damage");
        meta.addAttributeModifier(
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(
                        damageKey,
                        ATTACK_DAMAGE - BASE_ATTACK_DAMAGE,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                )
        );

        // =========================
        // Max durability 1 — one hit and it breaks
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

        // Complex 3x3 sword-shaped recipe: glass blade + netherite scrap guard + stick.
        recipe.shape(
                "DGD",
                "GGG",
                "GSG"
        );

        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('D', Material.NETHERITE_SCRAP);
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
