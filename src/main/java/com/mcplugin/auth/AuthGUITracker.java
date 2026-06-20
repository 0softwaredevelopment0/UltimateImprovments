package com.mcplugin.auth;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Трекинг состояний GUI авторизации и задачи сброса слотов.
 * <p>
 * Отслеживает:
 * - Какие игроки в меню выхода (logout)
 * - Какие игроки в меню смены пароля
 * - Какие игроки переключаются между GUI
 * - Какие игроки открывают auth GUI прямо сейчас
 * - Repeating-задачи для удержания звёздочки в слоте результата
 */
public class AuthGUITracker {

    private AuthGUITracker() {}

    // =========================
    // RESET TASKS — keep result slot as Nether Star
    // =========================
    private static final Map<UUID, BukkitTask> resetTasks = new ConcurrentHashMap<>();

    // =========================
    // TRACKING SETS
    // =========================
    private static final Set<UUID> logoutPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> changePasswordPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> transitioningPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> openingAuthPlayers = ConcurrentHashMap.newKeySet();

    // =========================
    // LOGOUT GUI
    // =========================
    public static boolean isLogoutPlayer(UUID uuid) {
        return logoutPlayers.contains(uuid);
    }

    public static void addLogoutPlayer(UUID uuid) {
        logoutPlayers.add(uuid);
    }

    public static void removeLogoutPlayer(UUID uuid) {
        logoutPlayers.remove(uuid);
    }

    // =========================
    // CHANGE PASSWORD GUI
    // =========================
    public static boolean isChangePasswordPlayer(UUID uuid) {
        return changePasswordPlayers.contains(uuid);
    }

    public static void addChangePasswordPlayer(UUID uuid) {
        changePasswordPlayers.add(uuid);
    }

    public static void removeChangePasswordPlayer(UUID uuid) {
        changePasswordPlayers.remove(uuid);
    }

    // =========================
    // TRANSITION
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
    // OPENING AUTH GUI
    // =========================
    public static boolean isOpeningAuthPlayer(UUID uuid) {
        return openingAuthPlayers.contains(uuid);
    }

    public static void addOpeningAuthPlayer(UUID uuid) {
        openingAuthPlayers.add(uuid);
    }

    public static void removeOpeningAuthPlayer(UUID uuid) {
        openingAuthPlayers.remove(uuid);
    }

    // =========================
    // CANCEL RESET TASK
    // =========================
    public static void cancelResetTask(UUID uuid) {
        BukkitTask task = resetTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================
    // START RESET TASK — keep result slot as star (login/register)
    // =========================
    public static void startResetTask(Player player) {
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
                    openInv.getTopInventory().setItem(2, AuthGUIItems.CONFIRM_STAR);
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
    // START CHANGE PASSWORD RESET TASK
    // =========================
    public static void startChangePasswordResetTask(Player player) {
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
                    // Slot 1 (Barrier) must be set BEFORE slot 2 (Nether Star)
                    // because setItem in slot 1 triggers anvil result recalculation
                    // which overwrites the star in slot 2.
                    openInv.getTopInventory().setItem(1, AuthGUIItems.CANCEL_BUTTON);
                    openInv.getTopInventory().setItem(2, AuthGUIItems.CHANGE_PASSWORD_CONFIRM_STAR);
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
    // START LOGOUT RESET TASK
    // =========================
    public static void startLogoutResetTask(Player player) {
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
                    openInv.getTopInventory().setItem(2, AuthGUIItems.LOGOUT_CONFIRM_STAR);
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
    // CLEANUP ON QUIT
    // =========================
    public static void cleanupPlayer(UUID uuid) {
        cancelResetTask(uuid);
        removeLogoutPlayer(uuid);
        removeChangePasswordPlayer(uuid);
        removeTransitioningPlayer(uuid);
        removeOpeningAuthPlayer(uuid);
    }

    // =========================
    // REMOVE AUTH ITEMS FROM PLAYER INVENTORY (anti-dup)
    // =========================
    public static void removeAuthItemsFromPlayer(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getItemMeta() != null) {
                var pdc = item.getItemMeta().getPersistentDataContainer();
                if (Keys.AUTH_GUI != null && pdc.has(Keys.AUTH_GUI, PersistentDataType.BOOLEAN)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    /**
     * Anti-dup: clear cursor + purge auth items from inventory.
     * Paper often fails to fully cancel anvil result slot clicks —
     * items can end up in the player's cursor or inventory.
     */
    public static void antiDupCleanup(Player player) {
        player.setItemOnCursor(null);
        removeAuthItemsFromPlayer(player);
    }
}
