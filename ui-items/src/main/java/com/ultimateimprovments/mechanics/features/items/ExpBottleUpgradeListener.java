package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 🧪 XP Bottle Upgrade — "charged" experience bottles.
 * <p>
 * <b>Mechanics:</b>
 * <ul>
 *   <li>Anvil: any amount of identical experience bottles (regular or xN) combines into
 *       half as many bottles of the next tier: 2 × x1 → 1 × x2, 64 + 64 × x1 → 64 × x2,
 *       any other quantity works too (odd leftovers stay in the anvil).</li>
 *   <li>The multiplier is stored in PDC {@code EXP_BOTTLE_MULTIPLIER} (int). A regular bottle has no tag (= x1).</li>
 *   <li>When thrown, an x-bottle gives N times more experience. The server still fires
 *       {@link ExpBottleEvent} (via {@code CraftEventFactory.callExpBottleEvent}) even on
 *       1.21.2+, so the multiplier is applied there directly — no manual orb spawning.</li>
 *   <li>Taking the anvil result is intercepted so that arbitrary stack sizes are consumed
 *       from both slots (vanilla only eats 1+1).</li>
 * </ul>
 */
public class ExpBottleUpgradeListener implements Listener {

    // =========================
    // CONFIG (features.exp_bottle_upgrade)
    // =========================
    private static boolean enabled = true;
    private static int maxMultiplier = 100;
    private static int anvilCostAmount = 1;
    private static String itemNameFormat = "<yellow>Пузырёк опыта <gold>x{level}</gold></yellow>";
    private static List<String> itemLoreFormats = List.of(
            "<gray>Даёт в <aqua>{level}</aqua> раз больше опыта</gray>",
            "<dark_gray>{level} + {level} = <aqua>x{next}</aqua> на наковальне</dark_gray>"
    );

    // =========================
    // CONFIG
    // =========================
    public static void loadConfig(Main plugin) {
        var cfg = plugin.getConfig().getConfigurationSection("features.exp_bottle_upgrade");
        if (cfg == null) {
            // Section missing — use defaults
            enabled = true;
            maxMultiplier = 100;
            anvilCostAmount = 1;
            itemNameFormat = "<yellow>Пузырёк опыта <gold>x{level}</gold></yellow>";
            itemLoreFormats = List.of(
                    "<gray>Даёт в <aqua>{level}</aqua> раз больше опыта</gray>",
                    "<dark_gray>{level} + {level} = <aqua>x{next}</aqua> на наковальне</dark_gray>"
            );
            return;
        }

        enabled = cfg.getBoolean("enabled", true);
        maxMultiplier = Math.max(1, cfg.getInt("max_multiplier", 100));
        anvilCostAmount = Math.max(0, cfg.getInt("anvil_cost_amount", 1));
        itemNameFormat = cfg.getString("item_name", itemNameFormat);
        if (itemNameFormat == null || itemNameFormat.isBlank()) {
            itemNameFormat = "<yellow>Пузырёк опыта <gold>x{level}</gold></yellow>";
        }

        List<String> lore = cfg.getStringList("item_lore");
        if (lore != null && !lore.isEmpty()) {
            itemLoreFormats = new ArrayList<>(lore);
        }
    }

