package com.mcplugin.mechanics.features.omniscanner;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.entity.LivingEntity;
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

    public static void performScan(Player player, ItemStack scanner) {
        Set<String> blockTypes = getBlockTypes(scanner);
        Set<String> itemTypes = getItemTypes(scanner);
        Set<String> entityTypes = getEntityTypes(scanner);
        int radius = getRadius(scanner);

        Location center = player.getLocation();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        List<ScanResult> results = new ArrayList<>();

        // 1. Сканирование блоков (пустой список = все блоки)
        Set<Material> blockMaterials = null;
        if (!blockTypes.isEmpty()) {
            blockMaterials = blockTypes.stream()
                    .map(s -> s.toUpperCase())
                    .filter(s -> {
                        try {
                            Material.valueOf(s);
                            return true;
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                    })
                    .map(Material::valueOf)
                    .collect(Collectors.toSet());
        }

        int minY = Math.max(player.getWorld().getMinHeight(), cy - radius);
        int maxY = Math.min(player.getWorld().getMaxHeight(), cy + radius);

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((cx - x) * (cx - x) + (cy - y) * (cy - y) + (cz - z) * (cz - z) > radius * radius) continue;
                    Block block = player.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR) continue;
                    if (blockMaterials == null || blockMaterials.contains(block.getType())) {
                        results.add(new ScanResult("Блок", block.getType().name(),
                                block.getLocation(), center.distance(block.getLocation().add(0.5, 0.5, 0.5))));
                    }
                }
            }
        }

        // 2. Сканирование предметов (на полу + в инвентарях; пустой список = все предметы)
        Set<Material> itemMaterials = null;
        if (!itemTypes.isEmpty()) {
            itemMaterials = itemTypes.stream()
                    .map(s -> s.toUpperCase())
                    .filter(s -> {
                        try {
                            Material.valueOf(s);
                            return true;
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                    })
                    .map(Material::valueOf)
                    .collect(Collectors.toSet());
        }

        // 2a. Предметы на полу
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item itemEntity)) continue;
            ItemStack stack = itemEntity.getItemStack();
            if (stack != null && (itemMaterials == null || itemMaterials.contains(stack.getType()))) {
                results.add(new ScanResult("Предмет(пол)", stack.getType().name(),
                        entity.getLocation(), center.distance(entity.getLocation())));
            }
        }

        // 2b. Инвентари игроков
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;
            for (ItemStack stack : target.getInventory().getContents()) {
                if (stack != null && (itemMaterials == null || itemMaterials.contains(stack.getType()))) {
                    results.add(new ScanResult("Предмет(игрок:" + target.getName() + ")", stack.getType().name(),
                            target.getLocation(), center.distance(target.getLocation())));
                }
            }
        }

        // 2c. Инвентари мобов (животные с инвентарём)
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof ChestedHorse horse) {
                Inventory inv = horse.getInventory();
                if (inv != null) {
                    for (ItemStack stack : inv.getContents()) {
                        if (stack != null && (itemMaterials == null || itemMaterials.contains(stack.getType()))) {
                            results.add(new ScanResult("Предмет(моб:" + getEntityDisplayName(horse) + ")", stack.getType().name(),
                                    entity.getLocation(), center.distance(entity.getLocation())));
                        }
                    }
                }
            }
        }

        // 2d. Контейнеры (сундуки, бочки, печки и т.д.)
        int minY2 = Math.max(player.getWorld().getMinHeight(), cy - radius);
        int maxY2 = Math.min(player.getWorld().getMaxHeight(), cy + radius);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = minY2; y <= maxY2; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((cx - x) * (cx - x) + (cy - y) * (cy - y) + (cz - z) * (cz - z) > radius * radius) continue;
                    Block block = player.getWorld().getBlockAt(x, y, z);
                    if (block.getState() instanceof Container container) {
                        try {
                            Inventory inv = container.getInventory();
                            String containerName = block.getType().name();
                            // Handle double chests
                            if (container instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest) {
                                containerName = "DOUBLE_CHEST";
                            }
                            for (ItemStack stack : inv.getContents()) {
                                if (stack != null && (itemMaterials == null || itemMaterials.contains(stack.getType()))) {
                                    results.add(new ScanResult("Предмет(" + containerName + ")", stack.getType().name(),
                                            block.getLocation(), center.distance(block.getLocation().add(0.5, 0.5, 0.5))));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 3. Сканирование сущностей (пустой список = все сущности)
        Set<String> upperEntityTypes = entityTypes.isEmpty() ? null
                : entityTypes.stream().map(String::toUpperCase).collect(Collectors.toSet());

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player) continue;
            if (entity instanceof Item) continue;
            String typeName = entity.getType().name().toUpperCase();
            if (upperEntityTypes == null || upperEntityTypes.contains(typeName)) {
                results.add(new ScanResult("Сущность", getEntityDisplayName(entity),
                        entity.getLocation(), center.distance(entity.getLocation())));
            }
        }

        // 4. Вывод результатов
        results.sort(Comparator.comparingDouble(r -> r.distance));

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
