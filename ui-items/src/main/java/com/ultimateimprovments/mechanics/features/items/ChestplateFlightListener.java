package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 🦅 Chestplate Flight Upgrade — chestplate upgrade in the anvil using phantom membranes.
 * <p>
 * <b>Mechanics:</b>
 * <ul>
 *   <li>Slot 1 (anvil): any chestplate (leather, iron, diamond, etc.)</li>
 *   <li>Slot 2: phantom membranes (each = +1%, 64 membranes = +64%)</li>
 *   <li>Result: the same chestplate with lore "Flight Upgrade: X.X%"</li>
 *   <li>At 100%: lore "Flight Ready", chestplate gets the glider component</li>
 * </ul>
 * <p>
 * The percent is stored in PDC as {@code Keys.CHESTPLATE_FLIGHT} (Double).
 * Lore gradient: dark red → red → orange → yellow → green → dark green.
 */
public class ChestplateFlightListener implements Listener {

    private static final Set<Material> CHESTPLATES = Set.of(
        Material.LEATHER_CHESTPLATE,
        Material.CHAINMAIL_CHESTPLATE,
        Material.IRON_CHESTPLATE,
        Material.GOLDEN_CHESTPLATE,
        Material.DIAMOND_CHESTPLATE,
        Material.NETHERITE_CHESTPLATE,
        Material.COPPER_CHESTPLATE
    );

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat PCT_FMT = new DecimalFormat("0.0", new java.text.DecimalFormatSymbols(java.util.Locale.US));

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;
        if (slot1.getType() != Material.PHANTOM_MEMBRANE) return;
        if (!CHESTPLATES.contains(slot0.getType())) return;

        ItemMeta meta0 = slot0.getItemMeta();
        if (meta0 == null) return;

        var pdc = meta0.getPersistentDataContainer();
        double existingPct = pdc.getOrDefault(Keys.CHESTPLATE_FLIGHT, PersistentDataType.DOUBLE, 0.0);
        if (existingPct >= 100.0) return;

        int membraneCount = slot1.getAmount();
        double newPct = Math.min(100.0, existingPct + membraneCount);

        // Clone chestplate and apply upgrade
        ItemStack result = slot0.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        var resultPdc = meta.getPersistentDataContainer();
        resultPdc.set(Keys.CHESTPLATE_FLIGHT, PersistentDataType.DOUBLE, newPct);

        // Build lore: preserve existing, remove old flight lines, append new one
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        List<Component> filteredLore = new ArrayList<>();
        for (Component c : lore) {
            String text = LEGACY.serialize(c);
            if (!text.contains("Улучшение полёта:") && !text.contains("Пригоден для полёта")) {
                filteredLore.add(c);
            }
        }

        if (newPct >= 100.0) {
            meta.setGlider(true);
            filteredLore.add(MessageUtil.parse("<!italic><green>Пригоден для полёта</green>"));
        } else {
            String pctColor = flightGradientColor(newPct);
            filteredLore.add(MessageUtil.parse(
                "<!italic><white>Улучшение полёта:</white> " + pctColor + PCT_FMT.format(newPct) + "%"
            ));
        }

        meta.lore(filteredLore);
        result.setItemMeta(meta);

        event.setResult(result);
        setAnvilCost(inv, 0, membraneCount);
    }

    /**
     * Returns the MiniMessage color tag for the flight upgrade percent.
     * The color is interpolated between 6 stops depending on pct (0-100):
     * 0% → #006400 (dark green)
     * 20% → #00FF00 (green)
     * 40% → #FFFF00 (yellow)
     * 60% → #FF8C00 (orange)
     * 80% → #FF0000 (red)
     * 100% → #8B0000 (dark red)
     */
    private static String flightGradientColor(double pct) {
        double clamped = Math.max(0.0, Math.min(100.0, pct));
        // 6 color stops: dark green → green → yellow → orange → red → dark red
        int[][] stops = {
            {0x00, 0x64, 0x00},  // 0%:  #006400 dark green
            {0x00, 0xFF, 0x00},  // 20%: #00FF00 green
            {0xFF, 0xFF, 0x00},  // 40%: #FFFF00 yellow
            {0xFF, 0x8C, 0x00},  // 60%: #FF8C00 orange
            {0xFF, 0x00, 0x00},  // 80%: #FF0000 red
            {0x8B, 0x00, 0x00}   // 100%: #8B0000 dark red
        };

        double segmentPct = 100.0 / (stops.length - 1); // 20% per segment
        int seg = (int) Math.min(stops.length - 2, Math.floor(clamped / segmentPct));
        double t = (clamped - seg * segmentPct) / segmentPct; // 0..1 within segment

        int r = (int) Math.round(stops[seg][0] + (stops[seg + 1][0] - stops[seg][0]) * t);
        int g = (int) Math.round(stops[seg][1] + (stops[seg + 1][1] - stops[seg][1]) * t);
        int b = (int) Math.round(stops[seg][2] + (stops[seg + 1][2] - stops[seg][2]) * t);

        return String.format("<#%02X%02X%02X>", r, g, b);
    }

    /**
     * Sets the anvil cost and how many items are consumed from slot 2.
     * repairCost = experience levels (0 = free),
     * repairCostAmount = how many items from slot 2 are consumed (membraneCount).
     */
    private void setAnvilCost(AnvilInventory inv, int repairCost, int repairCostAmount) {
        try {
            inv.setRepairCost(repairCost);
            inv.setRepairCostAmount(repairCostAmount);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Fallback for older Paper versions
            try {
                Field costField = inv.getClass().getDeclaredField("repairCost");
                costField.setAccessible(true);
                costField.set(inv, repairCost);
            } catch (Exception ignored) {}
        }
    }
}
