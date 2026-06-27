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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ⚡ AutoCraftManager — режим автокрафтера.
 * <p>
 * Когда режим включён, клик по результату крафта в верстаке:
 * <ul>
 *   <li>Обычный левый клик — результат сразу выпадает в инвентарь</li>
 *   <li>Shift+клик — крафтит всё, что возможно (как ванильный шифт-клик + авто-цикл)</li>
 * </ul>
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!autoCraftEnabled.contains(player.getUniqueId())) return;

        // Only intercept crafting table / player crafting grid
        InventoryType invType = event.getInventory().getType();
        if (invType != InventoryType.WORKBENCH && invType != InventoryType.CRAFTING) return;

        // Only intercept clicks on the result slot
        if (event.getSlot() != 0) return;
        if (!(event.getInventory() instanceof CraftingInventory craftInv)) return;

        ItemStack result = craftInv.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        boolean isShiftClick = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;

        // ── SHIFT-CLICK: let Minecraft handle it naturally, then re-craft ──
        if (isShiftClick) {
            // Don't cancel — let Paper's shift-click handle the craft.
            // After 1 tick, check if more can be crafted and auto-craft again.
            scheduleAutoCraft(player, craftInv, player.getUniqueId());
            return;
        }

        // ── REGULAR LEFT/RIGHT CLICK: manual handling ──
        // Cancel so item doesn't go to cursor
        event.setCancelled(true);

        // Check inventory space
        if (!hasSpaceFor(player, result)) {
            player.sendMessage(MessageUtil.parse(
                "<red>❌</red> <white>Inventory is full!</white>"
            ));
            return;
        }

        // Consume ONE layer of ingredients and give result
        if (consumeOneCraft(craftInv)) {
            // Give the crafted item to player
            ItemStack crafted = result.clone();
            player.getInventory().addItem(crafted).values().forEach(drop ->
                player.getWorld().dropItemNaturally(player.getLocation(), drop)
            );
            SoundUtil.playCraftSound(player);

            // Schedule auto-recraft if ingredients remain
            scheduleAutoCraft(player, craftInv, player.getUniqueId());
        }
    }

    /**
     * After a shift-click or a successful manual craft, schedule a task
     * to re-craft if ingredients are still available.
     */
    private static void scheduleAutoCraft(Player player, CraftingInventory ignoredInv, UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!autoCraftEnabled.contains(uuid)) return;
                if (!player.isOnline()) return;
                if (!(player.getOpenInventory().getTopInventory() instanceof CraftingInventory ci)) return;

                ItemStack nextResult = ci.getResult();
                if (nextResult == null || nextResult.getType() == Material.AIR) return;

                // Try to craft another set
                if (consumeOneCraft(ci)) {
                    ItemStack crafted = nextResult.clone();
                    player.getInventory().addItem(crafted).values().forEach(drop ->
                        player.getWorld().dropItemNaturally(player.getLocation(), drop)
                    );
                    SoundUtil.playCraftSound(player);

                    // Still more ingredients? Schedule again
                    scheduleAutoCraft(player, ci, uuid);
                }
            }
        }.runTask(Main.getInstance());
    }

    // =========================
    // INGREDIENT CONSUMPTION
    // =========================

    /**
     * Consumes ONE layer of ingredients from the crafting matrix.
     * Handles container items (buckets → empty bucket, etc.)
     *
     * @param craftInv the crafting inventory
     * @return true if ingredients were consumed, false if not enough ingredients
     */
    private static boolean consumeOneCraft(CraftingInventory craftInv) {
        ItemStack[] matrix = craftInv.getMatrix();
        if (matrix == null || matrix.length == 0) return false;

        // Verify all ingredients have at least 1 item
        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getAmount() < 1) return false;
        }

        // Consume ingredients
        ItemStack[] newMatrix = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType() == Material.AIR) {
                newMatrix[i] = null;
                continue;
            }
            int newAmount = item.getAmount() - 1;
            if (newAmount <= 0) {
                // Check for container remainder (e.g. WATER_BUCKET → BUCKET)
                ItemStack remainder = getContainerRemainder(item.getType());
                newMatrix[i] = remainder;
            } else {
                ItemStack reduced = item.clone();
                reduced.setAmount(newAmount);
                newMatrix[i] = reduced;
            }
        }

        craftInv.setMatrix(newMatrix);
        return true;
    }

    /**
     * Checks if the player has inventory space for at least one craft result.
     */
    private static boolean hasSpaceFor(Player player, ItemStack result) {
        int maxStack = result.getMaxStackSize();
        int freeSpace = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                freeSpace += maxStack;
            } else if (item.isSimilar(result) && item.getAmount() < item.getMaxStackSize()) {
                freeSpace += item.getMaxStackSize() - item.getAmount();
            }
        }
        return freeSpace >= result.getAmount();
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

    /**
     * Internal sound helper.
     */
    private static class SoundUtil {
        static void playCraftSound(Player player) {
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_CRAFTER_CRAFT, 0.8f, 1.2f);
            } catch (Exception ignored) {}
        }
    }
}
