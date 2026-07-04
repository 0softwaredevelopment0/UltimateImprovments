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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 🔧 Omniscanner Configuration GUI
 * <p>
 * Позволяет настроить:
 * - Список типов блоков для поиска
 * - Список типов предметов для поиска
 * - Список типов сущностей для поиска
 * - Радиус сканирования
 *
 * Все GUI-предметы имеют PDC GUI_PROTECTED (byte=1) — их нельзя забрать.
 * Anvil-меню обрабатывается через InventoryClickEvent, а не через поллинг.
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
    private static final int SLOT_LIST_START = 18;
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
        String anvilMode = null; // null=не в анвиле, "ADD"/"RADIUS"
        String anvilTab = null; // tab for ADD mode
        int timeoutTicks = 0;

        GUIState(Player player, ItemStack scanner) {
            this.player = player;
            this.scanner = scanner;
        }
    }

    // ========================================================================
    // PDC HELPERS
    // ========================================================================

    /** Пометить предмет как защищённый (нельзя забрать из GUI) */
    private static void markProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    /** Проверить, защищён ли предмет */
    private static boolean isProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.GUI_PROTECTED, PersistentDataType.BYTE);
    }

    // ========================================================================
    // OPEN
    // ========================================================================

    public static void open(Player player, ItemStack scanner) {
        register();
        GUIState state = new GUIState(player, scanner);
        openMenus.put(player.getUniqueId(), state);
        buildConfigGUI(state);
    }

    // ========================================================================
    // BUILD CONFIG GUI
    // ========================================================================

    private static void buildConfigGUI(GUIState state) {
        Player player = state.player;
        state.anvilMode = null;
        state.anvilTab = null;
        state.timeoutTicks = 0;

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

        inv.setItem(SLOT_CLEAR_ALL, createActionItem(Material.BARRIER, "<red>Очистить всё</red>",
                "<gray>Удалить все списки</gray>"));

        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createDivider());
        }

        Set<String> types;
        switch (state.currentTab) {
            case "ITEMS": types = getItemTypes(scanner); break;
            case "ENTITIES": types = getEntityTypes(scanner); break;
            default: types = getBlockTypes(scanner);
        }

        List<String> sortedTypes = new ArrayList<>(types);
        Collections.sort(sortedTypes);

        int slot = SLOT_LIST_START;
        for (String type : sortedTypes) {
            if (slot >= 45) break;
            inv.setItem(slot, createTypeItem(type));
            slot++;
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createDivider());
        }

        int radius = getRadius(scanner);
        inv.setItem(SLOT_RADIUS_DOWN, createActionItem(Material.RED_STAINED_GLASS_PANE,
                "<red>-10</red>", "<gray>Уменьшить радиус</gray>"));
        inv.setItem(SLOT_RADIUS_UP, createActionItem(Material.GREEN_STAINED_GLASS_PANE,
                "<green>+10</green>", "<gray>Увеличить радиус</gray>"));
        inv.setItem(SLOT_RADIUS_SET, createActionItem(Material.COMPASS,
                "<gold>Радиус: <white>" + radius + "</white></gold>",
                "<gray>Нажмите для точного ввода</gray>"));

        inv.setItem(SLOT_CLEAR, createActionItem(Material.LAVA_BUCKET,
                "<red>Очистить список</red>",
                "<gray>Удалить все типы из текущей вкладки</gray>"));
        inv.setItem(SLOT_ADD, createActionItem(Material.ANVIL,
                "<green>Добавить тип</green>",
                "<gray>Открыть ввод для добавления нового типа</gray>"));
        inv.setItem(SLOT_BACK, createActionItem(Material.OAK_DOOR, "<gray>Закрыть</gray>", ""));

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
        if (active) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createTypeItem(String typeName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<!italic><white>" + typeName + "</white>"));
        try {
            Material mat = Material.valueOf(typeName.toUpperCase());
            item.setType(mat);
        } catch (IllegalArgumentException ignored) {}

        meta.lore(List.of(MessageUtil.parse("<!italic><red>ПКМ — удалить</red>")));
        meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
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
        meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDivider() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createAnvilHintItem(String text, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><gray>" + text + "</gray>"));
            List<Component> loreComp = new ArrayList<>();
            for (String line : lore) {
                loreComp.add(MessageUtil.parse("<!italic><gray>" + line + "</gray>"));
            }
            meta.lore(loreComp);
            meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
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

    private static Set<String> getBlockTypes(ItemStack item) { return OmniscannerManager.getBlockTypes(item); }
    private static Set<String> getItemTypes(ItemStack item) { return OmniscannerManager.getItemTypes(item); }
    private static Set<String> getEntityTypes(ItemStack item) { return OmniscannerManager.getEntityTypes(item); }
    private static int getRadius(ItemStack item) { return OmniscannerManager.getRadius(item); }

    // ========================================================================
    // LISTENER
    // ========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        GUIState state = openMenus.get(uuid);
        if (state == null) return;

        // Всегда отменяем клик в любом кастомном инвентаре (включая Anvil)
        if (e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory()) {
            e.setCancelled(true);
        }

        // Нижний инвентарь (свой) — не трогаем
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        // ====================================================================
        // ANVIL MODE — обрабатываем через InventoryClickEvent (не поллинг)
        // ====================================================================
        if (state.anvilMode != null && e.getInventory().getType() == InventoryType.ANVIL) {
            state.timeoutTicks = 0; // сброс таймера при любом клике

            if (!e.isLeftClick()) return; // только ЛКМ

            // Слот 2 = подтверждение
            if (e.getSlot() == 2) {
                processAnvilConfirm(player, state);
            }
            // Слоты 0 и 1 — игнорируем (защищены PDC)
            return;
        }

        // ====================================================================
        // ОБЫЧНЫЙ РЕЖИМ — конфиг GUI
        // ====================================================================

        ItemStack scanner = findScannerInHand(player);
        if (scanner == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Omniscanner пропал из руки!</red>"));
            player.closeInventory();
            openMenus.remove(uuid);
            return;
        }
        state.scanner = scanner;

        int slot = e.getSlot();

        // Если клик по защищённому предмету — блокируем ВСЕ не-действия
        ItemStack current = e.getCurrentItem();
        if (current != null && isProtected(current)) {
            // Разрешённые действия на защищённых предметах обрабатываем ниже
        }

        // Вкладки (только ЛКМ)
        if (slot == SLOT_BLOCKS_HEADER && e.isLeftClick()) { state.currentTab = "BLOCKS"; buildConfigGUI(state); return; }
        if (slot == SLOT_ITEMS_HEADER && e.isLeftClick()) { state.currentTab = "ITEMS"; buildConfigGUI(state); return; }
        if (slot == SLOT_ENTITIES_HEADER && e.isLeftClick()) { state.currentTab = "ENTITIES"; buildConfigGUI(state); return; }

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
            buildConfigGUI(state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
            return;
        }
        if (slot == SLOT_RADIUS_UP && e.isLeftClick()) {
            int r = Math.min(500, getRadius(scanner) + 10);
            OmniscannerManager.setRadius(scanner, r);
            buildConfigGUI(state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f);
            return;
        }
        if (slot == SLOT_RADIUS_SET && e.isLeftClick()) {
            openRadiusAnvil(player, state);
            return;
        }

        // Список типов — ТОЛЬКО ПКМ удалить
        if (slot >= SLOT_LIST_START && slot < 45 && e.isRightClick()) {
            if (current != null && current.hasItemMeta() && current.getItemMeta().hasDisplayName()) {
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(current.getItemMeta().displayName()).trim();

                Set<String> types;
                switch (state.currentTab) {
                    case "ITEMS":
                        types = getItemTypes(scanner); types.remove(name);
                        OmniscannerManager.setItemTypes(scanner, types); break;
                    case "ENTITIES":
                        types = getEntityTypes(scanner); types.remove(name);
                        OmniscannerManager.setEntityTypes(scanner, types); break;
                    default:
                        types = getBlockTypes(scanner); types.remove(name);
                        OmniscannerManager.setBlockTypes(scanner, types);
                }
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.3f, 0.8f);
                buildConfigGUI(state);
            }
            return;
        }

        // Очистить текущий список (только ЛКМ)
        if (slot == SLOT_CLEAR && e.isLeftClick()) {
            switch (state.currentTab) {
                case "ITEMS": OmniscannerManager.setItemTypes(scanner, new HashSet<>()); break;
                case "ENTITIES": OmniscannerManager.setEntityTypes(scanner, new HashSet<>()); break;
                default: OmniscannerManager.setBlockTypes(scanner, new HashSet<>());
            }
            player.sendMessage(MessageUtil.parse("<green>✔ Список очищен.</green>"));
            buildConfigGUI(state);
            return;
        }

        // Добавить тип (только ЛКМ)
        if (slot == SLOT_ADD && e.isLeftClick()) {
            openAddAnvil(player, state);
            return;
        }

        // Закрыть (только ЛКМ)
        if (slot == SLOT_BACK && e.isLeftClick()) {
            player.closeInventory();
            openMenus.remove(uuid);
        }
    }

    // ========================================================================
    // ANVIL LOGIC
    // ========================================================================

    private static void openAddAnvil(Player player, GUIState state) {
        String categoryName = switch (state.currentTab) {
            case "ITEMS" -> "предмета";
            case "ENTITIES" -> "сущности";
            default -> "блока";
        };

        state.anvilMode = "ADD";
        state.anvilTab = state.currentTab;
        state.timeoutTicks = 0;

        var view = org.bukkit.inventory.MenuType.ANVIL.builder()
                .title(MessageUtil.parse("<dark_gray>Добавить тип " + categoryName + "</dark_gray>"))
                .build(player);
        view.open();

        Inventory topInv = view.getTopInventory();
        topInv.setItem(0, createAnvilHintItem("Введите Material name...",
                List.of("Например: DIAMOND_ORE, CHEST, ZOMBIE")));
        // Слот 1 и 2 — кликабельные подтверждения
        topInv.setItem(1, createAnvilSlot2());
        topInv.setItem(2, createAnvilSlot2());
    }

    private static void openRadiusAnvil(Player player, GUIState state) {
        state.anvilMode = "RADIUS";
        state.anvilTab = null;
        state.timeoutTicks = 0;

        var view = org.bukkit.inventory.MenuType.ANVIL.builder()
                .title(MessageUtil.parse("<dark_gray>Установить радиус</dark_gray>"))
                .build(player);
        view.open();

        Inventory topInv = view.getTopInventory();
        topInv.setItem(0, createAnvilHintItem("Радиус: " + getRadius(state.scanner),
                List.of("Введите число (1-500)")));
        topInv.setItem(1, createAnvilSlot2());
        topInv.setItem(2, createAnvilSlot2());
    }

    /** Слот подтверждения Anvil — с PDC защитой */
    private static ItemStack createAnvilSlot2() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic><green>✔ Подтвердить</green>"));
            meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Обработать подтверждение Anvil (клик по slot 2) */
    private static void processAnvilConfirm(Player player, GUIState state) {
        String text = getAnvilRenameText(player);
        if (text == null || text.trim().isEmpty()) return;

        if ("RADIUS".equals(state.anvilMode)) {
            try {
                int radius = Integer.parseInt(text.trim());
                if (radius >= 1 && radius <= 500) {
                    OmniscannerManager.setRadius(state.scanner, radius);
                    player.sendMessage(MessageUtil.parse("<green>✔ Радиус установлен: " + radius + "</green>"));
                } else {
                    player.sendMessage(MessageUtil.parse("<red>❌ Радиус должен быть от 1 до 500!</red>"));
                }
            } catch (NumberFormatException ex) {
                player.sendMessage(MessageUtil.parse("<red>❌ Введите число!</red>"));
            }
        } else {
            String typeName = text.trim().toUpperCase();
            String tab = state.anvilTab != null ? state.anvilTab : state.currentTab;
            Set<String> types;
            switch (tab) {
                case "ITEMS":
                    types = OmniscannerManager.getItemTypes(state.scanner);
                    types.add(typeName);
                    OmniscannerManager.setItemTypes(state.scanner, types);
                    break;
                case "ENTITIES":
                    types = OmniscannerManager.getEntityTypes(state.scanner);
                    types.add(typeName);
                    OmniscannerManager.setEntityTypes(state.scanner, types);
                    break;
                default:
                    types = OmniscannerManager.getBlockTypes(state.scanner);
                    types.add(typeName);
                    OmniscannerManager.setBlockTypes(state.scanner, types);
            }
            player.sendMessage(MessageUtil.parse("<green>✔ Добавлен тип: </green><white>" + typeName + "</white>"));
        }

        // Переоткрыть конфиг GUI
        player.closeInventory(); // закроет Anvil и вызовет onInventoryClose
        openMenus.put(player.getUniqueId(), state);
        buildConfigGUI(state);
    }

    /** Чтение текста из Anvil rename поля через рефлексию */
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
            String result = (String) itemNameField.get(handle);
            itemNameField.setAccessible(false);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // CLOSE / TIMEOUT
    // ========================================================================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        GUIState state = openMenus.get(uuid);
        // Если игрок закрыл Anvil (без подтверждения) — чистим
        if (state != null && state.anvilMode != null) {
            openMenus.remove(uuid);
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
