package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.*;

/**
 * ⚔ Незеритовое улучшение — прокачка незеритовых предметов в наковальне скрапом.
 * <p>
 * <b>Механика:</b>
 * <ul>
 *   <li>Слот 1: незеритовый предмет (меч, инструмент, броня)</li>
 *   <li>Слот 2: незеритовый скрап (НЕ слиток — слиток конфликтует с починкой)</li>
 *   <li>Оружие: +0.1 к урону атаки ({@link Attribute#ATTACK_DAMAGE}) за скрап</li>
 *   <li>Инструменты: +0.1 к скорости копания ({@link Attribute#BLOCK_BREAK_SPEED}) за скрап</li>
 *   <li>Броня: +0.1 к защите ({@link Attribute#ARMOR}) +0.05 к прочности ({@link Attribute#ARMOR_TOUGHNESS}) за скрап</li>
 *   <li>Все предметы: +1 к макс. прочности за скрап</li>
 *   <li>Улучшение бесконечно — стоимость всегда 0 уровней</li>
 * </ul>
 * <p>
 * Количество улучшений хранится в PDC как {@code Keys.NETHERITE_UPGRADE} (Integer).
 */
public class NetheriteUpgradeListener implements Listener {

    private static final Set<Material> NETHERITE_WEAPONS = Set.of(
        Material.NETHERITE_SWORD,
        Material.MACE
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
        ALL_NETHERITE = new HashSet<>();
        ALL_NETHERITE.addAll(NETHERITE_WEAPONS);
        ALL_NETHERITE.addAll(NETHERITE_TOOLS);
        ALL_NETHERITE.addAll(NETHERITE_ARMOR);
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /** +0.1 flat за каждый скрап (ADD_NUMBER) */
    private static final double PER_SCRAP_BONUS = 0.1;
    private static final DecimalFormat DF = new DecimalFormat("0.0");

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;
        // Только незеритовый скрап. Слиток НЕ поддерживается — он конфликтует
        // с ванильной починкой в наковальне (Minecraft сам чинит предмет слитком).
        if (slot1.getType() != Material.NETHERITE_SCRAP) return;
        if (!ALL_NETHERITE.contains(slot0.getType())) return;

        ItemMeta meta0 = slot0.getItemMeta();
        if (meta0 == null) return;

        var pdc = meta0.getPersistentDataContainer();
        int existingUpgrades = pdc.getOrDefault(Keys.NETHERITE_UPGRADE, PersistentDataType.INTEGER, 0);

        int itemCount = slot1.getAmount();
        int newUpgrades = existingUpgrades + itemCount;

        // Clone and apply upgrades
        ItemStack result = slot0.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        var resultPdc = meta.getPersistentDataContainer();
        resultPdc.set(Keys.NETHERITE_UPGRADE, PersistentDataType.INTEGER, newUpgrades);

        // ⚠ Paper/Leaf НЕ заменяет модификатор с тем же key — кидает исключение.
        // Сначала удаляем старый модификатор по ключу, не трогая базовые атрибуты.
        NamespacedKey modKey = new NamespacedKey(Main.getInstance(), "netherite_upgrade");
        double upgradeAmount = newUpgrades * PER_SCRAP_BONUS;

        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            // Меч: +0.1 урона атаки за скрап
            removeOurModifier(meta, Attribute.ATTACK_DAMAGE, modKey);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            // Инструменты: +0.1 скорости копания за скрап
            removeOurModifier(meta, Attribute.BLOCK_BREAK_SPEED, modKey);
            meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_ARMOR.contains(slot0.getType())) {
            // Броня: +0.1 к защите и +0.05 к прочности брони за скрап
            removeOurModifier(meta, Attribute.ARMOR, modKey);
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY
            ));
            removeOurModifier(meta, Attribute.ARMOR_TOUGHNESS, modKey);
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                modKey, upgradeAmount * 0.5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY
            ));
        }

        // +1 к макс. прочности за каждый скрап (для всех незеритовых предметов)
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            damageable.setMaxDamage(damageable.getMaxDamage() + itemCount);
        }

        // Build lore: сохраняем существующие строки, заменяем строку улучшения
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        List<Component> filteredLore = new ArrayList<>();
        for (Component c : lore) {
            // Используем PlainText — вытаскивает ТОЛЬКО видимый текст, без форматирования.
            // В отличие от MM.serialize(), PlainText работает с ЛЮБЫМИ компонентами:
            // будь то MiniMessage, NBT-десериализованные, или Legacy.
            String text = PLAIN.serialize(c);
            // Удаляем старые строки незеритового улучшения
            if (!text.contains("✦ Незерит") && !text.contains("Незеритовое улучшение")) {
                filteredLore.add(c);
            }
        }

        // Формируем строку с ИТОГОВЫМ значением атрибута (база + все улучшения)
        String upgradeLine;
        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            double total = getTotalAttribute(meta, Attribute.ATTACK_DAMAGE, slot0.getType());
            upgradeLine = "<!italic><gradient:#8B4513:#DAA520>✦ Незерит — ⚔ " + DF.format(total) + " урона</gradient>";
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            double total = getTotalAttribute(meta, Attribute.BLOCK_BREAK_SPEED, slot0.getType());
            upgradeLine = "<!italic><gradient:#8B4513:#DAA520>✦ Незерит — ⛏ " + DF.format(total) + " скорости</gradient>";
        } else {
            double totalArmor = getTotalAttribute(meta, Attribute.ARMOR, slot0.getType());
            upgradeLine = "<!italic><gradient:#8B4513:#DAA520>✦ Незерит — 🛡 " + DF.format(totalArmor)
                + " защиты</gradient>";
        }

        filteredLore.add(MessageUtil.parse(upgradeLine));
        meta.lore(filteredLore);
        result.setItemMeta(meta);

        event.setResult(result);
        // Стоимость: 0 уровней опыта (бесконечные улучшения), потребляется itemCount скрапа
        setAnvilCost(inv, 0, itemCount);
    }

    /**
     * Возвращает базовое значение атрибута из config.yml.
     * Используется для расчёта итогового значения в лоре.
     */
    private static double getBaseValue(Material material) {
        var config = Main.getInstance().getConfig();
        String name = material.name();

        if (name.equals("NETHERITE_SWORD")) {
            return config.getDouble("netherite_upgrade.base_values.weapons.sword", 8);
        }
        if (name.equals("MACE")) {
            return config.getDouble("netherite_upgrade.base_values.weapons.mace", 5);
        }
        if (name.endsWith("_HELMET")) {
            return config.getDouble("netherite_upgrade.base_values.armor.helmet", 3);
        }
        if (name.endsWith("_CHESTPLATE")) {
            return config.getDouble("netherite_upgrade.base_values.armor.chestplate", 8);
        }
        if (name.endsWith("_LEGGINGS")) {
            return config.getDouble("netherite_upgrade.base_values.armor.leggings", 6);
        }
        if (name.endsWith("_BOOTS")) {
            return config.getDouble("netherite_upgrade.base_values.armor.boots", 3);
        }
        // Инструменты: block_break_speed
        return config.getDouble("netherite_upgrade.base_values.block_break_speed", 1);
    }

    /**
     * Удаляет модификатор с указанным ключом из атрибута, если он есть.
     * Используется перед addAttributeModifier, так как Paper/Leaf не заменяет
     * модификатор с тем же key, а кидает IllegalArgumentException.
     */
    private static void removeOurModifier(ItemMeta meta, Attribute attribute, NamespacedKey key) {
        var allMods = meta.getAttributeModifiers();
        if (allMods == null || allMods.isEmpty()) return;

        Multimap<Attribute, AttributeModifier> newMods = ArrayListMultimap.create();
        for (var entry : allMods.entries()) {
            Attribute attr = entry.getKey();
            AttributeModifier mod = entry.getValue();
            // Пропускаем только наш модификатор, остальные (включая базовые) сохраняем.
            // ⚠ mod.getKey() может быть null у модификаторов от других плагинов/старых версий
            if (attr == attribute && mod.getKey() != null && key.equals(mod.getKey())) {
                continue;
            }
            newMods.put(attr, mod);
        }
        meta.setAttributeModifiers(newMods);
    }

    /**
     * Суммирует базовое значение атрибута из config.yml
     * (3 для шлема, 8 для меча и т.д.) + все ADD_NUMBER модификаторы
     * от улучшений незеритом.
     */
    private static double getTotalAttribute(ItemMeta meta, Attribute attribute, Material material) {
        double total = getBaseValue(material);

        // Кастомные модификаторы от улучшений незеритом
        var mods = meta.getAttributeModifiers(attribute);
        if (mods != null) {
            for (AttributeModifier mod : mods) {
                if (mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    total += mod.getAmount();
                }
            }
        }

        return total;
    }

    /**
     * Устанавливает стоимость наковальни.
     * repairCost = 0 → бесплатно, никогда не становится "Too Expensive".
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
