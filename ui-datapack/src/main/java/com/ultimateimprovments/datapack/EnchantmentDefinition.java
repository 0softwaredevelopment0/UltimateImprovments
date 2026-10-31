package com.ultimateimprovments.datapack;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * EnchantmentDefinition — the data half of the enchantment migration from the bundled
 * datapack to {@code RegistryEvents} (the code half is
 * {@link EnchantmentRegistryBootstrap}).
 * <p>
 * Every entry mirrors the former {@code data/ui/enchantment/<id>.json} file: display
 * name in both languages, the item set it can be applied to, max level, anvil cost and
 * active equipment slots. Values shared by all 18 enchantments (weight 1, cost 25/25 ->
 * 75/50) live in the constants below instead of being repeated per row.
 * <p>
 * Item sets come in two flavours, because {@code RegistrySet} has <b>no union
 * operation</b> — a set is either an explicit list of item keys or a single tag:
 * <ul>
 *   <li>{@link #items} — an explicit list, inlined here. Used wherever the old item tag
 *       listed plain items.</li>
 *   <li>{@link #itemTag} — a reference to a tag, resolved through
 *       {@code RegistryComposeEvent.getOrCreateTag}. Only used by the three entries whose
 *       old tag mixed several #{@code minecraft:enchantable/*} tags (or tags plus items),
 *       which cannot be expressed as a flat key list without copying vanilla tag
 *       content. Those three tags stay in the datapack, see the enum-free list at the
 *       bottom of this class for which ones.</li>
 * </ul>
 * Exactly one of the two fields is set per entry.
 */
record EnchantmentDefinition(
        String id,
        String nameEn,
        String nameRu,
        List<String> items,
        TagKey<ItemType> itemTag,
        int maxLevel,
        int anvilCost,
        EquipmentSlotGroup activeSlots) {

    /** Weight used by every custom enchantment (all were {@code "weight": 1}). */
    static final int WEIGHT = 1;

    /** Enchanting-table cost floor: {@code min_cost.base} / {@code per_level_above_first}. */
    static final int MIN_COST_BASE = 25;
    static final int MIN_COST_PER_LEVEL = 25;

    /** Enchanting-table cost ceiling: {@code max_cost.base} / {@code per_level_above_first}. */
    static final int MAX_COST_BASE = 75;
    static final int MAX_COST_PER_LEVEL = 50;

    // =========================
    // ITEM LISTS
    // =========================

    /** Tool materials in the order the old item tags used (copper tools exist, copper armour does not). */
    private static final List<String> TOOL_MATERIALS =
            List.of("wooden", "stone", "golden", "iron", "diamond", "netherite", "copper");

    /** Materials that have a sword — there is no {@code copper_sword}, matching the old tag. */
    private static final List<String> SWORD_MATERIALS =
            List.of("wooden", "stone", "golden", "iron", "diamond", "netherite");

    /** Armour materials — copper armour does not exist. */
    private static final List<String> ARMOR_MATERIALS =
            List.of("leather", "chainmail", "iron", "golden", "diamond", "netherite");

    private static List<String> toolItems(String shape) {
        List<String> out = new ArrayList<>(TOOL_MATERIALS.size());
        for (String material : TOOL_MATERIALS) out.add("minecraft:" + material + "_" + shape);
        return List.copyOf(out);
    }

    private static List<String> armorItems(String piece) {
        List<String> out = new ArrayList<>(ARMOR_MATERIALS.size());
        for (String material : ARMOR_MATERIALS) out.add("minecraft:" + material + "_" + piece);
        return List.copyOf(out);
    }

    /** 28 mining tools: every pickaxe, then every shovel, axe and hoe. */
    private static List<String> miningTools() {
        List<String> out = new ArrayList<>();
        for (String shape : List.of("pickaxe", "shovel", "axe", "hoe")) out.addAll(toolItems(shape));
        return List.copyOf(out);
    }

    /** 13 weapons: every sword, then every axe. */
    private static List<String> swordsAndAxes() {
        List<String> out = new ArrayList<>();
        for (String material : SWORD_MATERIALS) out.add("minecraft:" + material + "_sword");
        out.addAll(toolItems("axe"));
        return List.copyOf(out);
    }

    /** 24 armour pieces: helmets, chestplates, leggings, boots. */
    private static List<String> armorPieces() {
        List<String> out = new ArrayList<>();
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) out.addAll(armorItems(piece));
        return List.copyOf(out);
    }

    /** 7 chest-slot items: every chestplate plus the elytra. */
    private static List<String> chestplatesWithElytra() {
        List<String> out = new ArrayList<>(armorItems("chestplate"));
        out.add("minecraft:elytra");
        return List.copyOf(out);
    }

    // =========================
    // FACTORIES
    // =========================

    private static EnchantmentDefinition inline(String id, String nameEn, String nameRu,
                                                List<String> items, int maxLevel, int anvilCost,
                                                EquipmentSlotGroup activeSlots) {
        return new EnchantmentDefinition(id, nameEn, nameRu, items, null, maxLevel, anvilCost, activeSlots);
    }

    private static EnchantmentDefinition tag(String id, String nameEn, String nameRu,
                                             TagKey<ItemType> itemTag, int maxLevel, int anvilCost,
                                             EquipmentSlotGroup activeSlots) {
        return new EnchantmentDefinition(id, nameEn, nameRu, List.of(), itemTag, maxLevel, anvilCost, activeSlots);
    }

    /** Builds a {@code ui:<path>} item-tag key, i.e. a tag that still ships in the datapack. */
    private static TagKey<ItemType> customTag(String path) {
        return TagKey.create(RegistryKey.ITEM, Key.key("ui", path));
    }

    // =========================
    // THE ENCHANTMENTS
    // =========================

    /**
     * All 18 custom enchantments, keyed {@code ui:<id>}.
     * <p>
     * The last three keep a datapack tag because their item sets are unions the registry
     * API cannot express: {@code blunting} = three vanilla {@code #enchantable/*} tags,
     * {@code self_destruct} = two vanilla tags + two book items, {@code vulnerability} =
     * five vanilla armour tags + the elytra. The other fifteen are fully inlined:
     * {@code degradation}, {@code disappearance} and {@code repairing} use the built-in
     * {@code #minecraft:enchantable/durability} tag directly, so they need no file either.
     */
    private static final List<EnchantmentDefinition> ALL = List.of(
            inline("aoe", "AoE", "Область",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            inline("attack_aoe", "Attack AoE", "Область атаки",
                    swordsAndAxes(), 10, 1, EquipmentSlotGroup.MAINHAND),
            inline("autosmelt", "AutoSmelt", "Автоплавка",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            tag("blunting", "Curse of Blunting", "Проклятье затупленности",
                    customTag("blunting_enchantable"), 10, 8, EquipmentSlotGroup.MAINHAND),
            inline("container_stealing", "Container Stealing", "Кража контейнеров",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            tag("degradation", "Curse of Degradation", "Проклятье деградации",
                    io.papermc.paper.registry.keys.tags.ItemTypeTagKeys.ENCHANTABLE_DURABILITY,
                    10, 8, EquipmentSlotGroup.ANY),
            tag("disappearance", "Curse of Disappearance", "Проклятье исчезновения",
                    io.papermc.paper.registry.keys.tags.ItemTypeTagKeys.ENCHANTABLE_DURABILITY,
                    10, 8, EquipmentSlotGroup.ANY),
            inline("flight", "Flight", "Полёт",
                    chestplatesWithElytra(), 1, 1, EquipmentSlotGroup.ARMOR),
            inline("igniting", "Igniting", "Воспламенение",
                    append(armorPieces(), "minecraft:elytra"), 10, 1, EquipmentSlotGroup.ARMOR),
            inline("item_stealing", "Item Stealing", "Кража предметов",
                    List.of("minecraft:fishing_rod"), 10, 1, EquipmentSlotGroup.MAINHAND),
            inline("lava_walker", "Lava Walker", "Лавоход",
                    armorItems("boots"), 10, 1, EquipmentSlotGroup.ARMOR),
            inline("levitation", "Levitation", "Левитация",
                    chestplatesWithElytra(), 1, 1, EquipmentSlotGroup.ARMOR),
            inline("magnet", "Magnet", "Притяжение",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            tag("repairing", "Repairing", "Самовосстановление",
                    io.papermc.paper.registry.keys.tags.ItemTypeTagKeys.ENCHANTABLE_DURABILITY,
                    10, 1, EquipmentSlotGroup.ANY),
            tag("self_destruct", "Curse of Self-Destruct", "Проклятье самоуничтожения",
                    customTag("self_destruct_enchantable"), 1, 8, EquipmentSlotGroup.ANY),
            inline("treecapitator", "TreeCapitator", "Лесоруб",
                    toolItems("axe"), 1, 1, EquipmentSlotGroup.MAINHAND),
            inline("veinminer", "VeinMiner", "Рудокоп",
                    toolItems("pickaxe"), 1, 1, EquipmentSlotGroup.MAINHAND),
            tag("vulnerability", "Curse of Vulnerability", "Проклятье уязвимости",
                    customTag("vulnerability_enchantable"), 10, 8, EquipmentSlotGroup.ANY)
    );

    private static List<String> append(List<String> base, String extra) {
        List<String> out = new ArrayList<>(base);
        out.add(extra);
        return List.copyOf(out);
    }

    static List<EnchantmentDefinition> all() {
        return ALL;
    }
}
