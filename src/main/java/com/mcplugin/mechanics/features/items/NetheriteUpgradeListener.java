package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ⚔ Незеритовое улучшение — прокачка незеритовых предметов в наковальне скрапом.
 * <p>
 * <b>Механика:</b>
 * <ul>
 *   <li>Слот 1: любой незеритовый предмет (меч, инструмент, броня)</li>
 *   <li>Слот 2: незеритовый скрап (каждый = +0.1% к атрибуту, +1 к макс. прочности)</li>
 *   <li>Меч: +0.1% к урону от атаки ({@link Attribute#ATTACK_DAMAGE})</li>
 *   <li>Топор/кирка/лопата/мотыга: +0.1% к скорости копания ({@link Attribute#MINING_EFFICIENCY})</li>
 *   <li>Броня: +0.1% к броне ({@link Attribute#ARMOR}) + сопротивление отбрасыванию</li>
 *   <li>Все предметы: +1 к макс. прочности за каждый скрап</li>
 * </ul>
 * <p>
 * Количество улучшений хранится в PDC как {@code Keys.NETHERITE_UPGRADE} (Integer).
 */
public class NetheriteUpgradeListener implements Listener {

    private static final Set<Material> NETHERITE_WEAPONS = Set.of(
        Material.NETHERITE_SWORD
    );

    private static final Set<Material> NETHERITE_TOOLS = Set.of(
        Material.NETHERITE_AXE,
        Material.NETHERITE_PICKAXE,
        Material.NETHERITE_SHOVEL,
        Material.NETHERITE_HOE
    );

    private static final Set<Material> NETHERITE_ARMOR = Set.of(
        Material.NETHERITE_HELMET,
        Material.NETHERITE_CHESTPLATE,
        Material.NETHERITE_LEGGINGS,
        Material.NETHERITE_BOOTS
    );

    private static final Set<Material> ALL_NETHERITE;
    static {
        ALL_NETHERITE = new java.util.HashSet<>();
        ALL_NETHERITE.addAll(NETHERITE_WEAPONS);
        ALL_NETHERITE.addAll(NETHERITE_TOOLS);
        ALL_NETHERITE.addAll(NETHERITE_ARMOR);
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat BONUS_FMT = new DecimalFormat("0.0", new java.text.DecimalFormatSymbols(java.util.Locale.US));
    /** +0.1 flat за каждый скрап (ADD_NUMBER) */
    private static final double PER_SCRAP_BONUS = 0.1;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;
        if (slot1.getType() != Material.NETHERITE_SCRAP) return;
        if (!ALL_NETHERITE.contains(slot0.getType())) return;

        ItemMeta meta0 = slot0.getItemMeta();
        if (meta0 == null) return;

        var pdc = meta0.getPersistentDataContainer();
        int existingUpgrades = pdc.getOrDefault(Keys.NETHERITE_UPGRADE, PersistentDataType.INTEGER, 0);

        int scrapCount = slot1.getAmount();
        int newUpgrades = existingUpgrades + scrapCount;

        // Clone and apply upgrades
        ItemStack result = slot0.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        var resultPdc = meta.getPersistentDataContainer();
        resultPdc.set(Keys.NETHERITE_UPGRADE, PersistentDataType.INTEGER, newUpgrades);

        // ⚠ ВАЖНО: НЕ вызываем removeAttributeModifier(Attribute) — это удаляет
        // ВСЕ модификаторы атрибута, включая БАЗОВЫЙ урон/скорость/защиту предмета!
        // Вместо этого просто добавляем наш модификатор с тем же NamespacedKey.
        // addAttributeModifier() с одинаковым key ЗАМЕНЯЕТ старый модификатор,
        // не трогая базовый модификатор предмета (который задаёт 8 урона меча, 6 брони и т.д.).
        double totalBonus = newUpgrades * PER_SCRAP_BONUS;
        NamespacedKey modKey = new NamespacedKey(Main.getInstance(), "netherite_upgrade");

        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            // Меч: базовый урон 8 + бонус
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                modKey, totalBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            // Инструменты: базовая скорость копания + бонус
            meta.addAttributeModifier(Attribute.MINING_EFFICIENCY, new AttributeModifier(
                modKey, totalBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_ARMOR.contains(slot0.getType())) {
            // Броня: базовая броня + бонус
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                modKey, totalBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR
            ));

            NamespacedKey kbKey = new NamespacedKey(Main.getInstance(), "netherite_upgrade_kb");
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                kbKey, totalBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR
            ));
        }

        // +1 к макс. прочности за каждый скрап (для всех незеритовых предметов)
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            int currentMax = damageable.getMaxDamage();
            damageable.setMaxDamage(currentMax + scrapCount);
        }

        // Build lore: preserve existing, remove old upgrade line, append new
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        List<Component> filteredLore = new ArrayList<>();
        for (Component c : lore) {
            String text = LEGACY.serialize(c);
            if (!text.contains("Незеритовое улучшение:")) {
                filteredLore.add(c);
            }
        }

        // Line: upgrade count + attribute bonus (flat ADD_NUMBER, ПРИБАВЛЯЕТСЯ к базе)
        double bonusDisplay = newUpgrades * PER_SCRAP_BONUS;
        String attrName;
        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            attrName = "к урону";
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            attrName = "к скорости";
        } else {
            attrName = "к защите";
        }

        filteredLore.add(MessageUtil.parse(
            "<!italic><white>Незеритовое улучшение:</white> <yellow>+" + newUpgrades + "</yellow> " +
            "<gradient:#8B4513:#DAA520>+" + BONUS_FMT.format(bonusDisplay) + " " + attrName + "</gradient>"
        ));

        meta.lore(filteredLore);
        result.setItemMeta(meta);

        event.setResult(result);
        setAnvilCost(inv, 0, scrapCount);
    }

    /**
     * Устанавливает стоимость наковальни.
     * repairCost = уровни опыта (бесплатно),
     * repairCostAmount = сколько скрапа потребляется.
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
}