    // =========================
    // ANVIL: bottles of the same level → half as many bottles of the next level
    // (2 × x1 → 1 × x2, 64 + 64 × x1 → 64 × x2, any amount works)
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled) return;

        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() != Material.EXPERIENCE_BOTTLE) return;
        if (slot1 == null || slot1.getType() != Material.EXPERIENCE_BOTTLE) return;

        int level0 = getMultiplier(slot0);
        int level1 = getMultiplier(slot1);

        // Only bottles of the SAME level combine: x1+x1→x2, x2+x2→x3, ...
        if (level0 != level1) return;
        if (level0 >= maxMultiplier) return; // ceiling reached

        int total = slot0.getAmount() + slot1.getAmount();
        int resultAmount = total / 2;
        if (resultAmount < 1) return;

        int newLevel = level0 + 1;

        ItemStack result = new ItemStack(Material.EXPERIENCE_BOTTLE, resultAmount);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(
                Keys.EXP_BOTTLE_MULTIPLIER, PersistentDataType.INTEGER, newLevel
        );
        meta.displayName(MessageUtil.parse(
                itemNameFormat.replace("{level}", String.valueOf(newLevel))
        ));

        List<Component> lore = new ArrayList<>();
        boolean canUpgradeFurther = newLevel < maxMultiplier;
        for (String line : itemLoreFormats) {
            // Show the crafting hint line only if a further upgrade is still possible
            if (line.contains("{next}") && !canUpgradeFurther) continue;
            lore.add(MessageUtil.parse(line
                    .replace("{level}", String.valueOf(newLevel))
                    .replace("{next}", String.valueOf(newLevel + 1))));
        }
        meta.lore(lore);
        result.setItemMeta(meta);

        event.setResult(result);
        // XP cost shown by the anvil (clamped below the "Too Expensive" limit of 40 —
        // the full amount is charged manually in onTakeResult); item consumption is
        // handled there too.
        setAnvilCost(inv, Math.min(anvilCostAmount, 39), 0);
    }

    // =========================
    // TAKE RESULT: vanilla consumes only 1+1, so intercept the click and consume
    // exactly 2 × resultAmount bottles from both slots (odd leftovers stay).
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTakeResult(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof AnvilInventory inv)) return;
        if (event.getRawSlot() != 2) return; // result slot

        ItemStack result = inv.getItem(2);
        if (result == null || result.getType() != Material.EXPERIENCE_BOTTLE) return;
        int resultLevel = getMultiplier(result);
        if (resultLevel <= 1) return; // not our recipe

        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);
        if (slot0 == null || slot1 == null) return;
        // Sanity: inputs must be bottles of the exact previous level
        if (getMultiplier(slot0) != resultLevel - 1) return;
        if (getMultiplier(slot1) != resultLevel - 1) return;

        ClickType click = event.getClick();
        boolean shift = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        boolean hotbar = click == ClickType.NUMBER_KEY;
        boolean pickup = click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.MIDDLE;
        boolean cursorFree = event.getCursor() == null || event.getCursor().getType().isAir();

        // Only support clean "take the whole result" interactions. Any other click
        // (drag, number key without our support, etc.) is fully cancelled so vanilla
        // cannot consume 1+1 and hand out a whole stack (dupe protection).
        if (!shift && !hotbar && !pickup) {
            event.setCancelled(true);
            return;
        }
        if (!shift && !hotbar && !cursorFree) {
            event.setCancelled(true);
            return;
        }

        // Destination check before anything is consumed
        if (hotbar) {
            int button = event.getHotbarButton();
            ItemStack target = player.getInventory().getItem(button);
            boolean fits = target == null || target.getType().isAir()
                    || (target.isSimilar(result) && target.getAmount() + result.getAmount() <= target.getMaxStackSize());
            if (!fits) {
                event.setCancelled(true);
                return;
            }
        } else if (shift) {
            if (freeSpaceFor(player.getInventory(), result) < result.getAmount()) {
                event.setCancelled(true);
                return; // not enough space — deny
            }
        }

        // Charge XP levels first (vanilla's own cost handling is cancelled)
        if (anvilCostAmount > 0 && player.getGameMode() != GameMode.CREATIVE) {
            if (player.getLevel() < anvilCostAmount) {
                event.setCancelled(true);
                return; // not enough levels — deny
            }
            player.giveExpLevels(-anvilCostAmount);
        }

        event.setCancelled(true);

        // Consume exactly 2 × resultAmount bottles: from slot 0 first, then slot 1
        int need = result.getAmount() * 2;
        int take0 = Math.min(slot0.getAmount(), need);
        int take1 = need - take0;

        slot0.setAmount(slot0.getAmount() - take0);
        slot1.setAmount(slot1.getAmount() - take1);
        inv.setItem(0, slot0.getAmount() > 0 ? slot0 : null);
        inv.setItem(1, slot1.getAmount() > 0 ? slot1 : null);

        // Give the result to the player
        if (hotbar) {
            ItemStack target = player.getInventory().getItem(event.getHotbarButton());
            if (target == null || target.getType().isAir()) {
                player.getInventory().setItem(event.getHotbarButton(), result);
            } else {
                target.setAmount(target.getAmount() + result.getAmount());
            }
        } else if (shift) {
            player.getInventory().addItem(result);
        } else {
            event.setCursor(result);
        }
    }

    // =========================
    // THROW: remember the multiplier on the projectile's own PDC as a safety net,
    // so ExpBottleEvent always knows the tier even if item data is lost in flight.
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof ThrownExpBottle bottle)) return;

        int multiplier = getMultiplier(bottle.getItem());
        if (multiplier > 1) {
            bottle.getPersistentDataContainer().set(
                    Keys.EXP_BOTTLE_MULTIPLIER, PersistentDataType.INTEGER, multiplier
            );
        }
    }

    // =========================
    // BREAK: the server fires ExpBottleEvent and spawns the XP orb with
    // event.getExperience() — just multiply it there.
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpBottle(ExpBottleEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof ThrownExpBottle bottle)) return;

        int multiplier = getMultiplier(bottle.getItem());
        if (multiplier <= 1) {
            // Safety net: fall back to the projectile PDC written at launch
            multiplier = bottle.getPersistentDataContainer()
                    .getOrDefault(Keys.EXP_BOTTLE_MULTIPLIER, PersistentDataType.INTEGER, 1);
        }
        if (multiplier <= 1) return;

        long multiplied = (long) event.getExperience() * multiplier;
        event.setExperience((int) Math.min(Integer.MAX_VALUE, Math.max(1, multiplied)));
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Returns the bottle multiplier: 1 for a regular one, N for xN.
     */
    public static int getMultiplier(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) return 1;
        if (!item.hasItemMeta()) return 1;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return Math.max(1, pdc.getOrDefault(Keys.EXP_BOTTLE_MULTIPLIER, PersistentDataType.INTEGER, 1));
    }

    /**
     * Counts how many more of {@code stack} would fit into the player inventory
     * (sums empty slots and space in matching stacks).
     */
    private static int freeSpaceFor(PlayerInventory inv, ItemStack stack) {
        int space = 0;
        int max = stack.getMaxStackSize();
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                space += max;
            } else if (slot.isSimilar(stack)) {
                space += max - slot.getAmount();
            }
            if (space >= stack.getAmount()) return space;
        }
        return space;
    }

    /**
     * Sets the anvil cost via the API (with a reflection fallback).
     */
    private static void setAnvilCost(AnvilInventory inv, int repairCost, int repairCostAmount) {
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
}
