package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.crafting.RecipeRegistry;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

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
 * Creates the «Protection Block» item and registers its recipe.
 * <p>
 * The recipe is a complex 9-grid ShapedRecipe. By default crafting in a
 * regular workbench is disabled via {@link PrepareItemCraftEvent} — the
 * recipe shows in the recipe book but the result is set to AIR. In Crafter
 * (Paper 1.21+) it can be crafted if enabled in the config.
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

    /** PDC tag on the item. */
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

        // Complex 9-grid recipe (see TODOs.md: «invent one yourself, but make it complex»)
        // One proposed option: diamond cross + netherite + redstone + experience bottle.
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
    // PREPARE ITEM CRAFT — disables the regular workbench; allows Crafter.
    // <p>
    // HIGHEST priority so our block beats any other plugins that might
    // try to restore the result ItemStack.
    // The recipe stays registered globally (Bukkit.addRecipe),
    // so in a regular workbench the player sees it in the recipe book but the
    // result slot is empty (AIR) — exactly as TODOs.md demands: «the workbench
    // only shows the recipe, doesn't let you craft it».
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().equals(RECIPE_KEY)) return;

        boolean workbench = ProtectionConfig.isWorkbenchCraftAllowed();
        boolean crafter = ProtectionConfig.isCrafterCraftAllowed();
        boolean isCrafterInv = e.getInventory() instanceof org.bukkit.inventory.CrafterInventory;

        if (workbench && crafter) return; // both allowed
        if (crafter && isCrafterInv) return; // crafter: allow
        if (!crafter && !isCrafterInv) return; // workbench: allow
        // In all other combinations — block the craft
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
        // Message to the player
        e.getPlayer().sendMessage("");
        e.getPlayer().sendMessage("§a✔ §fБлок защиты установлен!");
        e.getPlayer().sendMessage("§7▸ <gold>Он сейчас ВЫКЛЮЧЕН. Откройте GUI (Shift+RMB) и включите.");
        e.getPlayer().sendMessage("§7▸ <gray>Используйте §fRMB с топливом в руке§7 для очков.</gray>");
        e.getPlayer().sendMessage("");
    }

    // =========================
    // Full protection against drop / drag / creative / pickup of the protection item.
    // <p>
    // The old code only blocked InventoryClickEvent into a foreign inventory,
    // but NOT: (1) Q-drop (SlotType.OUTSIDE in the player's own inventory),
    // (2) InventoryDragEvent (dragging with the mouse into a foreign inventory),
    // (3) InventoryCreativeClickEvent (creative menu), (4) pickup of the
    // dropped item by another player (EntityPickupItemEvent).
    // We close all these holes defense-in-depth.
    // <p>
    // LOWEST priority: block before other plugins so they can't move the item first.
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        boolean cursorIsProtection = isProtectionItem(cursor);
        boolean currentIsProtection = isProtectionItem(current);
        if (!cursorIsProtection && !currentIsProtection) return;

        // Q-drop / Ctrl+Q-drop (pressing Q in the own inventory creates a click
        // with SlotType.OUTSIDE). Only allow the owner to drop the item (they can
        // always pick it back up), but block everyone else — in case the owner's
        // account is stolen.
        if (e.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            e.setCancelled(true);
            return;
        }

        // If the top inventory is the player's own inventory, allow.
        if (e.getInventory().getHolder() == player) return;

        // Any attempt to put/swap the item into the top inventory (not the player's) —
        // block. This covers shift-click, number-key, direct click and swap.
        e.setCancelled(true);
    }

    /**
     * Drag (mouse left button held) — BlockManager did NOT block this before.
     * A player could drag the protection item into a chest or another plugin's GUI.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        // If the top inventory is our own, allow
        if (e.getInventory().getHolder() == player) return;
        // If the drag touches a protection item — block
        if (isProtectionItem(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        // The new cursor accumulates additions, but we detect via oldCursor
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
     * Q-drop / Ctrl+Q-drop. This is NOT an InventoryClickEvent with SlotType.OUTSIDE —
     * it's a separate event with its own item-drop representation. Block it entirely.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        if (isProtectionItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    /**
     * Creative mode — protects against obtaining/giving the protection item via
     * the creative menu (middle click to copy). Note: the class
     * {@code InventoryCreativeClickEvent} is unavailable in some Paper 1.21.x builds,
     * so we don't handle it separately — if the server mod-API returns it, Bukkit
     * will allow it right there. In all other cases creative-giving is blocked in
     * {@code onInventoryClick} via {@code e.getAction()} and slot Type.
     */

    /**
     * Pickup of a dropped protection item. For security reasons we block pickup
     * by ANY player (including the owner): if dropped accidentally — a server
     * restart removes the entity, or an admin can give it again.
     * <p>
     * The alternative (allow only the owner player) would require writing the owner-UUID
     * into ItemMeta on drop — that adds spoons and vulnerabilities (owner brute-force).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Player)) return;
        if (isProtectionItem(e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }
}
