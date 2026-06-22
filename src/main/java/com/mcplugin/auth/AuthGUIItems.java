package com.mcplugin.auth;

import com.mcplugin.Keys;
import com.mcplugin.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Creates items for the auth GUI.
 * All static items are cached on class load.
 */
public class AuthGUIItems {

    private AuthGUIItems() {}

    // =========================
    // CACHED ITEMS
    // =========================
    public static final ItemStack CONFIRM_STAR = createConfirmStar();
    public static final ItemStack LOGOUT_CONFIRM_STAR = createLogoutConfirmStar();
    public static final ItemStack CHANGE_PASSWORD_CONFIRM_STAR = createChangePasswordConfirmStar();
    public static final ItemStack CHANGE_PASSWORD_BUTTON = createChangePasswordButton();
    public static final ItemStack CANCEL_BUTTON = createCancelButton();

    // =========================
    // TAG WITH AUTH_GUI PDC KEY
    // =========================
    public static ItemStack tagAuthItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && Keys.AUTH_GUI != null) {
            meta.getPersistentDataContainer().set(Keys.AUTH_GUI, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    // =========================
    // ITEM CREATORS
    // =========================
    public static ItemStack createInstructionItem(boolean isRegister) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(" ")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse(isRegister ? "<gold>✦ Registration</gold>" : "<aqua>✦ Login</aqua>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>Type your password in the</gray>").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>item name field above</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse(isRegister ? "<gray>Minimum </gray><yellow>4</yellow><gray> characters</gray>" : "<gray>Enter your password</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<gray>Then click </gray><green>✔</green><gray> below</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    public static ItemStack createLogoutInstructionItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(" ")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<red>✦ Account Logout</red>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>Type your password in the</gray>").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>item name field above</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<gray>Then click </gray><red>✔</red><gray> to log out</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    public static ItemStack createChangePasswordInstructionItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(" ")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gold>✎ Change Password</gold>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>Type your </gray><yellow>new password</yellow><gray> in the</gray>").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>item name field above</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<gray>Minimum </gray><yellow>4</yellow><gray> characters</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<gray>Then click </gray><green>✔</green><gray> to confirm</gray>").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>Or click </gray><red>✖</red><gray> to cancel</gray>").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>Press </gray><white>Escape</white><gray> to exit</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createConfirmStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<green><bold>✔ Confirm</bold></green>").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to confirm password</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createLogoutConfirmStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<red><bold>✔ Logout</bold></red>").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to log out of your account</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createChangePasswordConfirmStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<green><bold>✔ Confirm</bold></green>").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to change password</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createChangePasswordButton() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<gold><bold>✎ Change Password</bold></gold>").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to change password</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>First enter your current</gray>").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<dark_gray>┃</dark_gray> <gray>password in the name field above</gray>").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                MessageUtil.parse("<gray>Then click </gray><gold>✎</gold><gray> here</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse("<red><bold>✖ Cancel</bold></red>").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse("<gray>Click to cancel password change</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }
}
