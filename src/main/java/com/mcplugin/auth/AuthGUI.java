package com.mcplugin.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;

import java.util.UUID;

/**
 * Фасад для открытия GUI авторизации.
 * <p>
 * Делегирует создание предметов → {@link AuthGUIItems},
 * трекинг состояний → {@link AuthGUITracker},
 * чтение ввода → {@link AuthGUIAnvilReader}.
 * <p>
 * Использует {@link MenuType#ANVIL} для создания настоящей наковальни
 * с полем переименования (rename field), куда игрок вводит пароль.
 */
public class AuthGUI {

    private AuthGUI() {}

    // =========================
    // OPEN LOGIN / REGISTER GUI
    // =========================
    public static void openRegister(Player player) {
        openAnvilGUI(player, true);
    }

    public static void openLogin(Player player) {
        openAnvilGUI(player, false);
    }

    private static void openAnvilGUI(Player player, boolean isRegister) {
        Component title = isRegister
                ? Component.text("Зарегистрируйтесь", NamedTextColor.DARK_GRAY)
                : Component.text("Введите пароль", NamedTextColor.DARK_GRAY);

        InventoryView view = MenuType.ANVIL.builder()
                .title(title)
                .build(player);

        Inventory topInv = view.getTopInventory();

        // Slot 0: Instruction item (triggers the rename field)
        topInv.setItem(0, AuthGUIItems.createInstructionItem(isRegister));

        // Slot 1: Change password button (login only)
        if (!isRegister) {
            topInv.setItem(1, AuthGUIItems.CHANGE_PASSWORD_BUTTON);
        }

        // Slot 2: Confirm button — Nether Star with "Подтвердить"
        topInv.setItem(2, AuthGUIItems.CONFIRM_STAR);

        // Mark as opening so onInventoryOpen allows this GUI even on forks
        // where getMenuType() doesn't return MenuType.ANVIL
        UUID uuid = player.getUniqueId();
        AuthGUITracker.addOpeningAuthPlayer(uuid);
        try {
            view.open();
        } finally {
            AuthGUITracker.removeOpeningAuthPlayer(uuid);
        }

        // Start a repeating task to keep slot 2 as our Nether Star
        // (the anvil recalculates the result slot whenever the rename text changes)
        AuthGUITracker.startResetTask(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage("§e✦ §7Напишите пароль в поле названия, затем нажмите §a✔");
    }

    // =========================
    // OPEN CHANGE PASSWORD GUI
    // Slot 0: Info item (far left)
    // Slot 1: Cancel button (center)
    // Slot 2: Confirm button (right)
    // =========================
    public static void openChangePassword(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing reset task from login GUI
        AuthGUITracker.cancelResetTask(uuid);

        // Mark as transitioning BEFORE view.open() so onInventoryClose
        // knows this close is from switching GUIs, not user cancellation
        AuthGUITracker.addTransitioningPlayer(uuid);
        AuthGUITracker.addChangePasswordPlayer(uuid);

        InventoryView view = MenuType.ANVIL.builder()
                .title(Component.text("Смена пароля", NamedTextColor.DARK_GRAY))
                .build(player);

        AuthGUITracker.addOpeningAuthPlayer(uuid);
        try {
            view.open();
        } finally {
            AuthGUITracker.removeTransitioningPlayer(uuid);
            AuthGUITracker.removeOpeningAuthPlayer(uuid);
        }

        // Set items AFTER view.open() so the anvil container
        // doesn't recalculate the result slot (slot 2) during initialization
        Inventory topInv = view.getTopInventory();
        topInv.setItem(0, AuthGUIItems.createChangePasswordInstructionItem());
        topInv.setItem(1, AuthGUIItems.CANCEL_BUTTON);
        topInv.setItem(2, AuthGUIItems.CHANGE_PASSWORD_CONFIRM_STAR);

        AuthGUITracker.startChangePasswordResetTask(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage("§e✦ §7Введите новый пароль в поле названия, затем нажмите §a✔");
    }

    // =========================
    // OPEN LOGOUT GUI (authenticated players)
    // =========================
    public static void openLogout(Player player) {
        UUID uuid = player.getUniqueId();

        AuthGUITracker.addTransitioningPlayer(uuid);
        AuthGUITracker.addLogoutPlayer(uuid);

        InventoryView view = MenuType.ANVIL.builder()
                .title(Component.text("Выход из аккаунта", NamedTextColor.DARK_GRAY))
                .build(player);

        AuthGUITracker.addOpeningAuthPlayer(uuid);
        try {
            view.open();
        } finally {
            AuthGUITracker.removeTransitioningPlayer(uuid);
            AuthGUITracker.removeOpeningAuthPlayer(uuid);
        }

        // Set items AFTER view.open() so the anvil container
        // doesn't recalculate the result slot (slot 2) during initialization
        Inventory topInv = view.getTopInventory();
        topInv.setItem(0, AuthGUIItems.createLogoutInstructionItem());
        topInv.setItem(2, AuthGUIItems.LOGOUT_CONFIRM_STAR);

        AuthGUITracker.startLogoutResetTask(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage("§e✦ §7Введите ваш пароль для выхода из аккаунта.");
    }
}
