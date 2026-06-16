package com.mcplugin.cp;

import com.mcplugin.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Строит и обновляет GUI двойного сундука для кодовой панели.
 *
 * Layout:
 *   Row 0 (0-8):   [G][G][P][P][P][P][P][G][G]  — экран (бумага)
 *   Row 1 (9-17):  [G][G][G][G][G][G][G][G][G]  — разделитель
 *   Row 2 (18-26): [G][G][G][1][2][3][G][G][G]  — цифры
 *   Row 3 (27-35): [G][G][G][4][5][6][G][G][G]
 *   Row 4 (36-44): [G][G][G][7][8][9][G][G][G]
 *   Row 5 (45-53): [G][R][G][B][0][E][G][G][G]  — R=Сброс, B=⌫, E=Ввод
 *
 *   G = Light Gray Stained Glass Pane (фон, скрытый тултип)
 *   P = Book (экран, имя меняется)
 *   0-9 = Iron Block с номером
 *   B = Gold Block (⌫ Назад)
 *   R = Redstone Block (R Сброс)
 *   E = Emerald Block (✔ Ввод)
 */
public class CodePanelGUI {

    public static final int GUI_SIZE = 54;
    public static final String GUI_TITLE = "§8Кодовая панель";

    // Screen slots (5 paper items showing the code)
    public static final int[] SCREEN_SLOTS = {2, 3, 4, 5, 6};

    // Number button slots
    public static final int SLOT_1 = 21;
    public static final int SLOT_2 = 22;
    public static final int SLOT_3 = 23;
    public static final int SLOT_4 = 30;
    public static final int SLOT_5 = 31;
    public static final int SLOT_6 = 32;
    public static final int SLOT_7 = 39;
    public static final int SLOT_8 = 40;
    public static final int SLOT_9 = 41;
    public static final int SLOT_0 = 49;

    // Action button slots
    public static final int SLOT_RESET = 46;
    public static final int SLOT_BACKSPACE = 48;
    public static final int SLOT_ENTER = 50;

    // Slot → action mapping
    public static final Map<Integer, String> BUTTON_MAP = new HashMap<>();

    static {
        BUTTON_MAP.put(SLOT_1, "1");
        BUTTON_MAP.put(SLOT_2, "2");
        BUTTON_MAP.put(SLOT_3, "3");
        BUTTON_MAP.put(SLOT_4, "4");
        BUTTON_MAP.put(SLOT_5, "5");
        BUTTON_MAP.put(SLOT_6, "6");
        BUTTON_MAP.put(SLOT_7, "7");
        BUTTON_MAP.put(SLOT_8, "8");
        BUTTON_MAP.put(SLOT_9, "9");
        BUTTON_MAP.put(SLOT_0, "0");
        BUTTON_MAP.put(SLOT_BACKSPACE, "BACKSPACE");
        BUTTON_MAP.put(SLOT_RESET, "RESET");
        BUTTON_MAP.put(SLOT_ENTER, "ENTER");
    }

    private CodePanelGUI() {}

    // =========================
    // OPEN GUI
    // =========================
    public static void open(Player player) {
        if (!isSafe()) return;

        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                Component.text(GUI_TITLE).decoration(TextDecoration.ITALIC, false));

        // Fill everything with glass
        ItemStack glass = createGlassPane();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, glass);
        }

        // Screen
        setScreenItems(inv, code, max);

        // Number buttons
        inv.setItem(SLOT_1, createNumberButton("1"));
        inv.setItem(SLOT_2, createNumberButton("2"));
        inv.setItem(SLOT_3, createNumberButton("3"));
        inv.setItem(SLOT_4, createNumberButton("4"));
        inv.setItem(SLOT_5, createNumberButton("5"));
        inv.setItem(SLOT_6, createNumberButton("6"));
        inv.setItem(SLOT_7, createNumberButton("7"));
        inv.setItem(SLOT_8, createNumberButton("8"));
        inv.setItem(SLOT_9, createNumberButton("9"));
        inv.setItem(SLOT_0, createNumberButton("0"));

        // Action buttons
        inv.setItem(SLOT_BACKSPACE, createActionButton("BACKSPACE"));
        inv.setItem(SLOT_RESET, createActionButton("RESET"));
        inv.setItem(SLOT_ENTER, createActionButton("ENTER"));

        player.openInventory(inv);
    }

    // =========================
    // UPDATE SCREEN (вызывается после изменения кода)
    // =========================
    public static void updateScreen(Player player) {
        if (!player.isOnline()) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;
        if (top.getSize() != GUI_SIZE) return;
        if (top.getHolder() != null) return; // not our GUI

        if (!isSafe()) return;
        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(player.getUniqueId());
        setScreenItems(top, code, max);
    }

    // =========================
    // SET SCREEN ITEMS
    // =========================
    private static void setScreenItems(Inventory inv, String code, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i < code.length()) {
                sb.append(code.charAt(i));
            } else {
                sb.append("-");
            }
        }
        String display = sb.toString();
        String fullText = code.isEmpty()
                ? "§8< §7" + display + " §8>"
                : "§8< §f" + display + " §8>";

        ItemStack screenItem = new ItemStack(Material.BOOK);
        ItemMeta meta = screenItem.getItemMeta();
        meta.displayName(Component.text(fullText).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        screenItem.setItemMeta(meta);

        for (int slot : SCREEN_SLOTS) {
            inv.setItem(slot, screenItem);
        }
    }

    // =========================
    // CREATE GLASS PANE (background, hidden tooltip)
    // =========================
    private static ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text("§r").decoration(TextDecoration.ITALIC, false));
        meta.setHideTooltip(true);
        glass.setItemMeta(meta);
        return glass;
    }

    // =========================
    // CREATE NUMBER BUTTON
    // =========================
    private static ItemStack createNumberButton(String number) {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§f" + number).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // =========================
    // CREATE ACTION BUTTON
    // =========================
    private static ItemStack createActionButton(String type) {
        ItemStack item;
        String name;

        switch (type) {
            case "BACKSPACE" -> {
                item = new ItemStack(Material.GOLD_BLOCK);
                name = "§6← §eНазад";
            }
            case "RESET" -> {
                item = new ItemStack(Material.REDSTONE_BLOCK);
                name = "§4❌ §cСброс";
            }
            case "ENTER" -> {
                item = new ItemStack(Material.EMERALD_BLOCK);
                name = "§2✔ §aВвод";
            }
            default -> {
                item = new ItemStack(Material.MAGENTA_WOOL);
                name = "§f?";
            }
        }

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // =========================
    // SAFE CHECK
    // =========================
    private static boolean isSafe() {
        return Main.getInstance() != null && Main.getInstance().getConfig() != null;
    }
}
