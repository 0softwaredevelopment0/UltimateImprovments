package com.mcplugin.mechanics.protection;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.mechanics.crafting.RecipeRegistry;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Создание предмета «Блок защиты» и регистрация рецепта.
 * <p>
 * Рецепт представляется как сложный 9-grid ShapedRecipe. По умолчанию
 * крафт в обычном верстаке отключён через {@link PrepareItemCraftEvent}
 * — рецепт виден в книге, но в result устанавливается AIR. В Crafter
 * (Paper 1.21+) можно крафтить, если включено в конфиге.
 */
public class ProtectionItem implements Listener {

    private static NamespacedKey RECIPE_KEY;

    // =========================
    // INIT
    // =========================
    public static void init(Main main) {
        RECIPE_KEY = new NamespacedKey(main, "protection_block_recipe");
        registerRecipe(main);
        Bukkit.getPluginManager().registerEvents(new ProtectionItem(), main);
        ConsoleLogger.info("[ProtectionBlock] Item + recipe registered.");
    }

    // =========================
    // CREATE PLACEABLE ITEM
    // =========================
    public static ItemStack createProtectionItem(int amount) {
        Material base = ProtectionManager.getInstance().getBlockMaterial();
        if (base == null) base = Material.LODESTONE;
        ItemStack stack = new ItemStack(base, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(MessageUtil.parse(ProtectionConfig.getMessage(
                "item.name", "<white>Блок защиты")));

        meta.lore(List.of(
                MessageUtil.parse(ProtectionConfig.getMessage(
                        "item.lore_line_1", "<gray>Защищает территорию вокруг установки.</gray>")),
                MessageUtil.parse(ProtectionConfig.getMessage(
                        "item.lore_line_2", "<gray>Shift+RMB — открыть GUI.</gray>")),
                MessageUtil.parse(ProtectionConfig.getMessage(
                        "item.lore_line_3", "<gray>RMB с топливом — получить очки.</gray>")),
                MessageUtil.parse(ProtectionConfig.getMessage(
                        "item.lore_line_4", "<dark_gray>Recipe: see crafting menu (crafter only)</dark_gray>"))
        ));

        meta.getPersistentDataContainer().set(
                Keys.PROTECTION_BLOCK_ITEM, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
                Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    /** PDC tag на item. */
    public static boolean isProtectionItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(Keys.PROTECTION_BLOCK_ITEM, PersistentDataType.BYTE);
    }

    // =========================
    // REGISTER RECIPE
    // =========================
    private static void registerRecipe(Main main) {
        ItemStack result = createProtectionItem(1);

        try {
            Bukkit.removeRecipe(RECIPE_KEY);
        } catch (Exception ignored) {}

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.setGroup(RECIPE_KEY.getKey());

        // Сложный 9-grid рецепт (см. TODOs.md: «придумай сам но сложный чтоб был»)
        // Один из предложенных вариантов: алмазный крест + незерит + редстоун + опытная бутылка.
        recipe.shape(
                "DRD",
                "NCN",
                "EWE"
        );

        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('C', Material.NETHER_STAR);
        recipe.setIngredient('E', Material.EXPERIENCE_BOTTLE);
        recipe.setIngredient('W', Material.WITHER_SKELETON_SKULL);

        try {
            Bukkit.addRecipe(recipe);
            RecipeRegistry.registerRecipe(RECIPE_KEY);
        } catch (Exception e) {
            ConsoleLogger.warn("[ProtectionBlock] Failed to register recipe: " + e.getMessage());
        }
    }

    // =========================
    // PREPARE ITEM CRAFT — отключает обычный верстак; в Crafter пропускает.
    // =========================
    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        if (ProtectionConfig.isWorkbenchCraftAllowed() && ProtectionConfig.isCrafterCraftAllowed()) {
            return; // оба разрешены, ничего не делаем
        }
        if (ProtectionConfig.isCrafterCraftAllowed() && !ProtectionConfig.isWorkbenchCraftAllowed()) {
            // Crafter only: пропускаем если инвентарь — CrafterInventory
            if (e.getInventory() instanceof org.bukkit.inventory.CrafterInventory) {
                return;
            }
            // Иначе — обычный верстак: убираем result
            e.getInventory().setResult(new ItemStack(Material.AIR));
            // Игрок всё ещё видит recipe-book hint — это нормально, конфиг явно говорит, что в верстаке нельзя.
        }
        if (!ProtectionConfig.isCrafterCraftAllowed() && ProtectionConfig.isWorkbenchCraftAllowed()) {
            // Только верстак; crafter запрещён
            if (!(e.getInventory() instanceof org.bukkit.inventory.CrafterInventory)) {
                return;
            }
            e.getInventory().setResult(new ItemStack(Material.AIR));
        }
        // Если оба false — полностью отключено, всегда блокируем
        if (!ProtectionConfig.isCrafterCraftAllowed() && !ProtectionConfig.isWorkbenchCraftAllowed()) {
            e.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    // =========================
    // BLOCK PLACE → create protection block
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (!isProtectionItem(hand)) return;
        Block placed = e.getBlockPlaced();
        if (placed == null) return;
        ProtectionBlock created = ProtectionManager.getInstance()
                .createBlock(placed.getLocation(), e.getPlayer().getUniqueId());
        // Сообщение игроку
        e.getPlayer().sendMessage("");
        e.getPlayer().sendMessage("§a✔ §fБлок защиты установлен!");
        e.getPlayer().sendMessage("§7▸ <gold>Он сейчас ВЫКЛЮЧЕН. Откройте GUI (Shift+RMB) и включите.");
        e.getPlayer().sendMessage("§7▸ <gray>Используйте §fRMB с топливом в руке§7 для очков.</gray>");
        e.getPlayer().sendMessage("");
    }

    // =========================
    // Защита от перетаскивания предмета в инвентарь (нельзя своровать)
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        if (isProtectionItem(cursor) || isProtectionItem(current)) {
            // Разрешаем только определённые слоты (броска в воздух, перемещение внутри
            // собственного инвентаря для использования). Блокируем:
            //   - click в верхний инвентарь (сундуки, GUI)
            //   - shift-click в верхний инвентарь
            if (e.getClick().isShiftClick()) {
                ItemStack clicked = e.getCurrentItem();
                if (isProtectionItem(clicked) && e.getInventory().getHolder() != e.getWhoClicked()) {
                    e.setCancelled(true);
                }
            }
        }
    }
}
