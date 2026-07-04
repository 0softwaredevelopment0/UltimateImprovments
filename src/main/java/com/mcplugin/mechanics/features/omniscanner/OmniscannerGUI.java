package com.mcplugin.mechanics.features.omniscanner;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔧 Omniscanner Configuration GUI
 * <p>
 * Позволяет настроить:
 * - Список типов блоков для поиска
 * - Список типов предметов для поиска
 * - Список типов сущностей для поиска
 * - Радиус сканирования
 */
public class OmniscannerGUI implements Listener {

    private static final Map<UUID, GUIState> openMenus = new HashMap<>();
    private static boolean registered = false;

    // Слоты интерфейса (6 строк = 54 слота)
    private static final int SLOT_BLOCKS_HEADER = 0;
    private static final int SLOT_ITEMS_HEADER = 1;
    private static final int SLOT_ENTITIES_HEADER = 2;
    private static final int SLOT_RADIUS_HEADER = 3;
    private static final int SLOT_CLEAR_ALL = 8;
    private static final int SLOT_LIST_START = 18; // список типов начинается с 18 слота
    private static final int SLOT_ADD = 52;
    private static final int SLOT_CLEAR = 51;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_RADIUS_DOWN = 46;
    private static final int SLOT_RADIUS_UP = 47;
    private static final int SLOT_RADIUS_SET = 48;

    private static class GUIState {
        final Player player;
        ItemStack scanner;
        String currentTab = "BLOCKS"; // BLOCKS, ITEMS, ENTITIES

        GUIState(Player player, ItemStack scanner) {
            this.player = player;
            this.scanner = scanner;
        }
    }

    public static void open(Player player, ItemStack scanner) {
        register();

        GUIState state = new GUIState(player, scanner);
        openMenus.put(player.getUniqueId(), state);
        buildGUI(state);
    }

    private static void buildGUI(GUIState state) {
        Player player = state.player;
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.legacy("<!italic><gradient:#FF6B6B:#FFD93D>🔭 Omniscanner Config</gradient>"));

        ItemStack scanner = findScannerInHand(player);
        if (scanner == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Omniscanner пропал из руки!</red>"));
            openMenus.remove(player.getUniqueId());
            return;
        }
        state.scanner = scanner;

        // Верхняя панель: переключатели вкладок
        inv.setItem(SLOT_BLOCKS_HEADER, createTabItem(Material.STONE, "Блоки", state.currentTab.equals("BLOCKS"),
                getBlockTypes(scanner).size() + " типов"));
        inv.setItem(SLOT_ITEMS_HEADER, createTabItem(Material.DIAMOND, "Предметы", state.currentTab.equals("ITEMS"),
                getItemTypes(scanner).size() + " типов"));
        inv.setItem(SLOT_ENTITIES_HEADER, createTabItem(Material.ZOMBIE_SPAWN_EGG, "Сущности", state.currentTab.equals("ENTITIES"),
                getEntityTypes(scanner).size() + " типов"));

        // Очистить всё
        inv.setItem(SLOT_CLEAR_ALL, createActionItem(Material.BARRIER, "<red>Очистить всё</red>",
                "<gray>Удалить все списки</gray>"));

