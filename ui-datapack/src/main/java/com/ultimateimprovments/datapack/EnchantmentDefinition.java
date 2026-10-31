package com.ultimateimprovments.datapack;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.tag.TagKey;
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
 */
record EnchantmentDefinition(
        String id,
        String nameEn,
        String nameRu,
        ItemSet items,
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

    /**
     * How an enchantment's applicable items are expressed.
     * <p>
     * {@code RegistrySet} has <b>no union operation</b> — a set is either an explicit key
     * list or a single tag — so the three shapes below are all the API can express. The
     * old datapack tags mixed several vanilla tags, which is why {@link Union} exists: it
     * is materialised by reading the vanilla tags at compose time and is backed by
     * {@link Union#fallbackTag()} when that read returns nothing.
     */
    sealed interface ItemSet {

        /** An explicit list of item ids, fully inlined here (most enchantments). */
        record Inline(List<String> ids) implements ItemSet {}

        /**
         * A single tag passed straight to the builder. The tag object is a live reference
         * that the game fills afterwards, so this is the documented, always-safe shape.
         */
        record SingleTag(TagKey<ItemType> tag) implements ItemSet {}

        /**
         * The union of several vanilla tags plus a few explicit items — what the old
         * {@code ui:*_enchantable} datapack tags did. {@code fallbackTag} is used when the
         * tag contents cannot be read at compose time: it is a single tag, so the
         * enchantment still registers, just with a slightly narrower item set.
         */
        record Union(List<TagKey<ItemType>> tags, List<String> extraItems,
                     TagKey<ItemType> fallbackTag) implements ItemSet {}
    }

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

    private static List<String> append(List<String> base, String extra) {
        List<String> out = new ArrayList<>(base);
        out.add(extra);
        return List.copyOf(out);
    }

    // =========================
    // FACTORIES
    // =========================

    private static EnchantmentDefinition inline(String id, String nameEn, String nameRu,
                                                List<String> ids, int maxLevel, int anvilCost,
                                                EquipmentSlotGroup activeSlots) {
        return new EnchantmentDefinition(id, nameEn, nameRu,
                new ItemSet.Inline(ids), maxLevel, anvilCost, activeSlots);
    }

    private static EnchantmentDefinition singleTag(String id, String nameEn, String nameRu,
                                                   TagKey<ItemType> tag, int maxLevel, int anvilCost,
                                                   EquipmentSlotGroup activeSlots) {
        return new EnchantmentDefinition(id, nameEn, nameRu,
                new ItemSet.SingleTag(tag), maxLevel, anvilCost, activeSlots);
    }

    private static EnchantmentDefinition union(String id, String nameEn, String nameRu,
                                               List<TagKey<ItemType>> tags, List<String> extraItems,
                                               TagKey<ItemType> fallbackTag, int maxLevel, int anvilCost,
                                               EquipmentSlotGroup activeSlots) {
        return new EnchantmentDefinition(id, nameEn, nameRu,
                new ItemSet.Union(tags, extraItems, fallbackTag), maxLevel, anvilCost, activeSlots);
    }

    // =========================
    // THE ENCHANTMENTS
    // =========================

    /**
     * All 18 custom enchantments, keyed {@code ui:<id>}.
     * <p>
     * Fifteen need no datapack file at all: twelve inline their items, and three point at
     * the built-in {@code #minecraft:enchantable/durability} tag. The remaining three used
     * to ship a {@code ui:*_enchantable} file whose content was a union of several vanilla
     * tags; that union is now assembled from the vanilla tags themselves at compose time
     * (see {@link ItemSet.Union}), so no enchantment tag ships in the datapack anymore.
     */
    private static final List<EnchantmentDefinition> ALL = List.of(
            inline("aoe", "AoE", "Область",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            inline("attack_aoe", "Attack AoE", "Область атаки",
                    swordsAndAxes(), 10, 1, EquipmentSlotGroup.MAINHAND),
            inline("autosmelt", "AutoSmelt", "Автоплавка",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            union("blunting", "Curse of Blunting", "Проклятье затупленности",
                    List.of(ItemTypeTagKeys.ENCHANTABLE_WEAPON,
                            ItemTypeTagKeys.ENCHANTABLE_MINING,
                            ItemTypeTagKeys.ENCHANTABLE_MINING_LOOT),
                    List.of(),
                    ItemTypeTagKeys.ENCHANTABLE_WEAPON,
                    10, 8, EquipmentSlotGroup.MAINHAND),
            inline("container_stealing", "Container Stealing", "Кража контейнеров",
                    miningTools(), 10, 1, EquipmentSlotGroup.MAINHAND),
            singleTag("degradation", "Curse of Degradation", "Проклятье деградации",
                    ItemTypeTagKeys.ENCHANTABLE_DURABILITY, 10, 8, EquipmentSlotGroup.ANY),
            singleTag("disappearance", "Curse of Disappearance", "Проклятье исчезновения",
                    ItemTypeTagKeys.ENCHANTABLE_DURABILITY, 10, 8, EquipmentSlotGroup.ANY),
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
            singleTag("repairing", "Repairing", "Самовосстановление",
                    ItemTypeTagKeys.ENCHANTABLE_DURABILITY, 10, 1, EquipmentSlotGroup.ANY),
            union("self_destruct", "Curse of Self-Destruct", "Проклятье самоуничтожения",
                    List.of(ItemTypeTagKeys.ENCHANTABLE_EQUIPPABLE,
                            ItemTypeTagKeys.ENCHANTABLE_DURABILITY),
                    List.of("minecraft:book", "minecraft:written_book"),
                    ItemTypeTagKeys.ENCHANTABLE_DURABILITY,
                    1, 8, EquipmentSlotGroup.ANY),
            inline("treecapitator", "TreeCapitator", "Лесоруб",
                    toolItems("axe"), 1, 1, EquipmentSlotGroup.MAINHAND),
            inline("veinminer", "VeinMiner", "Рудокоп",
                    toolItems("pickaxe"), 1, 1, EquipmentSlotGroup.MAINHAND),
            union("vulnerability", "Curse of Vulnerability", "Проклятье уязвимости",
                    List.of(ItemTypeTagKeys.ENCHANTABLE_ARMOR,
                            ItemTypeTagKeys.ENCHANTABLE_HEAD_ARMOR,
                            ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR,
                            ItemTypeTagKeys.ENCHANTABLE_LEG_ARMOR,
                            ItemTypeTagKeys.ENCHANTABLE_FOOT_ARMOR),
                    List.of("minecraft:elytra"),
                    ItemTypeTagKeys.ENCHANTABLE_ARMOR,
                    10, 8, EquipmentSlotGroup.ANY)
    );

    static List<EnchantmentDefinition> all() {
        return ALL;
    }
}
