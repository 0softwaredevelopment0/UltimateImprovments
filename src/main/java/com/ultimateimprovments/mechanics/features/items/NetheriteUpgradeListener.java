package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
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
import org.bukkit.inventory.EquipmentSlot;
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
 * ⚔ Netherite upgrade — upgrading netherite items in an anvil with scrap.
 * <p>
 * <b>Mechanics:</b>
 * <ul>
 *   <li>Slot 1: a netherite item (sword, tool, armor)</li>
 *   <li>Slot 2: netherite scrap (NOT an ingot — the ingot conflicts with repairing)</li>
 *   <li>Weapons: +0.1 attack damage ({@link Attribute#ATTACK_DAMAGE}) per scrap</li>
 *   <li>Tools: +0.1 mining speed ({@link Attribute#BLOCK_BREAK_SPEED}) per scrap</li>
 *   <li>Armor: +0.1 armor ({@link Attribute#ARMOR}) +0.05 toughness ({@link Attribute#ARMOR_TOUGHNESS}) +0.1 knockback resistance ({@link Attribute#KNOCKBACK_RESISTANCE}) per scrap</li>
 *   <li>All items: +1 max durability per scrap</li>
 *   <li>The upgrade is infinite — the cost is always 0 levels</li>
 * </ul>
 * <p>
 * The upgrade count is stored in PDC as {@code Keys.NETHERITE_UPGRADE} (Integer).
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
    /** +0.1 flat per scrap (ADD_NUMBER) */
    private static final double PER_SCRAP_BONUS = 0.1;
    private static final DecimalFormat DF = new DecimalFormat("0.0");

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;
        // Only netherite scrap. The ingot is NOT supported — it conflicts
        // with vanilla anvil repairing (Minecraft repairs items with ingots itself).
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

        // ⚠ Paper/Leaf does NOT replace a modifier with the same key — it throws.
        // First remove the old modifier by key without touching the base attributes.
        NamespacedKey modKey = new NamespacedKey(Main.getInstance(), "netherite_upgrade");
        double upgradeAmount = newUpgrades * PER_SCRAP_BONUS;

        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            // Sword: +0.1 attack damage per scrap
            removeOurModifier(meta, Attribute.ATTACK_DAMAGE, modKey, slot0.getType());
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            // Tools: +0.1 mining speed per scrap
            removeOurModifier(meta, Attribute.BLOCK_BREAK_SPEED, modKey, slot0.getType());
            meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND
            ));
        } else if (NETHERITE_ARMOR.contains(slot0.getType())) {
            // Armor: +0.1 armor and +0.05 toughness per scrap
            // Important: NOT EquipmentSlotGroup.ANY — otherwise armor gives protection even in hand!
            EquipmentSlotGroup armorSlot = getArmorSlotGroup(slot0.getType());
            removeOurModifier(meta, Attribute.ARMOR, modKey, slot0.getType());
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, armorSlot
            ));
            removeOurModifier(meta, Attribute.ARMOR_TOUGHNESS, modKey, slot0.getType());
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                modKey, upgradeAmount * 0.5, AttributeModifier.Operation.ADD_NUMBER, armorSlot
            ));
            removeOurModifier(meta, Attribute.KNOCKBACK_RESISTANCE, modKey, slot0.getType());
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                modKey, upgradeAmount, AttributeModifier.Operation.ADD_NUMBER, armorSlot
            ));
        }

        // +1 max durability per scrap (for all netherite items)
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            damageable.setMaxDamage(damageable.getMaxDamage() + itemCount);
        }

        // Build lore: keep existing lines, replace the upgrade line
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        List<Component> filteredLore = new ArrayList<>();
        for (Component c : lore) {
            // Use PlainText — extracts ONLY the visible text, without formatting.
            // Unlike MM.serialize(), PlainText works with ANY components:
            // be it MiniMessage, NBT-deserialized, or Legacy.
            String text = PLAIN.serialize(c);
            // Remove old netherite upgrade lines
            if (!text.contains("✦ Незерит") && !text.contains("Незеритовое улучшение")) {
                filteredLore.add(c);
            }
        }

        // Build the line with the FINAL attribute value (base + all upgrades)
        String upgradeLine;
        if (NETHERITE_WEAPONS.contains(slot0.getType())) {
            double total = getTotalAttribute(meta, Attribute.ATTACK_DAMAGE, slot0.getType());
            upgradeLine = "<!italic><gradient:#8B4513:#DAA520>✦ Незерит — ⚔ " + DF.format(total) + " урона</gradient>";
        } else if (NETHERITE_TOOLS.contains(slot0.getType())) {
            double total = getTotalAttribute(meta, Attribute.BLOCK_BREAK_SPEED, slot0.getType());
            upgradeLine = "<!italic><gradient:#8B4513:#DAA520>✦ Незерит — ⛏ " + DF.format(total) + " скорости</gradient>";
        } else {
            double totalArmor = getTotalAttribute(meta, Attribute.ARMOR, slot0.getType());
            double totalKnockback = newUpgrades * PER_SCRAP_BONUS;
            upgradeLine = "<!italic><gradient:#8B4513:#DAA520>✦ Незерит — 🛡 " + DF.format(totalArmor)
                + " защиты | ↺ " + DF.format(totalKnockback) + " отбрасывания</gradient>";
        }

        filteredLore.add(MessageUtil.parse(upgradeLine));
        meta.lore(filteredLore);
        result.setItemMeta(meta);

        event.setResult(result);
        // Cost: 0 experience levels (infinite upgrades), consumes itemCount of scrap
        setAnvilCost(inv, 0, itemCount);
    }

    /**
     * Returns the base attribute value from config.yml.
     * Used to compute the final value shown in the lore.
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
        // Tools: block_break_speed
        return config.getDouble("netherite_upgrade.base_values.block_break_speed", 1);
    }

    /**
     * Determines the EquipmentSlot by material (armor → HEAD/CHEST/LEGS/FEET, everything else → HAND).
     * Needed to fetch the material's default modifiers.
     */
    private static EquipmentSlot getDefaultSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return EquipmentSlot.HAND;
    }

    /**
     * Determines the EquipmentSlotGroup for armor (HEAD/CHEST/LEGS/FEET).
     * Used instead of ANY so armor only grants protection when equipped.
     */
    private static EquipmentSlotGroup getArmorSlotGroup(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return EquipmentSlotGroup.ANY; // fallback
    }

    /**
     * Removes the modifier with the given key from the attribute, if present.
     * Used before addAttributeModifier because Paper/Leaf does not replace
     * a modifier with the same key — it throws IllegalArgumentException.
     * <p>
     * Important: after setAttributeModifiers() Paper clears internalCustomAttributes,
     * so we explicitly add the material's default modifiers back.
     */
    private static void removeOurModifier(ItemMeta meta, Attribute attribute, NamespacedKey key, Material material) {
        var allMods = meta.getAttributeModifiers();

        Multimap<Attribute, AttributeModifier> newMods = ArrayListMultimap.create();

        // 1) Copy all existing custom modifiers EXCEPT ours
        if (allMods != null && !allMods.isEmpty()) {
            for (var entry : allMods.entries()) {
                AttributeModifier mod = entry.getValue();
                if (entry.getKey() == attribute && mod.getKey() != null && key.equals(mod.getKey())) {
                    continue;
                }
                newMods.put(entry.getKey(), mod);
            }
        }

        // 2) Add the material's default modifiers if they're not in newMods yet
        //    (Paper/Leaf clears internalCustomAttributes on setAttributeModifiers(),
        //     which may hold the material's base attributes)
        EquipmentSlot slot = getDefaultSlot(material);
        var defaults = material.getDefaultAttributeModifiers(slot);
        if (defaults != null) {
            for (var entry : defaults.entries()) {
                AttributeModifier mod = entry.getValue();
                if (!newMods.containsEntry(entry.getKey(), mod)) {
                    newMods.put(entry.getKey(), mod);
                }
            }
        }

        meta.setAttributeModifiers(newMods);
    }

    /**
     * Sums the base attribute value from config.yml
     * (3 for a helmet, 8 for a sword, etc.) + all ADD_NUMBER modifiers
     * from the netherite upgrades.
     */
    private static double getTotalAttribute(ItemMeta meta, Attribute attribute, Material material) {
        double total = getBaseValue(material);

        // Custom modifiers from the netherite upgrades
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
     * Sets the anvil cost.
     * repairCost = 0 → free, never becomes "Too Expensive".
     * repairCostAmount = how much scrap is consumed.
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
