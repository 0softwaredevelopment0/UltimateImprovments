package com.mcplugin.energy.machines.assembler;

import com.mcplugin.core.Keys;
import com.mcplugin.mechanics.features.omniscanner.OmniscannerManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Все рецепты для «Создатель предметов» (Item Creator).
 * Каждый рецепт: 3x3 матрица ингредиентов + результат (ItemStack с PDC, лором и т.д.).
 * Рецепты проверяются по порядку — первый подходящий выигрывает.
 */
public class ItemCreatorRecipe {

    public record Recipe(
            String name,
            ItemStack[] matrix,   // 9 слотов (row-major), null = пусто
            ItemStack result
    ) {}

    private static final List<Recipe> recipes = new ArrayList<>();

    static {
        registerAll();
    }

    private ItemCreatorRecipe() {}

    private static void registerAll() {
        // 1. Omniscanner
        add(recipe("Omniscanner",
                "DND", "NEN", "DND",
                'D', Material.DIAMOND_BLOCK,
                'N', Material.NETHERITE_INGOT,
                'E', Material.ENDER_EYE,
                OmniscannerManager.createItem()));

        // 2. Photon Cannon (Plasma)
        add(recipe("Photon Cannon",
                " E ", "EDE", "EEE",
                'E', Material.ECHO_SHARD,
                'D', Material.DIAMOND,
                pdcItem(Material.WARPED_FUNGUS_ON_A_STICK,
                        "<white>Photon Cannon *</white>",
                        "<gray>Strange gun shoots with echo shards.</gray>",
                        Keys.PLASMA)));

        // 3. Electro Shoker
        add(recipe("Electro Shoker",
                " R ", "RCR", " R ",
                'R', Material.REDSTONE_BLOCK,
                'C', Material.COPPER_BLOCK,
                pdcItem(Material.WARPED_FUNGUS_ON_A_STICK,
                        "<aqua>Electro Shoker *</aqua>",
                        "<gray>Stuns enemies with electricity.</gray>",
                        Keys.SHOCKER)));

        // 4. Multimeter
        add(recipe("Multimeter",
                "IDI", "DCD", "IDI",
                'I', Material.IRON_INGOT,
                'D', Material.DIAMOND,
                'C', Material.CLOCK,
                pdcItem(Material.CLOCK,
                        "<white>Multimeter *</white>",
                        "<gray>Inspect energy nodes and their connections.</gray>",
                        Keys.MULTIMETER)));

        // 5. Metal Detector
        add(recipe("Metal Detector",
                "  I", " S ", "S S",
                'I', Material.IRON_INGOT,
                'S', Material.STICK,
                pdcItem(Material.STICK,
                        "<white>Metal Detector *</white>",
                        "<gray>Scans for metal blocks and items nearby.</gray>",
                        Keys.METAL_DETECTOR)));

        // 6. Ore Finder
        add(recipe("Ore Finder",
                "RCR", "CEC", "RCR",
                'R', Material.REDSTONE,
                'C', Material.COMPASS,
                'E', Material.EMERALD,
                pdcItem(Material.COMPASS,
                        "<white>Ore Finder *</white>",
                        "<gray>Scans chunk for ores.</gray>",
                        Keys.ORE_FINDER)));

        // 7. Mob Finder
        add(recipe("Mob Finder",
                "RCR", "CEC", "RCR",
                'R', Material.ROTTEN_FLESH,
                'C', Material.COMPASS,
                'E', Material.ENDER_PEARL,
                pdcItem(Material.COMPASS,
                        "<white>Mob Finder *</white>",
                        "<gray>Scans chunk for entities.</gray>",
                        Keys.MOB_FINDER)));

        // 8. Health Meter
        add(recipe("Health Meter",
                "RHR", "HCH", "RHR",
                'R', Material.REDSTONE,
                'H', Material.HEART_OF_THE_SEA,
                'C', Material.COMPASS,
                pdcItem(Material.COMPASS,
                        "<white>Health Meter *</white>",
                        "<gray>Check entity health.</gray>",
                        Keys.HEALTH_METER)));

        // 9. Portable Radar
        add(recipe("Portable Radar",
                "RCR", "CEC", "RCR",
                'R', Material.IRON_INGOT,
                'C', Material.COMPASS,
                'E', Material.ENDER_PEARL,
                pdcItem(Material.COMPASS,
                        "<white>Portable Radar *</white>",
                        "<gray>Find nearby entities.</gray>",
                        Keys.RADAR)));

        // 10. Lead Ingot
        add(recipe("Lead Ingot",
                "III", "INI", "III",
                'I', Material.IRON_INGOT,
                'N', Material.NETHERITE_SCRAP,
                pdcItem(Material.IRON_INGOT,
                        "<gray>Lead Ingot *</gray>",
                        "<gray>Radiation shielding material.</gray>",
                        Keys.LEAD_INGOT)));

        // 11. Lead Shield
        add(recipe("Lead Shield",
                "LLL", "LSL", " L ",
                'L', pdcItem(Material.IRON_INGOT, "<gray>Lead Ingot *</gray>", "", Keys.LEAD_INGOT),
                'S', Material.SHIELD,
                pdcItem(Material.SHIELD,
                        "<gray>Lead Shield *</gray>",
                        "<gray>Protects from radiation.</gray>",
                        Keys.LEAD_SHIELD)));

        // 12. Concrete Bucket
        add(recipe("Concrete Bucket",
                "CBC", "BWB", "CBC",
                'C', Material.CYAN_CONCRETE_POWDER,
                'B', Material.BUCKET,
                'W', Material.WATER_BUCKET,
                pdcItem(Material.BUCKET,
                        "<white>Concrete Bucket *</white>",
                        "<gray>Place instant concrete.</gray>",
                        Keys.CONCRETE_BUCKET)));

        // 13. Portable Ender Chest
        add(recipe("Portable Ender Chest",
                "OEO", "ECE", "OEO",
                'O', Material.OBSIDIAN,
                'E', Material.ENDER_PEARL,
                'C', Material.ENDER_CHEST,
                portableEnderChest()));

        // 14. Chunk Loader
        add(recipe("Chunk Loader",
                "DED", "EGE", "DED",
                'D', Material.DIAMOND,
                'E', Material.EMERALD_BLOCK,
                'G', Material.GOLD_BLOCK,
                chunkLoaderItem()));

        // 15. Entity Locator
        add(recipe("Entity Locator",
                "RCR", "CEC", "RCR",
                'R', Material.REDSTONE,
                'C', Material.COMPASS,
                'E', Material.ECHO_SHARD,
                pdcItem(Material.COMPASS,
                        "<white>Entity Locator *</white>",
                        "<gray>Points to nearest entity.</gray>",
                        Keys.LOCATOR)));

        // 16. Antimatter
        add(recipe("Antimatter",
                "RCR", "CSC", "RCR",
                'R', Material.REDSTONE_BLOCK,
                'C', Material.COPPER_BLOCK,
                'S', Material.NETHER_STAR,
                pdcItem(Material.NETHER_STAR,
                        "<light_purple>Antimatter *</light_purple>",
                        "<gray>Dangerous substance.</gray>",
                        Keys.ANTIMATTER)));

        // 17. Particle Ring (GLASS)
        add(recipe("Particle Ring",
                "TCT", "C C", "TCT",
                'T', Material.TUFF,
                'C', Material.COPPER_INGOT,
                particleBlockItem(Material.GLASS,
                        "<white>Particle Ring *</white>",
                        "<gray>Guides particles along the accelerator path.</gray>",
                        "particle_ring")));

        // 18. Particle Engine (TUFF_BRICKS)
        add(recipe("Particle Engine",
                "RBR", "BCB", "RBR",
                'R', Material.REDSTONE,
                'B', Material.TUFF_BRICKS,
                'C', Material.COPPER_BLOCK,
                particleBlockItem(Material.TUFF_BRICKS,
                        "<white>Particle Engine *</white>",
                        "<gray>Accelerates particles. Requires 1000⚡ buffer and redstone.</gray>",
                        "particle_engine")));

        // 19. Particle Speed Sensor (POLISHED_DIORITE)
        add(recipe("Particle Speed Sensor",
                "QSQ", "SMS", "QSQ",
                'Q', Material.QUARTZ,
                'S', Material.POLISHED_DIORITE,
                'M', Material.CLOCK,
                particleBlockItem(Material.POLISHED_DIORITE,
                        "<white>Particle Speed Sensor *</white>",
                        "<gray>Measures particle speed (0-99.999% light speed).</gray>",
                        "particle_sensor")));

        // 20. Particle Injector (REINFORCED_DEEPSLATE)
        add(recipe("Particle Injector",
                "SDS", "DID", "SDS",
                'S', Material.CRYING_OBSIDIAN,
                'D', Material.REINFORCED_DEEPSLATE,
                'I', Material.IRON_BLOCK,
                particleBlockItem(Material.REINFORCED_DEEPSLATE,
                        "<white>Particle Injector *</white>",
                        "<gray>Right-click with any item to inject it as a particle.</gray>",
                        "particle_injector")));

        // 21. Generator (BLAST_FURNACE с PDC)
        add(recipe("Generator",
                "IBI", "BFB", "IBI",
                'I', Material.IRON_BLOCK,
                'B', Material.BLAST_FURNACE,
                'F', Material.FURNACE,
                generatorItem()));
    }

