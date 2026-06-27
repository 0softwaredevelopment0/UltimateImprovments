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
 *   <li>Слот 1: незеритовый меч или инструмент</li>
 *   <li>Слот 2: незеритовый скрап (+0.1 к атрибуту, +1 прочности) или
 *       незеритовый слиток (1 слиток = 9 скрапам = +0.9 к атрибуту, +9 прочности)</li>
 *   <li>Меч: +0.1% к урону от атаки ({@link Attribute#ATTACK_DAMAGE})</li>
 *   <li>Топор/кирка/лопата/мотыга: +0.1 к скорости копания ({@link Attribute#BLOCK_BREAK_SPEED})</li>
 *   <li>Броня: не улучшается (только через ванильный незерит)</li>
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

    private static final Set<Material> ALL_NETHERITE;
    static {
        ALL_NETHERITE = new java.util.HashSet<>();
        ALL_NETHERITE.addAll(NETHERITE_WEAPONS);
        ALL_NETHERITE.addAll(NETHERITE_TOOLS);
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    /** +0.1 flat за каждый скрап (ADD_NUMBER) */
    private static final double PER_SCRAP_BONUS = 0.1;
    /** 1 незеритовый слиток = 9 скрапов */
    private static final int INGOT_TO_SCRAP = 9;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;
        Material slot1Type = slot1.getType();
        if (slot1Type != Material.NETHERITE_SCRAP && slot1Type != Material.NETHERITE_INGOT) return;
        if (!ALL_NETHERITE.contains(slot0.getType())) return;

        ItemMeta meta0 = slot0.getItemMeta();
        if (meta0 == null) return;

        var pdc = meta0.getPersistentDataContainer();
        int existingUpgrades = pdc.getOrDefault(Keys.NETHERITE_UPGRADE, PersistentDataType.INTEGER, 0);

        int itemCount = slot1.getAmount();
        int scrapEquivalent = (slot1Type == Material.NETHERITE_INGOT)
                ? itemCount * INGOT_TO_SCRAP
                : itemCount;
        int newUpgrades = existingUpgrades + scrapEquivalent;

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
        NamespacedKey modKey = new NamespacedKey(Main.getInstance(), "netherite_upgrade");

        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            // Меч: базовый урон 8 + бонус
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                modKey, newUpgrades * PER_SCRAP_BONUS, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            // Инструменты: базовая скорость копания + бонус
            meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(
                modKey, newUpgrades * PER_SCRAP_BONUS, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        }

        // +1 к макс. прочности за каждый скрап/слиток (для всех незеритовых предметов)
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            int currentMax = damageable.getMaxDamage();
            damageable.setMaxDamage(currentMax + scrapEquivalent);
        }

        // Build lore: preserve existing, remove old upgrade line, append new
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // Фильтруем старые строки улучшения (оба формата — старый и новый)
        List<Component> filteredLore = new ArrayList<>();
        for (Component c : lore) {
            String text = LEGACY.serialize(c);
            if (!text.contains("Незеритовое улучшение:") && !text.contains("✦ Незерит")) {
                filteredLore.add(c);
            }
        }

        // Line: upgrade level (одна строка, одно число — каждый скрап = +1 уровень)
        // Без отдельных "+X к урону/скорости" — атрибут применяется внутри.
        filteredLore.add(MessageUtil.parse(
            "<!italic><gradient:#8B4513:#DAA520>✦ Незерит +" + newUpgrades + "</gradient>"
        ));

        meta.lore(filteredLore);
        result.setItemMeta(meta);

        event.setResult(result);
        // Стоимость — сколько предметов потребляется из слот 2
        setAnvilCost(inv, 0, itemCount);
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
