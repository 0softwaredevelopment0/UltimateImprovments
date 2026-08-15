package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🛡 Integrity Combine Listener — handles events related to
 * restoring and combining item integrity:
 * <p>
 * - Anvil: repairing with material raises integrity, combining two items sums it
 * - Grindstone: combining two items sums the integrity
 * - Crafting table / inventory: combining two identical items sums the integrity
 * - Experience gain: an item with Mending restores integrity
 */
public class IntegrityCombineListener implements Listener {

    // Anvil message cooldown (to avoid spamming)
    private final Map<UUID, Long> anvilMessageCooldowns = new HashMap<>();
    private static final long ANVIL_MSG_COOLDOWN_MS = 2000; // 2 seconds

    // =========================
    // ANVIL — repair and combine
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!IntegrityManager.isEnabled()) return;

        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;

        // Check whether the item in slot 0 has durability
        if (IntegrityManager.getMaxDurability(slot0) <= 0) return;

        // =========================
        // SCENARIO 1: Crafting with material (item + material, e.g. diamond pickaxe + diamond)
        // =========================
        if (isRepairMaterial(slot0.getType(), slot1.getType())) {
            if (!IntegrityManager.isAnvilRepairEnabled()) return;

            // Make sure the item is initialized
            IntegrityManager.ensureInitialized(slot0);

            double currentIntegrity = IntegrityManager.getCurrentIntegrity(slot0);
            if (currentIntegrity <= 0) return;

            // ===== NEW MECHANIC: crafting with material =====
            if (IntegrityManager.isAnvilMaterialCraftEnabled()) {
                int materialCount = 1;
                double bonusPerMaterial = IntegrityManager.getAnvilMaterialCraftBonus();
                double totalBonus = materialCount * bonusPerMaterial;
                double newIntegrity = Math.min(100.0, currentIntegrity + totalBonus);

                ItemStack result = slot0.clone();
                ItemIntegrityAPI.setItemIntegrity(result, newIntegrity);
                IntegrityManager.updateItemLore(result);

                double pct = newIntegrity;
                double bonusPct = totalBonus;

                Player player = getPlayerFromAnvil(event);
                if (player != null) {
                    String msg = IntegrityManager.getAnvilMaterialCraftMessage()
                            .replace("%current%", IntegrityManager.formatPercent(pct))
                            .replace("%bonus%", IntegrityManager.formatPercent(bonusPct));
                    if (canSendAnvilMessage(player)) {
                        player.sendMessage(MessageUtil.parse(msg));
                    }
                }

                event.setResult(result);
                setAnvilCost(inv, 0, 1);
                return;
            }

            // ===== OLD MECHANIC (fallback): repair with a fixed percentage =====
            if (currentIntegrity >= 100.0) return;

            double restoreAmount = 100.0 * IntegrityManager.getAnvilRepairMultiplier();

            ItemStack result = slot0.clone();
            ItemIntegrityAPI.increaseItemIntegrityPercent(result, restoreAmount);
            IntegrityManager.updateItemLore(result);

            double newCurrent = IntegrityManager.getCurrentIntegrity(result);

            Player player = getPlayerFromAnvil(event);
            if (player != null) {
                String msg = IntegrityManager.getAnvilRepairMessage()
                        .replace("%current%", IntegrityManager.formatPercent(newCurrent));
                if (canSendAnvilMessage(player)) {
                    player.sendMessage(MessageUtil.parse(msg));
                }
            }

            event.setResult(result);
            setAnvilCost(inv, 0, 1);
            return;
        }

        // =========================
        // SCENARIO 2: Combining two identical items (pickaxe + pickaxe)
        // =========================
        if (slot0.getType() == slot1.getType() && IntegrityManager.getMaxDurability(slot1) > 0) {
            if (!IntegrityManager.isAnvilCombineEnabled()) return;
            if (!IntegrityManager.isCombineEnabled()) return;

            IntegrityManager.ensureInitialized(slot0);
            IntegrityManager.ensureInitialized(slot1);

            double cur0 = IntegrityManager.getCurrentIntegrity(slot0);
            double cur1 = IntegrityManager.getCurrentIntegrity(slot1);

            if (cur0 < 0 || cur1 < 0) return;

            // Sum the integrity + bonus, cap — 100.0%
            double combined = cur0 + cur1;
            double bonus = 100.0 * IntegrityManager.getAnvilCombineBonus();
            combined += bonus;

            double newCurrent = Math.min(100.0, combined);

            ItemStack result = slot0.clone();
            ItemIntegrityAPI.setItemIntegrity(result, newCurrent);
            IntegrityManager.updateItemLore(result);

            Player player = getPlayerFromAnvil(event);
            if (player != null) {
                String msg = IntegrityManager.getAnvilCombineMessage()
                        .replace("%current%", IntegrityManager.formatPercent(newCurrent));
                if (canSendAnvilMessage(player)) {
                    player.sendMessage(MessageUtil.parse(msg));
                }
            }

            event.setResult(result);
            setAnvilCost(inv, 0, 2);
        }
    }

    // =========================
    // GRINDSTONE — combining two items (removes enchantments)
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isCombineEnabled()) return;

        GrindstoneInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;

        if (slot0.getType() != slot1.getType()) return;
        if (IntegrityManager.getMaxDurability(slot0) <= 0) return;

        IntegrityManager.ensureInitialized(slot0);
        IntegrityManager.ensureInitialized(slot1);

        double cur0 = IntegrityManager.getCurrentIntegrity(slot0);
        double cur1 = IntegrityManager.getCurrentIntegrity(slot1);

        if (cur0 < 0 || cur1 < 0) return;

        double combined = cur0 + cur1;
        double lossRate = IntegrityManager.getCombineLossRate();
        if (lossRate > 0) {
            combined *= (1.0 - lossRate);
        }

        double newCurrent = Math.min(100.0, combined);

        ItemStack result = slot0.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null && resultMeta.hasEnchants()) {
            java.util.Map<Enchantment, Integer> enchants = new java.util.HashMap<>(resultMeta.getEnchants());
            for (Enchantment ench : enchants.keySet()) {
                resultMeta.removeEnchant(ench);
            }
            result.setItemMeta(resultMeta);
        }

        ItemIntegrityAPI.setItemIntegrity(result, newCurrent);
        IntegrityManager.updateItemLore(result);

        event.setResult(result);
    }

    // =========================
    // CRAFTING TABLE / INVENTORY — combining on craft (exactly 2 items)
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isCombineEnabled()) return;

        ItemStack[] matrix = event.getInventory().getMatrix();

        ItemStack item1 = null;
        ItemStack item2 = null;
        int itemCount = 0;
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType() == Material.AIR) continue;

            if (IntegrityManager.getMaxDurability(stack) > 0) {
                if (item1 == null) {
                    item1 = stack;
                    itemCount = 1;
                } else if (stack.getType() == item1.getType() && itemCount == 1) {
                    item2 = stack;
                    itemCount = 2;
                } else {
                    return;
                }
            } else {
                return;
            }
        }

        if (itemCount != 2 || item1 == null || item2 == null) return;

        IntegrityManager.ensureInitialized(item1);
        IntegrityManager.ensureInitialized(item2);

        double cur0 = IntegrityManager.getCurrentIntegrity(item1);
        double cur1 = IntegrityManager.getCurrentIntegrity(item2);

        if (cur0 < 0 || cur1 < 0) return;

        double combined = cur0 + cur1;
        double lossRate = IntegrityManager.getCombineLossRate();
        if (lossRate > 0) {
            combined *= (1.0 - lossRate);
        }

        double newCurrent = Math.min(100.0, combined);

        ItemStack result = item1.clone();
        ItemIntegrityAPI.setItemIntegrity(result, newCurrent);
        IntegrityManager.updateItemLore(result);

        event.getInventory().setResult(result);
    }

    // =========================
    // MENDING — restoring integrity on experience pickup
    // =========================
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMendingRestore(PlayerExpChangeEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isMendingXpEnabled()) return;

        Player player = event.getPlayer();
        int xpAmount = event.getAmount();
        if (xpAmount <= 0) return;

        double restore = xpAmount * IntegrityManager.getMendingXpMultiplier();
        if (restore <= 0) return;

        PlayerInventory inv = player.getInventory();
        boolean restored = false;

        for (int i = 0; i <= 40; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (IntegrityManager.getMaxDurability(item) <= 0) continue;

            // Only items with the Mending enchantment
            if (!item.containsEnchantment(Enchantment.MENDING)) continue;

            double before = IntegrityManager.getCurrentIntegrity(item);
            if (before >= 100.0) continue;

            ItemIntegrityAPI.increaseItemIntegrityPercent(item, restore);
            restored = true;
        }

        // Message to the player — only if at least 1 item was actually restored
        if (restored) {
            String msg = IntegrityManager.getMendingMessage();
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(MessageUtil.parse(msg.replace("%amount%", String.format("%.3f", restore))));
            }
        }
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Sets the repair cost on the AnvilInventory via the API or reflection.
     * Without this the player cannot take the result out of the anvil.
     */
    private void setAnvilCost(AnvilInventory inv, int repairCost, int repairCostAmount) {
        try {
            inv.setRepairCost(repairCost);
            inv.setRepairCostAmount(repairCostAmount);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            try {
                Field costField = inv.getClass().getDeclaredField("repairCost");
                costField.setAccessible(true);
                costField.set(inv, repairCost);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Anvil message cooldown (to avoid spamming when moving items).
     */
    private boolean canSendAnvilMessage(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = anvilMessageCooldowns.get(uuid);
        if (last != null && (now - last) < ANVIL_MSG_COOLDOWN_MS) {
            return false;
        }
        anvilMessageCooldowns.put(uuid, now);
        return true;
    }

    /**
     * Checks whether the item is a repair material for the given tool.
     */
    private boolean isRepairMaterial(Material tool, Material material) {
        String toolName = tool.name();
        String matName = material.name();

        return switch (matName) {
            case "DIAMOND" -> toolName.startsWith("DIAMOND_");
            case "IRON_INGOT" -> toolName.startsWith("IRON_") || toolName.startsWith("CHAINMAIL_");
            case "GOLD_INGOT" -> toolName.startsWith("GOLD_");
            case "NETHERITE_INGOT" -> toolName.startsWith("NETHERITE_");
            case "LEATHER" -> toolName.contains("LEATHER");
            case "COPPER_INGOT" -> toolName.startsWith("COPPER_");
            case "PHANTOM_MEMBRANE" -> toolName.equals("ELYTRA");
            default -> {
                boolean isWoodMaterial = matName.endsWith("_PLANKS") || matName.equals("STICK");
                if (isWoodMaterial && toolName.startsWith("WOODEN_")) {
                    yield true;
                }
                if ((matName.equals("COBBLESTONE") || matName.equals("BLACKSTONE") || matName.equals("COBBLED_DEEPSLATE"))
                        && toolName.startsWith("STONE_")) {
                    yield true;
                }
                if (matName.equals("SCUTE") && toolName.equals("TURTLE_HELMET")) {
                    yield true;
                }
                yield false;
            }
        };
    }

    /**
     * Tries to get the player from the anvil event via the inventory.
     */
    private Player getPlayerFromAnvil(PrepareAnvilEvent event) {
        var view = event.getView();
        if (view.getPlayer() instanceof Player player) {
            return player;
        }
        return null;
    }
}
