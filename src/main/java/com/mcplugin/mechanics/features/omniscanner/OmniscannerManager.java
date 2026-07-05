package com.mcplugin.mechanics.features.omniscanner;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Barrel;
import org.bukkit.block.Hopper;
import org.bukkit.block.Dropper;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import org.bukkit.entity.ChestedHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔭 Omniscanner — админский предмет для сканирования блоков, предметов и сущностей.
 * <p>
 * ПКМ — запустить сканирование по настроенным параметрам.
 * Shift+ПКМ — открыть GUI настройки (типы блоков, предметов, сущностей, радиус).
 */
public class OmniscannerManager implements Listener {

    private static final long COOLDOWN_MS = 500L;
    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    // ========================================================================
    // ITEM CREATION
    // ========================================================================

    /**
     * Создаёт предмет Omniscanner.
     */
    public static ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<!italic><gradient:#FF6B6B:#FFD93D>🔭 Omniscanner</gradient>"));
        meta.lore(List.of(
                MessageUtil.parse("<!italic><gray>Админский сканер</gray>"),
                MessageUtil.parse("<!italic><gold>Радиус: </gold><white>16</white>"),
                MessageUtil.parse("<!italic><gray>Блоки: </gray><white>все</white>"),
                MessageUtil.parse("<!italic><gray>Предметы: </gray><white>все</white>"),
                MessageUtil.parse("<!italic><gray>Сущности: </gray><white>все</white>"),
                Component.empty(),
                MessageUtil.parse("<!italic><gold>ПКМ</gold><gray> — Сканировать</gray>"),
                MessageUtil.parse("<!italic><gold>Shift+ПКМ</gold><gray> — Настройки</gray>")
        ));

        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.OMNISCANNER, PersistentDataType.BYTE, (byte) 1);
        pdc.set(Keys.OMNISCANNER_RADIUS, PersistentDataType.INTEGER, 16);
        pdc.set(Keys.OMNISCANNER_BLOCKS, PersistentDataType.STRING, "");
        pdc.set(Keys.OMNISCANNER_ITEMS, PersistentDataType.STRING, "");
        pdc.set(Keys.OMNISCANNER_ENTITIES, PersistentDataType.STRING, "");

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isOmniscanner(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.OMNISCANNER, PersistentDataType.BYTE);
    }

    // ========================================================================
    // PDC HELPERS
    // ========================================================================

    public static int getRadius(ItemStack item) {
        return getInt(item, Keys.OMNISCANNER_RADIUS, 16);
    }

    public static void setRadius(ItemStack item, int radius) {
        setInt(item, Keys.OMNISCANNER_RADIUS, Math.max(1, Math.min(500, radius)));
        updateLore(item);
    }

    public static Set<String> getBlockTypes(ItemStack item) {
        return getSet(item, Keys.OMNISCANNER_BLOCKS);
    }

    public static void setBlockTypes(ItemStack item, Set<String> types) {
        setSet(item, Keys.OMNISCANNER_BLOCKS, types);
        updateLore(item);
    }

    public static Set<String> getItemTypes(ItemStack item) {
        return getSet(item, Keys.OMNISCANNER_ITEMS);
    }

    public static void setItemTypes(ItemStack item, Set<String> types) {
        setSet(item, Keys.OMNISCANNER_ITEMS, types);
        updateLore(item);
    }

    public static Set<String> getEntityTypes(ItemStack item) {
        return getSet(item, Keys.OMNISCANNER_ENTITIES);
    }

    public static void setEntityTypes(ItemStack item, Set<String> types) {
        setSet(item, Keys.OMNISCANNER_ENTITIES, types);
        updateLore(item);
    }

    private static int getInt(ItemStack item, org.bukkit.NamespacedKey key, int def) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return def;
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, def);
    }

    private static void setInt(ItemStack item, org.bukkit.NamespacedKey key, int value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    private static Set<String> getSet(ItemStack item, org.bukkit.NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new HashSet<>();
        String raw = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, "");
        if (raw.isEmpty()) return new HashSet<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static void setSet(ItemStack item, org.bukkit.NamespacedKey key, Set<String> values) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String joined = values.stream().collect(Collectors.joining(","));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, joined);
        item.setItemMeta(meta);
    }

    // ========================================================================
    // LORE UPDATE
    // ========================================================================

    private static void updateLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Set<String> blocks = getSet(item, Keys.OMNISCANNER_BLOCKS);
        Set<String> itemTypes = getSet(item, Keys.OMNISCANNER_ITEMS);
        Set<String> entities = getSet(item, Keys.OMNISCANNER_ENTITIES);
        int radius = getInt(item, Keys.OMNISCANNER_RADIUS, 16);

        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtil.parse("<!italic><gray>Админский сканер</gray>"));
        lore.add(MessageUtil.parse("<!italic><gold>Радиус: </gold><white>" + radius + "</white>"));

        if (!blocks.isEmpty()) {
            lore.add(MessageUtil.parse("<!italic><gray>Блоков: </gray><white>" + blocks.size() + "</white>"));
        } else {
            lore.add(MessageUtil.parse("<!italic><gray>Блоки: </gray><white>все</white>"));
        }
        if (!itemTypes.isEmpty()) {
            lore.add(MessageUtil.parse("<!italic><gray>Предметов: </gray><white>" + itemTypes.size() + "</white>"));
        } else {
            lore.add(MessageUtil.parse("<!italic><gray>Предметы: </gray><white>все</white>"));
        }
        if (!entities.isEmpty()) {
            lore.add(MessageUtil.parse("<!italic><gray>Сущностей: </gray><white>" + entities.size() + "</white>"));
        } else {
            lore.add(MessageUtil.parse("<!italic><gray>Сущности: </gray><white>все</white>"));
        }

        lore.add(Component.empty());
        lore.add(MessageUtil.parse("<!italic><gold>ПКМ</gold><gray> — Сканировать</gray>"));
        lore.add(MessageUtil.parse("<!italic><gold>Shift+ПКМ</gold><gray> — Настройки</gray>"));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    // ========================================================================
    // LISTENER
    // ========================================================================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isOmniscanner(item)) return;

        e.setCancelled(true);

        // Cooldown
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uid);
        if (last != null && (now - last) < COOLDOWN_MS) {
            return;
        }
        cooldowns.put(uid, now);

        if (player.isSneaking()) {
            // Shift+ПКМ — открыть GUI настройки
            OmniscannerGUI.open(player, item);
        } else {
            // ПКМ — сканировать (пустые списки = всё)
            performScan(player, item);
        }
    }

    // ========================================================================
    // SCAN LOGIC
    // ========================================================================

    /**
     * Запускает сканирование.
     * <p>
     * ⚡ Для предотвращения фриза сервера (см. TODOs.md):
     * - ChunkSnapshot собираются на server thread (быстро — O(чанки), не O(блоки))
     * - Блоки сканируются на async thread через {@link ChunkSnapshot#getBlockType} (thread-safe)
     * - Сущности собираются на server thread (O(сущности), быстро)
     * - Результаты возвращаются на server thread для вывода
     */
    public static void performScan(Player player, ItemStack scanner) {
        Set<String> blockTypes = getBlockTypes(scanner);
        Set<String> itemTypes = getItemTypes(scanner);
        Set<String> entityTypes = getEntityTypes(scanner);
        int radius = getRadius(scanner);

        Location center = player.getLocation();
        World world = player.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Сообщение игроку — сканирование может занять время
        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gradient:#FF6B6B:#FFD93D>═══════ 🔭 Omniscanner ═══════</gradient>"));
        player.sendMessage(MessageUtil.parse("<gold>🔭 Scanning started...</gold> <gray>Radius: " + radius + " blocks. This may take a moment on large scans.</gray>"));
        player.sendMessage(MessageUtil.parse("<gradient:#FF6B6B:#FFD93D>════════════════════════════</gradient>"));

        // =========================
        // Pre-resolve Material sets (main thread)
        // =========================
        Set<Material> blockMaterials = resolveMaterials(blockTypes);
        Set<Material> itemMaterials = resolveMaterials(itemTypes);
        Set<String> upperEntityTypes = entityTypes.isEmpty() ? null
                : entityTypes.stream().map(String::toUpperCase).collect(Collectors.toSet());

        boolean scanAllBlocks = blockTypes.isEmpty();
        boolean scanAllItems = itemTypes.isEmpty();
        boolean scanAllEntities = entityTypes.isEmpty();

        // =========================
        // Collect chunk snapshots (main thread, fast — один вызов на чанк)
        // =========================
        int minChunkX = (cx - radius) >> 4;
        int maxChunkX = (cx + radius) >> 4;
        int minChunkZ = (cz - radius) >> 4;
        int maxChunkZ = (cz + radius) >> 4;

        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                    snapshots.put(key, world.getChunkAt(chunkX, chunkZ).getChunkSnapshot());
                }
            }
        }

        // =========================
        // Collect entity data on main thread (thread-safe для async)
        // Bukkit Entity/Player объекты НЕ thread-safe, извлекаем всё заранее
        // =========================
        List<EntityData> entityDataList = new ArrayList<>();
        List<PlayerData> playerData = new ArrayList<>();
        List<MobInventoryData> mobData = new ArrayList<>();

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Item itemEntity) {
                ItemStack stack = itemEntity.getItemStack();
                entityDataList.add(new EntityData(
                        "ITEM", "Предмет(пол)", stack != null ? stack.getType().name() : "?",
                        entity.getLocation()));
            } else if (entity instanceof Player target && !target.equals(player)) {
                Location loc = target.getLocation();
                String name = target.getName();
                ItemStack[] contents = target.getInventory().getContents().clone();
                playerData.add(new PlayerData(name, loc, contents));
            } else if (entity instanceof ChestedHorse horse) {
                Inventory inv = horse.getInventory();
                if (inv != null) {
                    String displayName = getEntityDisplayName(horse);
                    ItemStack[] contents = inv.getContents().clone();
                    mobData.add(new MobInventoryData(displayName, entity.getLocation(), contents));
                }
            } else {
                // Обычные сущности (мобы, животные, и т.д.)
                entityDataList.add(new EntityData(
                        "ENTITY", getEntityDisplayName(entity),
                        entity.getType().name().toUpperCase(),
                        entity.getLocation()));
            }
        }

        // =========================
        // Process all data on async thread (ChunkSnapshot thread-safe)
        // =========================
        int finalCy = cy;
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            List<ScanResult> results = new ArrayList<>();
            int minY = Math.max(world.getMinHeight(), finalCy - radius);
            int maxY = Math.min(world.getMaxHeight(), finalCy + radius);

            // =========================
            // 1. Сканирование блоков (через ChunkSnapshot — thread-safe)
            // Пустой список = ВСЕ блоки (кроме воздуха)
            // =========================
            if (scanAllBlocks) {
                ConsoleLogger.info("[Omniscanner] Scanning ALL blocks (empty type list)");
            }

            for (Map.Entry<Long, ChunkSnapshot> entry : snapshots.entrySet()) {
                long key = entry.getKey();
                ChunkSnapshot snapshot = entry.getValue();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;

                for (int bx = 0; bx < 16; bx++) {
                    int wx = (chunkX << 4) + bx;
                    int dx = Math.abs(wx - cx);
                    if (dx > radius) continue;

                    for (int bz = 0; bz < 16; bz++) {
                        int wz = (chunkZ << 4) + bz;
                        int dz = Math.abs(wz - cz);
                        if (dz > radius) continue;

                        for (int by = minY; by <= maxY; by++) {
                            int dy = Math.abs(by - finalCy);
                            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

                            Material type = snapshot.getBlockType(bx, by, bz);
                            if (type == Material.AIR) continue;
                            if (scanAllBlocks || blockMaterials.contains(type)) {
                                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                results.add(new ScanResult("Блок", type.name(),
                                        new Location(world, wx, by, wz), dist));
                            }
                        }
                    }
                }
            }

            // =========================
            // 2. Предметы на полу + Сущности (из предварительно собранных данных)
            // =========================
            for (EntityData ed : entityDataList) {
                double dist = center.distance(ed.location);
                if ("ITEM".equals(ed.type)) {
                    if (scanAllItems || itemMaterials.contains(Material.valueOf(ed.name))) {
                        results.add(new ScanResult(ed.category, ed.name, ed.location, dist));
                    }
                } else {
                    // Entity
                    if (scanAllEntities || (upperEntityTypes != null && upperEntityTypes.contains(ed.name))) {
                        results.add(new ScanResult(ed.category, ed.displayName, ed.location, dist));
                    }
                }
            }

            // =========================
            // 3. Инвентари игроков
            // =========================
            for (PlayerData pd : playerData) {
                for (ItemStack stack : pd.contents) {
                    if (stack != null && (scanAllItems || itemMaterials.contains(stack.getType()))) {
                        double dist = center.distance(pd.location);
                        results.add(new ScanResult("Предмет(игрок:" + pd.name + ")",
                                stack.getType().name(), pd.location, dist));
                    }
                }
            }

            // =========================
            // 4. Инвентари мобов
            // =========================
            for (MobInventoryData md : mobData) {
                for (ItemStack stack : md.contents) {
                    if (stack != null && (scanAllItems || itemMaterials.contains(stack.getType()))) {
                        double dist = center.distance(md.location);
                        results.add(new ScanResult("Предмет(моб:" + md.name + ")",
                                stack.getType().name(), md.location, dist));
                    }
                }
            }

            // =========================
            // 6. Контейнеры (server thread — Bukkit API)
            // Сканирование инвентарей контейнеров выполняется отдельным проходом
            // на server thread, чтобы избежать race condition'ов
            // =========================
            List<ScanResult> containerResults = new ArrayList<>();
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                for (Map.Entry<Long, ChunkSnapshot> entry : snapshots.entrySet()) {
                    long key = entry.getKey();
                    ChunkSnapshot snapshot = entry.getValue();
                    int chunkX = (int) (key >> 32);
                    int chunkZ = (int) key;

                    for (int bx = 0; bx < 16; bx++) {
                        int wx = (chunkX << 4) + bx;
                        int dx = Math.abs(wx - cx);
                        if (dx > radius) continue;

                        for (int bz = 0; bz < 16; bz++) {
                            int wz = (chunkZ << 4) + bz;
                            int dz = Math.abs(wz - cz);
                            if (dz > radius) continue;

                            for (int by = minY; by <= maxY; by++) {
                                int dy = Math.abs(by - finalCy);
                                if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

                                Material type = snapshot.getBlockType(bx, by, bz);
                                if (isContainerType(type)) {
                                    Block block = world.getBlockAt(wx, by, wz);
                                    scanContainer(block, center, itemMaterials, scanAllItems, containerResults);
                                }
                            }
                        }
                    }
                }

                // =========================
                // 7. Вывод результатов (server thread)
                // =========================
                results.addAll(containerResults);
                results.sort(Comparator.comparingDouble(r -> r.distance));
                displayResults(player, results, radius);
            });
        });
    }

    // ========================================================================
    // CONTAINER SCAN (server thread only — Bukkit API)
    // ========================================================================

    /** Материалы, которые являются контейнерами с инвентарём */
    private static final Set<Material> CONTAINER_TYPES = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
    );

    private static boolean isContainerType(Material type) {
        return CONTAINER_TYPES.contains(type);
    }

    /**
     * Сканирует инвентарь одного контейнера (вызывается с server thread).
     */
    private static void scanContainer(Block block, Location center, Set<Material> itemMaterials,
                                       boolean scanAllItems, List<ScanResult> results) {
        if (!(block.getState() instanceof Container container)) return;
        try {
            Inventory inv = container.getInventory();
            String containerName = block.getType().name();
            if (container instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest) {
                containerName = "DOUBLE_CHEST";
            }
            for (ItemStack stack : inv.getContents()) {
                if (stack != null && (scanAllItems || itemMaterials.contains(stack.getType()))) {
                    double dist = center.distance(block.getLocation().add(0.5, 0.5, 0.5));
                    results.add(new ScanResult("Предмет(" + containerName + ")",
                            stack.getType().name(), block.getLocation(), dist));
                }
            }
        } catch (Exception ignored) {}
    }

    // ========================================================================
    // DISPLAY RESULTS
    // ========================================================================

    /**
     * Выводит результаты сканирования игроку (вызывается с server thread).
     */
    private static void displayResults(Player player, List<ScanResult> results, int radius) {
        if (!player.isOnline()) return;

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gradient:#FF6B6B:#FFD93D>═══════ 🔭 Omniscanner ═══════</gradient>"));
        player.sendMessage(MessageUtil.parse("<gray>Найдено: <white>" + results.size() + "</white> объектов</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>Радиус: <white>" + radius + "</white> блоков</gray>"));

        if (results.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<red>Ничего не найдено.</red>"));
        } else {
            player.sendMessage(Component.empty());
            int showCount = Math.min(30, results.size());
            for (int i = 0; i < showCount; i++) {
                ScanResult r = results.get(i);
                String distColor = r.distance <= radius / 3 ? "<green>" :
                        r.distance <= radius * 2 / 3 ? "<yellow>" : "<red>";
                player.sendMessage(MessageUtil.parse(
                        "  <gray>" + (i + 1) + ".</gray> " +
                                distColor + r.category + "</" + distColor.substring(1) +
                                " <white>" + r.name + "</white> " +
                                distColor + (int) r.distance + "м</" + distColor.substring(1)
                ));
                player.sendMessage(MessageUtil.parse(
                        "    <dark_gray>→ " + r.location.getBlockX() + " " + r.location.getBlockY() + " " + r.location.getBlockZ() + "</dark_gray>"
                ));
            }
            if (results.size() > 30) {
                player.sendMessage(MessageUtil.parse("  <dark_gray>... и ещё " + (results.size() - 30) + " объектов</dark_gray>"));
            }
        }

        player.sendMessage(MessageUtil.parse("<gradient:#FF6B6B:#FFD93D>════════════════════════════</gradient>"));

        // Звук
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.5f);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Преобразует список строк Material в набор Material (только валидные).
     */
    private static Set<Material> resolveMaterials(Set<String> typeNames) {
        if (typeNames.isEmpty()) return null;
        Set<Material> materials = new HashSet<>();
        for (String s : typeNames) {
            try {
                materials.add(Material.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        return materials;
    }

    // ========================================================================
    // THREAD-SAFE DATA HOLDERS
    // ========================================================================

    /** Данные игрока и его инвентаря для async обработки */
    private record PlayerData(String name, Location location, ItemStack[] contents) {}

    /** Данные животного с инвентарём для async обработки */
    private record MobInventoryData(String name, Location location, ItemStack[] contents) {}

    /** Данные сущности/предмета для async обработки (все поля thread-safe) */
    private record EntityData(String type, String category, String name, String displayName, Location location) {
        EntityData(String type, String category, String name, Location location) {
            this(type, category, name, name, location);
        }
    }

    private static String getEntityDisplayName(Entity entity) {
        String name = entity.getType().name().toLowerCase().replace('_', ' ');
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        if (entity.getCustomName() != null) {
            name = entity.getCustomName();
        }
        return name;
    }

    // ========================================================================
    // RESULT DATA
    // ========================================================================

    private static class ScanResult {
        final String category;
        final String name;
        final Location location;
        final double distance;

        ScanResult(String category, String name, Location location, double distance) {
            this.category = category;
            this.name = name;
            this.location = location;
            this.distance = distance;
        }
    }
}
