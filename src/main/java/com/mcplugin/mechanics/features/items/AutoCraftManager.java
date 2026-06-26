package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ⚡ AutoCraftManager — режим автокрафтера.
 * <p>
 * Когда режим включён, клик по результату крафта в верстаке
 * отменяет стандартное действие и сразу выдаёт результат в инвентарь игрока.
 * <p>
 * Команда: {@code /mp toggleautocraft}
 * Право: {@code mcplugin.autocraft} (default: op)
 */
public class AutoCraftManager implements Listener {

    private static final Set<UUID> autoCraftEnabled = new HashSet<>();

    // =========================
    // TOGGLE
    // =========================
    public static void toggleAutoCraft(Player player) {
        UUID uuid = player.getUniqueId();
        if (autoCraftEnabled.contains(uuid)) {
            autoCraftEnabled.remove(uuid);
            player.sendMessage(MessageUtil.parse(
                "<red>⚡</red> <white>Auto-craft mode:</white> <red>OFF</red>"
            ));
        } else {
            autoCraftEnabled.add(uuid);
            player.sendMessage(MessageUtil.parse(
                "<green>⚡</green> <white>Auto-craft mode:</white> <green>ON</green>"
            ));
        }
    }

    public static boolean isAutoCraftEnabled(UUID uuid) {
        return autoCraftEnabled.contains(uuid);
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new AutoCraftManager(), plugin);
        plugin.getLogger().info("[AutoCraft] ✔ Auto-craft mode registered.");
    }

    // =========================
    // CRAFT INTERCEPTION
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!autoCraftEnabled.contains(player.getUniqueId())) return;

        // Only intercept crafting table / player crafting grid
        InventoryType invType = event.getInventory().getType();
        if (invType != InventoryType.WORKBENCH && invType != InventoryType.CRAFTING) return;

        // Only intercept clicks on the result slot (slot 0)
        if (event.getSlot() != 0) return;
        if (!(event.getInventory() instanceof CraftingInventory craftInv)) return;

        // Allow shift-click to work but still intercept
        ItemStack result = craftInv.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        // Cancel the normal click action
        event.setCancelled(true);

        // Check inventory space
        int maxStack = result.getMaxStackSize();
        int freeSpace = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                freeSpace += maxStack;
            } else if (item.isSimilar(result) && item.getAmount() < item.getMaxStackSize()) {
                freeSpace += item.getMaxStackSize() - item.getAmount();
            }
        }
        int totalOutput = result.getAmount(); // per craft
        if (freeSpace < totalOutput) {
            player.sendMessage(MessageUtil.parse(
                "<red>❌</red> <white>Inventory is full!</white>"
            ));
            return;
        }

        // Calculate craft count: shift-click = max, regular click = 1
        boolean isShiftClick = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;

        ItemStack[] matrix = craftInv.getMatrix();
        int maxCrafts = Integer.MAX_VALUE;

        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null || matrix[i].getType() == Material.AIR) continue;
            maxCrafts = Math.min(maxCrafts, matrix[i].getAmount());
        }

        if (maxCrafts <= 0 || maxCrafts == Integer.MAX_VALUE) return;

        int craftCount = isShiftClick
            ? Math.min(maxCrafts, result.getMaxStackSize())
            : 1;

        // Consume ingredients, handling container items (buckets, bowls, etc.)
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null || matrix[i].getType() == Material.AIR) continue;
            int newAmount = matrix[i].getAmount() - craftCount;
            if (newAmount <= 0) {
                // Check if ingredient leaves a container (e.g. WATER_BUCKET → BUCKET)
                ItemStack remainder = getContainerRemainder(matrix[i].getType());
                matrix[i] = remainder;
            } else {
                matrix[i].setAmount(newAmount);
            }
        }
        craftInv.setMatrix(matrix);

        // Give result to player
        ItemStack crafted = result.clone();
        crafted.setAmount(craftCount * result.getAmount());
        player.getInventory().addItem(crafted).values().forEach(drop ->
            player.getWorld().dropItemNaturally(player.getLocation(), drop)
        );

        // Play sound
        player.playSound(player.getLocation(), Sound.BLOCK_CRAFTER_CRAFT, 0.8f, 1.2f);
    }

    /**
     * Returns the empty container item for a given ingredient type,
     * or null if the ingredient is fully consumed.
     */
    private static ItemStack getContainerRemainder(Material material) {
        return switch (material) {
            case WATER_BUCKET, LAVA_BUCKET, MILK_BUCKET, POWDER_SNOW_BUCKET, COD_BUCKET,
                 SALMON_BUCKET, PUFFERFISH_BUCKET, TROPICAL_FISH_BUCKET, AXOLOTL_BUCKET,
                 TADPOLE_BUCKET -> new ItemStack(Material.BUCKET);
            case HONEY_BOTTLE -> new ItemStack(Material.GLASS_BOTTLE);
            default -> null;
        };
    }

}