    private static ItemStack generatorItem() {
        return com.mcplugin.energy.generation.basic.GeneratorManager.createGeneratorItem();
    }

    // =========================
    // RECIPE MATCHING
    // =========================

    /**
     * Ищет рецепт, подходящий под 9 слотов крафтера.
     * @param grid 9 ItemStack (слоты 0-8 CRAFTER)
     * @return Recipe если найден, null если нет
     */
    public static Recipe match(ItemStack[] grid) {
        for (Recipe r : recipes) {
            if (matches(r.matrix(), grid)) {
                return r;
            }
        }
        return null;
    }

    private static boolean matches(ItemStack[] expected, ItemStack[] actual) {
        for (int i = 0; i < 9; i++) {
            ItemStack e = expected[i];
            ItemStack a = actual[i];

            if (e == null || e.getType().isAir()) {
                if (a != null && !a.getType().isAir()) return false;
            } else {
                if (a == null || a.getType().isAir()) return false;
                if (e.getType() != a.getType()) return false;
                if (e.hasItemMeta() && a.hasItemMeta()) {
                    // Check PDC keys for custom ingredient matching
                    var ePdc = e.getItemMeta().getPersistentDataContainer();
                    var aPdc = a.getItemMeta().getPersistentDataContainer();
                    for (var key : ePdc.getKeys()) {
                        if (!aPdc.has(key, PersistentDataType.BYTE)) return false;
                        Byte eVal = ePdc.get(key, PersistentDataType.BYTE);
                        Byte aVal = aPdc.get(key, PersistentDataType.BYTE);
                        if (!Objects.equals(eVal, aVal)) return false;
                    }
                }
            }
        }
        return true;
    }

