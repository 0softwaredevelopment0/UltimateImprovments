package com.mcplugin.auth;

import com.mcplugin.Keys;
import com.mcplugin.Main;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthGUI {

    // These constants are intentionally empty — the anvil rename field starts blank

    // Repeating tasks to keep the result slot as Nether Star
    private static final Map<UUID, BukkitTask> resetTasks = new ConcurrentHashMap<>();

    // Cached confirm star item (never changes)
    private static final ItemStack CONFIRM_STAR;
    private static final ItemStack LOGOUT_CONFIRM_STAR;
    private static final ItemStack CHANGE_PASSWORD_CONFIRM_STAR;
    private static final ItemStack CHANGE_PASSWORD_BUTTON;
    private static final ItemStack CANCEL_BUTTON;

    static {
        CONFIRM_STAR = createConfirmStar();
        LOGOUT_CONFIRM_STAR = createLogoutConfirmStar();
        CHANGE_PASSWORD_CONFIRM_STAR = createChangePasswordConfirmStar();
        CHANGE_PASSWORD_BUTTON = createChangePasswordButton();
        CANCEL_BUTTON = createCancelButton();
    }

    // Tracking which players currently have the logout GUI open
    private static final Set<UUID> logoutPlayers = ConcurrentHashMap.newKeySet();

    // Tracking which players currently have the change password GUI open
    private static final Set<UUID> changePasswordPlayers = ConcurrentHashMap.newKeySet();

    // Players currently transitioning between auth GUIs (e.g., login → change-password)
    // Prevents InventoryCloseEvent from interfering with the transition
    private static final Set<UUID> transitioningPlayers = ConcurrentHashMap.newKeySet();

    // Players whose auth GUI is currently being opened (during view.open() call).
    // Used to allow our own inventory in onInventoryOpen even when getMenuType()
    // doesn't return MenuType.ANVIL (Leaf/Paper fork compatibility).
    private static final Set<UUID> openingAuthPlayers = ConcurrentHashMap.newKeySet();

    // =========================
    // LOGOUT GUI TRACKING
    // =========================
    public static boolean isLogoutPlayer(UUID uuid) {
        return logoutPlayers.contains(uuid);
    }

    public static void removeLogoutPlayer(UUID uuid) {
        logoutPlayers.remove(uuid);
    }

    // =========================
    // CHANGE PASSWORD GUI TRACKING
    // =========================
    public static boolean isChangePasswordPlayer(UUID uuid) {
        return changePasswordPlayers.contains(uuid);
    }

    public static void removeChangePasswordPlayer(UUID uuid) {
        changePasswordPlayers.remove(uuid);
    }

    // =========================
    // TRANSITION TRACKING
    // =========================
    public static boolean isTransitioning(UUID uuid) {
        return transitioningPlayers.contains(uuid);
    }

    public static void addTransitioningPlayer(UUID uuid) {
        transitioningPlayers.add(uuid);
    }

    public static void removeTransitioningPlayer(UUID uuid) {
        transitioningPlayers.remove(uuid);
    }

    // =========================
    // OPENING AUTH GUI TRACKING
    // =========================
    public static boolean isOpeningAuthPlayer(UUID uuid) {
        return openingAuthPlayers.contains(uuid);
    }

    // =========================
    // OPEN ANVIL GUI
    // =========================
    public static void openRegister(Player player) {
        openAnvilGUI(player, true);
    }

    public static void openLogin(Player player) {
        openAnvilGUI(player, false);
    }

    private static void openAnvilGUI(Player player, boolean isRegister) {
        Component title = isRegister
                ? Component.text("§8Зарегистрируйтесь")
                : Component.text("§8Введите пароль");

        InventoryView view = MenuType.ANVIL.builder()
                .title(title)
                .build(player);

        Inventory topInv = view.getTopInventory();

        // Slot 0: Instruction item (triggers the rename field)
        topInv.setItem(0, createInstructionItem(isRegister));

        // Slot 1: Change password button (login only)
        if (!isRegister) {
            topInv.setItem(1, CHANGE_PASSWORD_BUTTON);
        }

        // Slot 2: Confirm button — Nether Star with "Подтвердить"
        topInv.setItem(2, CONFIRM_STAR);

        // Mark as opening so onInventoryOpen allows this GUI even on forks
        // where getMenuType() doesn't return MenuType.ANVIL
        UUID uuid = player.getUniqueId();
        openingAuthPlayers.add(uuid);
        try {
            view.open();
        } finally {
            openingAuthPlayers.remove(uuid);
        }

        // Start a repeating task to keep slot 2 as our Nether Star
        // (the anvil recalculates the result slot whenever the rename text changes)
        startResetTask(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage("§e✦ §7Напишите пароль в поле названия, затем нажмите §a✔");
    }

    // =========================
    // GET RENAME TEXT FROM ANVIL (via NMS reflection)
    // =========================
    public static String getAnvilRenameText(Player player) {
        try {
            InventoryView view = player.getOpenInventory();

            // Get the CraftInventoryView to access the NMS container
            // Paper 1.21.11 uses setAccessible reflection approach
            Object craftView = view;
            Class<?> craftViewClass = craftView.getClass();

            // CraftInventoryView.getHandle() returns AbstractContainerMenu
            java.lang.reflect.Method getHandle = craftViewClass.getMethod("getHandle");
            Object handle = getHandle.invoke(craftView);

            // handle is net.minecraft.world.inventory.AnvilMenu
            // In Mojang mappings, the field is "itemName"
            Class<?> anvilClass = handle.getClass();
            java.lang.reflect.Field itemNameField;

            try {
                itemNameField = anvilClass.getDeclaredField("itemName");
            } catch (NoSuchFieldException e) {
                // Try parent class
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

    // =========================
    // REPEATING TASK — KEEP RESULT SLOT AS NETHER STAR
    // =========================
    private static void startResetTask(Player player) {
        UUID uuid = player.getUniqueId();
        cancelResetTask(uuid);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    resetTasks.remove(uuid);
                    return;
                }
                try {
                    var openInv = player.getOpenInventory();
                    if (openInv.getMenuType() != MenuType.ANVIL) {
                        cancel();
                        resetTasks.remove(uuid);
                        return;
                    }
                    // Keep the result slot as our Nether Star
                    openInv.getTopInventory().setItem(2, CONFIRM_STAR);
                    // 🛡 Anti-dup: purge any auth items that slipped into player inventory
                    removeAuthItemsFromPlayer(player);
                } catch (Exception e) {
                    cancel();
                    resetTasks.remove(uuid);
                }
            }
        }.runTaskTimer(Main.getInstance(), 3L, 3L);

        resetTasks.put(uuid, task);
    }

    public static void cancelResetTask(UUID uuid) {
        BukkitTask task = resetTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================
    // REPEATING TASK — KEEP RESULT SLOT AS CHANGE PASSWORD CONFIRM STAR
    // =========================
    private static void startChangePasswordResetTask(Player player) {
        UUID uuid = player.getUniqueId();
        cancelResetTask(uuid);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    resetTasks.remove(uuid);
                    return;
                }
                try {
                    var openInv = player.getOpenInventory();
                    if (openInv.getMenuType() != MenuType.ANVIL) {
                        cancel();
                        resetTasks.remove(uuid);
                        return;
                    }
                    // ВАЖНО: слот 1 (Barrier) устанавливаем ДО слота 2 (Nether Star),
                    // потому что setItem в слот 1 триггерит пересчёт результата наковальни
                    // и затирает звезду в слоте 2. Сначала триггерим пересчёт,
                    // потом финально устанавливаем звезду.
                    openInv.getTopInventory().setItem(1, CANCEL_BUTTON);
                    openInv.getTopInventory().setItem(2, CHANGE_PASSWORD_CONFIRM_STAR);
                    // 🛡 Anti-dup: purge any auth items that slipped into player inventory
                    removeAuthItemsFromPlayer(player);
                } catch (Exception e) {
                    cancel();
                    resetTasks.remove(uuid);
                }
            }
        }.runTaskTimer(Main.getInstance(), 3L, 3L);

        resetTasks.put(uuid, task);
    }

    // =========================
    // REPEATING TASK — KEEP RESULT SLOT AS LOGOUT CONFIRM STAR
    // =========================
    private static void startLogoutResetTask(Player player) {
        UUID uuid = player.getUniqueId();
        cancelResetTask(uuid);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    resetTasks.remove(uuid);
                    return;
                }
                try {
                    var openInv = player.getOpenInventory();
                    if (openInv.getMenuType() != MenuType.ANVIL) {
                        cancel();
                        resetTasks.remove(uuid);
                        return;
                    }
                    openInv.getTopInventory().setItem(2, LOGOUT_CONFIRM_STAR);
                    // 🛡 Anti-dup: purge any auth items that slipped into player inventory
                    removeAuthItemsFromPlayer(player);
                } catch (Exception e) {
                    cancel();
                    resetTasks.remove(uuid);
                }
            }
        }.runTaskTimer(Main.getInstance(), 3L, 3L);

        resetTasks.put(uuid, task);
    }

    // =========================
    // OPEN CHANGE PASSWORD GUI (NEW DESIGN)
    // Slot 0: Info item (far left)
    // Slot 1: Cancel button (center)
    // Slot 2: Confirm button (right)
    // =========================
    public static void openChangePassword(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing reset task from login GUI
        cancelResetTask(uuid);

        // Mark as transitioning BEFORE view.open() so onInventoryClose
        // knows this close is from switching GUIs, not user cancellation
        transitioningPlayers.add(uuid);
        changePasswordPlayers.add(uuid);

        InventoryView view = MenuType.ANVIL.builder()
                .title(Component.text("§8✎ Смена пароля"))
                .build(player);

        openingAuthPlayers.add(uuid);
        try {
            view.open();
        } finally {
            transitioningPlayers.remove(uuid); // Transition complete
            openingAuthPlayers.remove(uuid);
        }

        // Устанавливаем предметы ПОСЛЕ view.open(), чтобы контейнер наковальни
        // не пересчитал слот результата (слот 2) при инициализации и не стёр нашу звезду
        Inventory topInv = view.getTopInventory();
        topInv.setItem(0, createChangePasswordInstructionItem());
        topInv.setItem(1, CANCEL_BUTTON);
        topInv.setItem(2, CHANGE_PASSWORD_CONFIRM_STAR);

        startChangePasswordResetTask(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage("§e✦ §7Введите новый пароль в поле названия, затем нажмите §a✔");
    }

    // =========================
    // OPEN LOGOUT GUI (authenticated players)
    // =========================
    public static void openLogout(Player player) {
        UUID uuid = player.getUniqueId();

        // Mark transitioning so close event doesn't interfere
        transitioningPlayers.add(uuid);
        logoutPlayers.add(uuid);

        InventoryView view = MenuType.ANVIL.builder()
                .title(Component.text("§8✦ Выход из аккаунта"))
                .build(player);

        Inventory topInv = view.getTopInventory();

        topInv.setItem(0, createLogoutInstructionItem());
        topInv.setItem(2, LOGOUT_CONFIRM_STAR);

        openingAuthPlayers.add(uuid);
        try {
            view.open();
        } finally {
            transitioningPlayers.remove(uuid); // Transition complete
            openingAuthPlayers.remove(uuid);
        }

        startLogoutResetTask(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.0f);
        player.sendMessage("§e✦ §7Введите ваш пароль для выхода из аккаунта.");
    }

    // =========================
    // REMOVE AUTH ITEMS FROM PLAYER INVENTORY (anti-dup)
    // Called when the GUI closes — removes any GUI items that fell into player's inventory
    // =========================
    public static void removeAuthItemsFromPlayer(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getItemMeta() != null) {
                var pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(Keys.AUTH_GUI, PersistentDataType.BOOLEAN)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    // =========================
    // ITEM CREATORS
    // =========================
    private static ItemStack tagAuthItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.AUTH_GUI, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInstructionItem(boolean isRegister) {
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

    private static ItemStack createLogoutInstructionItem() {
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

    private static ItemStack createChangePasswordInstructionItem() {
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
