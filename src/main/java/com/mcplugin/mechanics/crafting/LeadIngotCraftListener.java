package com.mcplugin.mechanics.crafting;

import com.mcplugin.energy.machines.assembler.AssemblerChecker;
import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
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

        meta.displayName(MessageUtil.parse("<i:false><white>Свинцовый слиток</white>"));

        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Используется для крафта свинцового щита.</gray>")
        ));

        meta.getPersistentDataContainer().set(
                Keys.LEAD_INGOT,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);

        Bukkit.removeRecipe(RECIPE_KEY);

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.shape(
                "III",
                "INI",
                "III"
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);

        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(RECIPE_KEY);
        plugin.getLogger().info("[LEAD_INGOT] Recipe registered with Keys system");
    }

    // =========================
    // OVERRIDE RESULT — ставим PDC на свинцовый слиток
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

        meta.displayName(MessageUtil.parse("<i:false><white>Свинцовый слиток</white>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Используется для крафта свинцового щита.</gray>")
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
    // ЗАЩИТА ОТ РАСКРАФЧИВАНИЯ
    // Если любой ингредиент имеет isLeadIngot PDC, а рецепт — НЕ lead_ingot → блокируем
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUncraftProtection(PrepareItemCraftEvent e) {
        // Пропускаем наш собственный рецепт — он легальный
        Recipe recipe = e.getRecipe();
        if (recipe instanceof ShapedRecipe sr && sr.getKey().equals(RECIPE_KEY)) return;

        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null) return;

        // Проверяем все слоты матрицы (может быть и 2×2 крафт)
        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient == null || ingredient.getType() == Material.AIR) continue;
            if (ingredient.getType() != Material.NETHERITE_INGOT) continue;

            ItemMeta ingMeta = ingredient.getItemMeta();
            if (ingMeta == null) continue;

            if (ingMeta.getPersistentDataContainer().has(Keys.LEAD_INGOT, PersistentDataType.BYTE)) {
                // Найден свинцовый слиток в нелегальном рецепте — блокируем
                inv.setResult(null);
                return;
            }
        }
    }
}
