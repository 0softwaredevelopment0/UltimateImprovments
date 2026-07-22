package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
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
 * Builds and updates the double-chest GUI for the code panel.
 */
public class CodePanelGUI {

    public static final int GUI_SIZE = 54;
    public static final String GUI_TITLE = "<dark_gray>Code Panel</dark_gray>";

    public static final int[] SCREEN_SLOTS = {2, 3, 4, 5, 6};

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

    public static final int SLOT_RESET = 46;
    public static final int SLOT_BACKSPACE = 48;
    public static final int SLOT_ENTER = 50;

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

    public static void open(Player player) {
        if (!isSafe()) return;

        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                MessageUtil.parse(GUI_TITLE).decoration(TextDecoration.ITALIC, false));

        ItemStack glass = createGlassPane();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, glass);
        }

        setScreenItems(inv, code, max);

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

        inv.setItem(SLOT_BACKSPACE, createActionButton("BACKSPACE"));
        inv.setItem(SLOT_RESET, createActionButton("RESET"));
        inv.setItem(SLOT_ENTER, createActionButton("ENTER"));

        player.openInventory(inv);
    }

    public static void updateScreen(Player player) {
        if (!player.isOnline()) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;
        if (top.getSize() != GUI_SIZE) return;
        if (top.getHolder() != null) return;
        if (!isSafe()) return;
        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(player.getUniqueId());
        setScreenItems(top, code, max);
    }

    private static void setScreenItems(Inventory inv, String code, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i < code.length()) sb.append(code.charAt(i));
            else sb.append("-");
        }
        String display = sb.toString();
        String fullText = code.isEmpty()
                ? "<dark_gray>< </dark_gray><gray>" + display + "</gray><dark_gray> ></dark_gray>"
                : "<dark_gray>< </dark_gray><white>" + display + "</white><dark_gray> ></dark_gray>";

        ItemStack screenItem = new ItemStack(Material.BOOK);
        ItemMeta meta = screenItem.getItemMeta();
        meta.displayName(MessageUtil.parse(fullText).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        screenItem.setItemMeta(meta);

        for (int slot : SCREEN_SLOTS) {
            inv.setItem(slot, screenItem);
        }
    }

    private static ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(MessageUtil.parse("<reset>").decoration(TextDecoration.ITALIC, false));
        meta.setHideTooltip(true);
        glass.setItemMeta(meta);
        return glass;
    }

    private static ItemStack createNumberButton(String number) {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<white>" + number + "</white>").decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createActionButton(String type) {
        ItemStack item;
        String name;

        switch (type) {
            case "BACKSPACE" -> {
                item = new ItemStack(Material.GOLD_BLOCK);
                name = "<gold>← </gold><yellow>Back</yellow>";
            }
            case "RESET" -> {
                item = new ItemStack(Material.REDSTONE_BLOCK);
                name = "<dark_red>❌ </dark_red><red>Reset</red>";
            }
            case "ENTER" -> {
                item = new ItemStack(Material.EMERALD_BLOCK);
                name = "<dark_green>✔ </dark_green><green>Enter</green>";
            }
            default -> {
                item = new ItemStack(Material.MAGENTA_WOOL);
                name = "<white>?</white>";
            }
        }

        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isSafe() {
        return Main.getInstance() != null && Main.getInstance().getConfig() != null;
    }
}
