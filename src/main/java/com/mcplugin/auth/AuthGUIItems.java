package com.mcplugin.auth;

import com.mcplugin.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Создание предметов для GUI авторизации.
 * Все статические предметы кэшируются при загрузке класса.
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
                Component.text(isRegister
                        ? "§6✦ Регистрация"
                        : "§3✦ Вход").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7Напишите пароль в поле").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7названия предмета выше").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text(isRegister
                        ? "§7Минимум §e4 §7символа"
                        : "§7Введите ваш пароль").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§7Затем нажмите §a✔ §7внизу").decoration(TextDecoration.ITALIC, false)
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
                Component.text("§c✦ Выход из аккаунта").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7Напишите ваш пароль в поле").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7названия предмета выше").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§7Затем нажмите §c✔ §7для выхода").decoration(TextDecoration.ITALIC, false)
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
                Component.text("§6✎ Смена пароля").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7Напишите §eновый пароль§7 в поле").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7названия предмета выше").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§7Минимум §e4 §7символа").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§7Затем нажмите §a✔ §7подтвердить").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7Или нажмите §c✖ §7отмена").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7Нажмите §fEscape§7 для выхода").decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createConfirmStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§a§l✔ Подтвердить").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7Нажмите, чтобы подтвердить пароль").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createLogoutConfirmStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§c§l✔ Выйти").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7Нажмите, чтобы выйти из аккаунта").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createChangePasswordConfirmStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§a§l✔ Подтвердить").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7Нажмите, чтобы сменить пароль").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createChangePasswordButton() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§6§l✎ Сменить пароль").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7Нажмите, чтобы сменить пароль").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7Сначала введите ваш текущий").decoration(TextDecoration.ITALIC, false),
                Component.text("§8┃ §7пароль в поле названия выше").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§7Затем нажмите §6✎ §7здесь").decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }

    private static ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§c§l✖ Отмена").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7Нажмите, чтобы отменить смену пароля").decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return tagAuthItem(item);
    }
}
