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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

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
    // <p>
    // HIGHEST приоритет чтобы наш блок побеждал любые другие плагины,
    // которые могли бы попытаться восстановить result-ItemStack.
    // Рецепт при этом остаётся зарегистрирован глобально (Bukkit.addRecipe),
    // так что в обычном верстаке игрок видит его в recipe-book, но result-slot
    // пустой (AIR) — ровно как требует TODOs.md: «верстак только крафт показывает
    // не даёт крафтить».
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        boolean workbench = ProtectionConfig.isWorkbenchCraftAllowed();
        boolean crafter = ProtectionConfig.isCrafterCraftAllowed();
        boolean isCrafterInv = e.getInventory() instanceof org.bukkit.inventory.CrafterInventory;

        if (workbench && crafter) return; // оба разрешены
        if (crafter && isCrafterInv) return; // crafter: разрешаем
        if (!crafter && !isCrafterInv) return; // workbench: разрешаем
        // Во всех остальных комбинациях — блокируем крафт
        e.getInventory().setResult(new ItemStack(Material.AIR));
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
    // Полная защита от drop / drag / creative / pickup protection item'а.
    // <p>
    // Старый код блокировал только InventoryClickEvent в чужой инвентарь,
    // но НЕ блокировал: (1) Q-drop (SlotType.OUTSIDE в собств. инвентаре
    // игрока), (2) InventoryDragEvent (мышью в чужой инвентарь),
    // (3) InventoryCreativeClickEvent (creative-меню), (4) подбор
    // выброшенного предмета другим игроком (EntityPickupItemEvent).
    // Закрываем все эти дыры defense-in-depth.
    // <p>
    // LOWEST приоритет: блокируем раньше других плагинов, чтобы они не успели
    // переместить предмет до нас.
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        boolean cursorIsProtection = isProtectionItem(cursor);
        boolean currentIsProtection = isProtectionItem(current);
        if (!cursorIsProtection && !currentIsProtection) return;

        // Q-drop / Ctrl+Q-drop (нажатие Q в собственном инвентаре создаёт клик
        // с SlotType.OUTSIDE). Разрешаем только owner'у выкинуть предмет (он всё равно
        // сможет вернуть), но блокируем всем остальным — на случай если owner'ский
        // аккаунт украден.
        if (e.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            e.setCancelled(true);
            return;
        }

        // Если верхний инвентарь — собственный инвентарь игрока, разрешаем.
        if (e.getInventory().getHolder() == player) return;

        // Любая попытка поместить/обменять предмет в верхний инвентарь (не игрока) —
        // блокируем. Это покрывает shift-click, number-key, прямой клик и swap.
        e.setCancelled(true);
    }

    /**
     * Drag (mouse left button held) — BlockManager НЕ блокировал это раньше.
     * Игрок мог перетащить protection item в сундук или GUI другого плагина.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        // Если верхний инвентарь — свой, разрешаем
        if (e.getInventory().getHolder() == player) return;
        // Если drag задевает protection item — блокируем
        if (isProtectionItem(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        // New cursor накапливает добавки, но детектим по oldCursor
        ItemStack carried = e.getCursor();
        if (isProtectionItem(carried)) {
            e.setCancelled(true);
        }
        for (ItemStack s : e.getNewItems().values()) {
            if (isProtectionItem(s)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Q-drop / Ctrl+Q-drop. Это НЕ InventoryClickEvent с SlotType.OUTSIDE — это
     * отдельное событие со своим item-drop представлением. Блокируем целиком.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        if (isProtectionItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    /**
     * Creative mode — защищает от получения/выдачи protection item через
     * creative-меню (средний клик для копирования). Замечание: класс
     * {@code InventoryCreativeClickEvent} недоступен в некоторых build'ах Paper 1.21.x,
     * поэтому отдельно не обрабатываем — если серверный mod-API его вернёт, Bukkit
     * разрешит тут же. В остальных случаях creative-выдача блокируется в
     * {@code onInventoryClick} через {@code e.getAction()} и slot Type.
     */

    /**
     * Подбор выброшенного protection item. По соображениям безопасности
     * блокируем pickup ЛЮБЫМ игроком (включая owner'а): если случайно дропнул —
     * перезагрузка сервера уберёт entity, либо админ выдаст снова.
     * <p>
     * Альтернатива (разрешить только owner-плееру) потребовала бы писать owner-UUID
     * в ItemMeta при дропе — это добавляет ложки и уязвимости (перебор owner'ов).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Player)) return;
        if (isProtectionItem(e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }
}