        // Разделители
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createDivider());
        }

        // Список типов
        Set<String> types;
        switch (state.currentTab) {
            case "ITEMS":
                types = getItemTypes(scanner);
                break;
            case "ENTITIES":
                types = getEntityTypes(scanner);
                break;
            default:
                types = getBlockTypes(scanner);
        }

        List<String> sortedTypes = new ArrayList<>(types);
        Collections.sort(sortedTypes);

        int slot = SLOT_LIST_START;
        for (String type : sortedTypes) {
            if (slot >= 45) break;
            inv.setItem(slot, createTypeItem(type));
            slot++;
        }

        // Нижняя панель
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createDivider());
        }

        // Радиус (показываем всегда)
        int radius = getRadius(scanner);
        inv.setItem(SLOT_RADIUS_DOWN, createActionItem(Material.RED_STAINED_GLASS_PANE,
                "<red>-10</red>", "<gray>Уменьшить радиус</gray>"));
        inv.setItem(SLOT_RADIUS_UP, createActionItem(Material.GREEN_STAINED_GLASS_PANE,
                "<green>+10</green>", "<gray>Увеличить радиус</gray>"));
        inv.setItem(SLOT_RADIUS_SET, createActionItem(Material.COMPASS,
                "<gold>Радиус: <white>" + radius + "</white></gold>",
                "<gray>Нажмите для точного ввода</gray>"));

        // Кнопки действий
        inv.setItem(SLOT_CLEAR, createActionItem(Material.LAVA_BUCKET,
                "<red>Очистить список</red>",
                "<gray>Удалить все типы из текущей вкладки</gray>"));
        inv.setItem(SLOT_ADD, createActionItem(Material.ANVIL,
                "<green>Добавить тип</green>",
                "<gray>Открыть ввод для добавления нового типа</gray>"));
        inv.setItem(SLOT_BACK, createActionItem(Material.OAK_DOOR,
                "<gray>Закрыть</gray>", ""));

        player.openInventory(inv);
    }

    // ========================================================================
    // ITEM CREATORS
    // ========================================================================

    private static ItemStack createTabItem(Material material, String name, boolean active, String count) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String color = active ? "<gold>" : "<gray>";
        meta.displayName(MessageUtil.parse("<!italic>" + color + name + (active ? " <dark_gray>◄</dark_gray>" : "")));
        meta.lore(List.of(
                MessageUtil.parse("<!italic><gray>" + count + "</gray>"),
                MessageUtil.parse("<!italic><dark_gray>Нажмите чтобы переключиться</dark_gray>")
        ));
        if (active) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createTypeItem(String typeName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<!italic><white>" + typeName + "</white>"));

        // Try to get the material for display
        try {
            Material mat = Material.valueOf(typeName.toUpperCase());
            item.setType(mat);
        } catch (IllegalArgumentException ignored) {}

        meta.lore(List.of(
                MessageUtil.parse("<!italic><red>ПКМ — удалить</red>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createActionItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<!italic>" + name));
        if (!lore.isEmpty()) {
            meta.lore(List.of(MessageUtil.parse("<!italic>" + lore)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDivider() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static ItemStack findScannerInHand(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (OmniscannerManager.isOmniscanner(mainHand)) return mainHand;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (OmniscannerManager.isOmniscanner(offHand)) return offHand;
        return null;
    }

    private static Set<String> getBlockTypes(ItemStack item) {
        return OmniscannerManager.getBlockTypes(item);
    }

    private static Set<String> getItemTypes(ItemStack item) {
        return OmniscannerManager.getItemTypes(item);
    }

    private static Set<String> getEntityTypes(ItemStack item) {
        return OmniscannerManager.getEntityTypes(item);
    }

    private static int getRadius(ItemStack item) {
        return OmniscannerManager.getRadius(item);
    }

    // ========================================================================
    // LISTENER
    // ========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        GUIState state = openMenus.get(uuid);
        if (state == null) return;

        // Отменяем клик только в верхнем GUI (кастомный инвентарь)
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            e.setCancelled(true);
        }

        // Клик в нижнем инвентаре (своём) — разрешаем, всё ок
        if (e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }

        ItemStack scanner = findScannerInHand(player);
        if (scanner == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Omniscanner пропал из руки!</red>"));
            player.closeInventory();
            openMenus.remove(uuid);
            return;
        }
        state.scanner = scanner;

        int slot = e.getSlot();

        // Вкладки (только ЛКМ)
        if (slot == SLOT_BLOCKS_HEADER && e.isLeftClick()) {
            state.currentTab = "BLOCKS";
            buildGUI(state);
            return;
        }
        if (slot == SLOT_ITEMS_HEADER && e.isLeftClick()) {
            state.currentTab = "ITEMS";
            buildGUI(state);
            return;
        }
        if (slot == SLOT_ENTITIES_HEADER && e.isLeftClick()) {
            state.currentTab = "ENTITIES";
            buildGUI(state);
            return;
        }

        // Очистить всё (только ЛКМ)
        if (slot == SLOT_CLEAR_ALL && e.isLeftClick()) {
            OmniscannerManager.setBlockTypes(scanner, new HashSet<>());
            OmniscannerManager.setItemTypes(scanner, new HashSet<>());
            OmniscannerManager.setEntityTypes(scanner, new HashSet<>());
            player.sendMessage(MessageUtil.parse("<green>✔ Все списки очищены.</green>"));
            player.closeInventory();
            openMenus.remove(uuid);
            return;
        }

        // Радиус (только ЛКМ)
        if (slot == SLOT_RADIUS_DOWN && e.isLeftClick()) {
            int r = Math.max(1, getRadius(scanner) - 10);
            OmniscannerManager.setRadius(scanner, r);
            buildGUI(state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
            return;
        }
        if (slot == SLOT_RADIUS_UP && e.isLeftClick()) {
            int r = Math.min(500, getRadius(scanner) + 10);
            OmniscannerManager.setRadius(scanner, r);
            buildGUI(state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f);
            return;
        }
        if (slot == SLOT_RADIUS_SET && e.isLeftClick()) {
            player.closeInventory();
            openMenus.remove(uuid);
            openRadiusAnvil(player, scanner);
            return;
        }

        // Список типов — ТОЛЬКО ПКМ удалить
        if (slot >= SLOT_LIST_START && slot < 45 && e.isRightClick()) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(clicked.getItemMeta().displayName()).trim();

                Set<String> types;
                switch (state.currentTab) {
                    case "ITEMS":
                        types = getItemTypes(scanner);
                        types.remove(name);
                        OmniscannerManager.setItemTypes(scanner, types);
                        break;
                    case "ENTITIES":
                        types = getEntityTypes(scanner);
                        types.remove(name);
                        OmniscannerManager.setEntityTypes(scanner, types);
                        break;
                    default:
                        types = getBlockTypes(scanner);
                        types.remove(name);
                        OmniscannerManager.setBlockTypes(scanner, types);
                }

                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.3f, 0.8f);
                buildGUI(state);
            }
            return;
        }

        // Очистить текущий список (только ЛКМ)
        if (slot == SLOT_CLEAR && e.isLeftClick()) {
            switch (state.currentTab) {
                case "ITEMS":
                    OmniscannerManager.setItemTypes(scanner, new HashSet<>());
                    break;
                case "ENTITIES":
                    OmniscannerManager.setEntityTypes(scanner, new HashSet<>());
                    break;
                default:
                    OmniscannerManager.setBlockTypes(scanner, new HashSet<>());
            }
            player.sendMessage(MessageUtil.parse("<green>✔ Список очищен.</green>"));
            buildGUI(state);
            return;
        }

        // Добавить тип (только ЛКМ)
        if (slot == SLOT_ADD && e.isLeftClick()) {
            player.closeInventory();
            openMenus.remove(uuid);
            openAddAnvil(player, scanner, state.currentTab);
            return;
        }

        // Закрыть (только ЛКМ)
        if (slot == SLOT_BACK && e.isLeftClick()) {
            player.closeInventory();
            openMenus.remove(uuid);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        openMenus.remove(uuid);
    }

    // ========================================================================
    // ANVIL GUI for adding types / setting radius
    // ========================================================================

    private static void openAddAnvil(Player player, ItemStack scanner, String tab) {
        String categoryName = switch (tab) {
            case "ITEMS" -> "предмета";
            case "ENTITIES" -> "сущности";
            default -> "блока";
        };

        var view = org.bukkit.inventory.MenuType.ANVIL.builder()
                .title(MessageUtil.parse("<dark_gray>Добавить тип " + categoryName + "</dark_gray>"))
                .build(player);
        view.open();

        Inventory topInv = view.getTopInventory();
        ItemStack hint = new ItemStack(Material.PAPER);
        ItemMeta meta = hint.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><gray>Введите Material name...</gray>"));
            meta.lore(List.of(
                    MessageUtil.parse("<!italic><gray>Например: DIAMOND_ORE, CHEST, ZOMBIE</gray>")
            ));
            hint.setItemMeta(meta);
        }
        topInv.setItem(0, hint);
        topInv.setItem(1, createAnvilConfirmItem());
        topInv.setItem(2, createAnvilConfirmItem());

        // Store context in PDC of the confirm item
        ItemStack ctx = new ItemStack(Material.NETHER_STAR);
        ItemMeta ctxMeta = ctx.getItemMeta();
        if (ctxMeta != null) {
            ctxMeta.getPersistentDataContainer().set(Keys.OMNISCANNER, PersistentDataType.STRING, "ADD_" + tab);
            ctx.setItemMeta(ctxMeta);
        }
        topInv.setItem(2, ctx);

        startAnvilListener(player, scanner, tab, "ADD");
    }

    private static void openRadiusAnvil(Player player, ItemStack scanner) {
        var view = org.bukkit.inventory.MenuType.ANVIL.builder()
                .title(MessageUtil.parse("<dark_gray>Установить радиус</dark_gray>"))
                .build(player);
        view.open();

        Inventory topInv = view.getTopInventory();
        ItemStack hint = new ItemStack(Material.COMPASS);
        ItemMeta meta = hint.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><gray>Радиус: " + getRadius(scanner) + "</gray>"));
            meta.lore(List.of(
                    MessageUtil.parse("<!italic><gray>Введите число (1-500)</gray>")
            ));
            hint.setItemMeta(meta);
        }
        topInv.setItem(0, hint);
        topInv.setItem(1, createAnvilConfirmItem());
        topInv.setItem(2, createAnvilConfirmItem());

        ItemStack ctx = new ItemStack(Material.NETHER_STAR);
        ItemMeta ctxMeta = ctx.getItemMeta();
        if (ctxMeta != null) {
            ctxMeta.getPersistentDataContainer().set(Keys.OMNISCANNER, PersistentDataType.STRING, "SET_RADIUS");
            ctx.setItemMeta(ctxMeta);
        }
        topInv.setItem(2, ctx);

        startAnvilListener(player, scanner, null, "RADIUS");
    }

    private static ItemStack createAnvilConfirmItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><green>✔ Подтвердить</green>"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void startAnvilListener(Player player, ItemStack scanner, String tab, String mode) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || ticks > 600) { // 30 sec timeout
                    cancel();
                    return;
                }

                var openInv = player.getOpenInventory();
                if (openInv.getType() != org.bukkit.event.inventory.InventoryType.ANVIL) {
                    cancel();
                    return;
                }

                Inventory topInv = openInv.getTopInventory();
                ItemStack slot2 = topInv.getItem(2);

                // Check if player clicked slot 2
                if (slot2 == null || slot2.getType() == Material.AIR) {
                    cancel();
                    // Read the text from the anvil rename field
                    String text = getAnvilRenameText(player);
                    if (text != null && !text.trim().isEmpty()) {
                        if (mode.equals("RADIUS")) {
                            try {
                                int radius = Integer.parseInt(text.trim());
                                if (radius >= 1 && radius <= 500) {
                                    OmniscannerManager.setRadius(scanner, radius);
                                    player.sendMessage(MessageUtil.parse("<green>✔ Радиус установлен: " + radius + "</green>"));
                                } else {
                                    player.sendMessage(MessageUtil.parse("<red>❌ Радиус должен быть от 1 до 500!</red>"));
                                }
                            } catch (NumberFormatException ex) {
                                player.sendMessage(MessageUtil.parse("<red>❌ Введите число!</red>"));
                            }
                        } else {
                            // ADD mode
                            String typeName = text.trim().toUpperCase();
                            Set<String> types;
                            switch (tab) {
                                case "ITEMS":
                                    types = OmniscannerManager.getItemTypes(scanner);
                                    types.add(typeName);
                                    OmniscannerManager.setItemTypes(scanner, types);
                                    break;
                                case "ENTITIES":
                                    types = OmniscannerManager.getEntityTypes(scanner);
                                    types.add(typeName);
                                    OmniscannerManager.setEntityTypes(scanner, types);
                                    break;
                                default:
                                    types = OmniscannerManager.getBlockTypes(scanner);
                                    types.add(typeName);
                                    OmniscannerManager.setBlockTypes(scanner, types);
                            }
                            player.sendMessage(MessageUtil.parse("<green>✔ Добавлен тип: </green><white>" + typeName + "</white>"));
                        }
                    }

                    // Reopen config GUI
                    openMenus.put(player.getUniqueId(), new GUIState(player, scanner));
                    buildGUI(openMenus.get(player.getUniqueId()));
                }
            }
        }.runTaskTimer(Main.getInstance(), 1L, 1L);
    }

    private static String getAnvilRenameText(Player player) {
        try {
            var view = player.getOpenInventory();
            Object craftView = view;
            Class<?> craftViewClass = craftView.getClass();

            java.lang.reflect.Method getHandle = craftViewClass.getMethod("getHandle");
            Object handle = getHandle.invoke(craftView);

            Class<?> anvilClass = handle.getClass();
            java.lang.reflect.Field itemNameField;

            try {
                itemNameField = anvilClass.getDeclaredField("itemName");
            } catch (NoSuchFieldException e) {
                itemNameField = anvilClass.getSuperclass().getDeclaredField("itemName");
            }

            itemNameField.setAccessible(true);
            String text = (String) itemNameField.get(handle);
            itemNameField.setAccessible(false);
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    public static void register() {
        if (registered) return;
        registered = true;
        Bukkit.getPluginManager().registerEvents(new OmniscannerGUI(), Main.getInstance());
    }
}
