package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.util.MessageUtil;
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
 * 🦅 Chestplate Flight Upgrade — улучшение нагрудника в наковальне мембранами фантома.
 * <p>
 * <b>Механика:</b>
 * <ul>
 *   <li>Слот 1 (наковальня): любой нагрудник (кожаный, железный, алмазный и т.д.)</li>
 *   <li>Слот 2: мембраны фантома (каждая = +1%, 64 мембраны = +64%)</li>
 *   <li>Результат: тот же нагрудник с лором "Улучшение полёта: X.X%"</li>
 *   <li>При 100%: лор "Пригоден для полёта", нагрудник получает компонент glider</li>
 * </ul>
 * <p>
 * Процент хранится в PDC как {@code Keys.CHESTPLATE_FLIGHT} (Double).
 * Градиент в лоре: тёмно-красный → красный → оранжевый → жёлтый → зелёный → тёмно-зелёный.
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
    private static final DecimalFormat PCT_FMT = new DecimalFormat("0.0");

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
            filteredLore.add(MessageUtil.parse("<green>Пригоден для полёта</green>"));
        } else {
            filteredLore.add(MessageUtil.parse(
                "<white>Улучшение полёта:</white> <gradient:#8B0000:#FF0000:#FF8C00:#FFFF00:#00FF00:#006400>" +
                PCT_FMT.format(newPct) + "%</gradient>"
            ));
        }

        meta.lore(filteredLore);
        result.setItemMeta(meta);

        event.setResult(result);
        setAnvilCost(inv, 0, membraneCount);
    }

    /**
     * Устанавливает стоимость наковальни и количество потребляемых предметов из слота 2.
     * repairCost = уровни опыта (0 = бесплатно),
     * repairCostAmount = сколько предметов из слота 2 потребляется (membraneCount).
     */
    private void setAnvilCost(AnvilInventory inv, int repairCost, int repairCostAmount) {
        try {
            inv.setRepairCost(repairCost);
            inv.setRepairCostAmount(repairCostAmount);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Fallback для старых версий Paper
            try {
                Field costField = inv.getClass().getDeclaredField("repairCost");
                costField.setAccessible(true);
                costField.set(inv, repairCost);
            } catch (Exception ignored) {}
        }
    }
}