    // =========================
    // HELPERS
    // =========================

    private static void add(Recipe r) {
        recipes.add(r);
    }

    /** Создаёт рецепт из строковой матрицы и мапы ингредиентов. */
    private static Recipe recipe(String name, String row1, String row2, String row3, Object... args) {
        // Parse args into char->ItemStack map
        Map<Character, ItemStack> ingredientMap = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i] instanceof Character ch && args[i + 1] instanceof ItemStack is) {
                ingredientMap.put(ch, is);
            } else if (args[i] instanceof Character ch && args[i + 1] instanceof Material mat) {
                ingredientMap.put(ch, new ItemStack(mat));
            }
        }

        String[] rows = {row1, row2, row3};
        ItemStack[] matrix = new ItemStack[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                char ch = rows[r].charAt(c);
                matrix[r * 3 + c] = ch == ' ' ? null : ingredientMap.get(ch);
            }
        }

        ItemStack result = (ItemStack) args[args.length - 1];
        return new Recipe(name, matrix, result);
    }

    /** Создаёт ItemStack с PDC тегом (BYTE = 1). */
    private static ItemStack pdcItem(Material mat, String name, String lore, org.bukkit.NamespacedKey key) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic>" + name));
            if (!lore.isEmpty()) {
                meta.lore(List.of(MessageUtil.parse("<!italic>" + lore)));
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Создаёт блоковый предмет ускорителя с PDC тегом 'particle_block'. */
    private static ItemStack particleBlockItem(Material mat, String name, String lore, String blockType) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic>" + name));
            if (!lore.isEmpty()) {
                meta.lore(List.of(MessageUtil.parse("<!italic>" + lore)));
            }
            // Используем NamespacedKey для particle_block
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("mcplugin", "particle_block");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, blockType);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack portableEnderChest() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><white>Портативное хранилище</white>"));
            meta.lore(List.of(MessageUtil.parse("<!italic><gray>Поставьте и сломайте чтобы прочитать описание.</gray>")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack chunkLoaderItem() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><gold>✦ Чанклоадер ✦</gold>"));
            meta.lore(List.of(
                    MessageUtil.parse("<!italic><gray>При установке чанк остается загруженным</gray>"),
                    MessageUtil.parse("<!italic><gray>Разрушить — получить предмет обратно</gray>")
            ));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey("mcplugin", "is_chunk_loader"),
                    PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Проверяет, есть ли у предмета PDC тег particle_block. */
    public static boolean isParticleBlock(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(new org.bukkit.NamespacedKey("mcplugin", "particle_block"), PersistentDataType.STRING);
    }

    /** Получает тип particle_block из PDC предмета. */
    public static String getParticleBlockType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(new org.bukkit.NamespacedKey("mcplugin", "particle_block"), PersistentDataType.STRING);
    }
}
