package com.mcplugin.auth;

import com.mcplugin.Main;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.mcplugin.Keys;

import java.util.UUID;

public class AuthListener implements Listener {

    // =========================
    // CHECK IF AUTH IS ENABLED
    // =========================
    private boolean isAuthEnabled() {
        try {
            return Main.getInstance() != null
                    && Main.getInstance().getConfig() != null
                    && Main.getInstance().getConfig().getBoolean("auth.enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    // =========================
    // CHECK IF DUP NAME CHECK IS ENABLED
    // =========================
    private boolean isDupNameCheckEnabled() {
        try {
            return Main.getInstance() != null
                    && Main.getInstance().getConfig() != null
                    && Main.getInstance().getConfig().getBoolean("auth.check_duplicate_name.enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    // =========================
    // IS THIS OUR AUTH GUI? (anvil)
    // =========================
    private boolean isAuthGUI(InventoryClickEvent event) {
        return event.getView().getMenuType() == MenuType.ANVIL;
    }

    private boolean isAuthGUI(InventoryCloseEvent event) {
        return event.getView().getMenuType() == MenuType.ANVIL;
    }

    private boolean isAuthGUI(InventoryOpenEvent event) {
        return event.getView().getMenuType() == MenuType.ANVIL;
    }

    // =========================
    // GET CONFIG MESSAGE
    // =========================
    private String getConfigMessage(String path, String def) {
        try {
            return Main.getInstance().getConfig().getString("auth.messages." + path, def);
        } catch (Exception e) {
            return def;
        }
    }

    // =========================
    // 🔒 PRE-LOGIN — проверка дубликатов ников ДО того, как Minecraft кинет оригинального игрока
    // AsyncPlayerPreLoginEvent срабатывает РАНЬШЕ, чем сервер решит кикать игроков
    // Это критически важно для пиратских серверов, где одинаковый ник = одинаковый UUID
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isAuthEnabled()) return;
        if (!isDupNameCheckEnabled()) return;

        String newPlayerName = event.getName();
        UUID newPlayerUuid = event.getUniqueId();

        // Проверяем: есть ли уже онлайн игрок с таким же ником, но другим UUID
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(newPlayerName)) {
                String dupMessage = getConfigMessage("duplicate_name_kick",
                        "§c❌ Игрок с таким ником уже на сервере!\n§7Пожалуйста, зайдите под другим ником.");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        "§6✦ MC-Plugin\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        dupMessage + "\n\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━"
                );
                return;
            }
        }
    }

    // =========================
    // JOIN → open GUI
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isAuthEnabled()) return;
        Player player = event.getPlayer();
        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            manager.handleJoin(player);
        }
    }

    // =========================
    // QUIT → cleanup
    // =========================
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            manager.removePlayer(uuid);
        }
        AuthGUI.cancelResetTask(uuid);
        AuthGUI.removeLogoutPlayer(uuid);
        AuthGUI.removeChangePasswordPlayer(uuid);
        AuthGUI.removeTransitioningPlayer(uuid);
    }

    // =========================
    // CHECK IF PLAYER NEEDS AUTH
    // =========================
    private boolean needsAuth(Player player) {
        if (!isAuthEnabled()) return false;
        AuthManager manager = AuthManager.getInstance();
        return manager != null && !manager.isAuthenticated(player.getUniqueId());
    }

    // =========================
    // BLOCK MOVEMENT if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    // =========================
    // BLOCK INTERACT if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
    }

    // =========================
    // BLOCK BREAK if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
    }

    // =========================
    // BLOCK PLACE if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
    }

    // =========================
    // BLOCK CHAT / COMMANDS if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
        player.sendMessage("§c❌ Сначала авторизуйтесь! Введите пароль в открывшемся окне.");
    }

    // =========================
    // BLOCK ITEM DROP if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
    }

    // =========================
    // 🛡 ANTI-DUP: cancel item spawn if it has AUTH_GUI PDC tag
    // This catches items that fall to ground when anvil GUI closes
    // and the player's inventory is full
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(Keys.AUTH_GUI, PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
        }
    }

    // =========================
    // 🛡 ANTI-DUP: clear cursor + purge auth items from inventory
    // Paper often fails to fully cancel anvil result slot clicks —
    // items can end up in the player's cursor or inventory.
    // =========================
    private void antiDupCleanup(Player player) {
        player.setItemOnCursor(null);
        AuthGUI.removeAuthItemsFromPlayer(player);
    }

    // =========================
    // HANDLE ANVIL CLICK
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isAuthGUI(event)) return;

        UUID uuid = player.getUniqueId();

        // =========================
        // CHANGE PASSWORD GUI (player is changing password)
        // Slot 0: info, Slot 1: cancel, Slot 2: confirm
        // =========================
        if (AuthGUI.isChangePasswordPlayer(uuid)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2) {
                handleChangePasswordConfirm(player);
            } else if (event.getRawSlot() == 1) {
                handleChangePasswordCancel(player);
            }
            antiDupCleanup(player);
            return;
        }

        // =========================
        // LOGOUT GUI (authenticated player)
        // =========================
        if (AuthGUI.isLogoutPlayer(uuid)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2) {
                handleLogout(player);
            }
            antiDupCleanup(player);
            return;
        }

        // =========================
        // LOGIN / REGISTER GUI (non-authenticated player)
        // =========================
        if (!needsAuth(player)) return;

        event.setCancelled(true);

        if (event.getRawSlot() == 2) {
            handleEnter(player);
        } else if (event.getRawSlot() == 1) {
            // Slot 1 = change password button (only for registered players)
            handleChangePasswordRequest(player);
        }
        antiDupCleanup(player);
    }

    // =========================
    // HANDLE LOGOUT CONFIRM — verify password, kick on success
    // =========================
    private void handleLogout(Player player) {
        String password = AuthGUI.getAnvilRenameText(player);

        if (password == null || password.isEmpty()) {
            player.sendMessage("§c❌ Введите ваш пароль в поле названия предмета!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) return;

        if (manager.handleLogout(player, password)) {
            // handleLogout already kicks the player on success
        } else {
            player.sendMessage("§c❌ Неверный пароль! Попробуйте ещё раз.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        }
    }

    // =========================
    // HANDLE CHANGE PASSWORD REQUEST — verify current password, open change GUI
    // =========================
    private void handleChangePasswordRequest(Player player) {
        UUID uuid = player.getUniqueId();

        // ⏱ Rate limit check
        if (!AuthManager.getInstance().checkRequestCooldown(player)) return;

        // Only registered players can change password
        if (!com.mcplugin.auth.AuthDatabase.isRegistered(uuid)) {
            player.sendMessage("§c❌ Вы ещё не зарегистрированы! Сначала придумайте пароль.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        String currentPassword = AuthGUI.getAnvilRenameText(player);

        if (currentPassword == null || currentPassword.isEmpty()) {
            player.sendMessage("§c❌ Введите ваш текущий пароль в поле названия предмета!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        // Verify current password
        if (!com.mcplugin.auth.AuthDatabase.checkPassword(uuid, currentPassword)) {
            player.sendMessage("§c❌ Неверный пароль!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        // Open change password GUI (new anvil GUI with info, cancel, confirm)
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);
        AuthGUI.openChangePassword(player);
    }

    // =========================
    // HANDLE CHANGE PASSWORD CANCEL — go back to login GUI
    // =========================
    private void handleChangePasswordCancel(Player player) {
        UUID uuid = player.getUniqueId();

        AuthGUI.cancelResetTask(uuid);
        AuthGUI.removeChangePasswordPlayer(uuid);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 1.0f);
        player.sendMessage("§e✦ §7Смена пароля отменена.");

        // Mark as transitioning so InventoryCloseEvent doesn't fire re-open logic
        AuthGUI.addTransitioningPlayer(uuid);

        // Open login GUI directly (not via reopenAfterDelay) to avoid self-loop
        AuthManager manager = AuthManager.getInstance();
        if (manager != null && !manager.isAuthenticated(uuid)) {
            if (com.mcplugin.auth.AuthDatabase.isRegistered(uuid)) {
                AuthGUI.openLogin(player);
            } else {
                AuthGUI.openRegister(player);
            }
        }

        AuthGUI.removeTransitioningPlayer(uuid);
    }

    // =========================
    // HANDLE CHANGE PASSWORD CONFIRM — save new password, authenticate
    // =========================
    private void handleChangePasswordConfirm(Player player) {
        String newPassword = AuthGUI.getAnvilRenameText(player);
        UUID uuid = player.getUniqueId();

        // ⏱ Rate limit check
        if (!AuthManager.getInstance().checkRequestCooldown(player)) return;

        if (newPassword == null || newPassword.isEmpty()) {
            player.sendMessage("§c❌ Введите новый пароль в поле названия предмета!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        int minLen = Main.getInstance().getConfig().getInt("auth.min_password_length", 4);
        if (newPassword.length() < minLen) {
            player.sendMessage("§c❌ Пароль должен быть не менее " + minLen + " символов!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            // Clean up change password tracking FIRST to prevent state loop
            AuthGUI.cancelResetTask(uuid);
            AuthGUI.removeChangePasswordPlayer(uuid);
            // Handle the password change + authenticate
            manager.handleSelfChangePassword(player, newPassword);
        }
    }

    // =========================
    // HANDLE ENTER — read password from anvil via reflection
    // =========================
    private void handleEnter(Player player) {
        String password = AuthGUI.getAnvilRenameText(player);

        // If player hasn't typed anything, the rename field is empty
        if (password == null || password.isEmpty()) {
            player.sendMessage("§c❌ Введите пароль в поле названия предмета!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        int minLen = Main.getInstance().getConfig().getInt("auth.min_password_length", 4);
        if (password.length() < minLen) {
            player.sendMessage("§c❌ Пароль должен быть не менее " + minLen + " символов!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            manager.handlePasswordSubmit(player, password);
        }
    }

    // =========================
    // ESCAPE PROTECTION + ANTI-DUP
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isAuthGUI(event)) return;

        UUID uuid = player.getUniqueId();

        // 🚫 TRANSITION CHECK: if the GUI is being switched (e.g. login → change-password),
        // skip the close handling to prevent re-open loop
        if (AuthGUI.isTransitioning(uuid)) {
            return;
        }

        // 🛡 ANTI-DUP: Remove any auth GUI items from player's inventory
        AuthGUI.removeAuthItemsFromPlayer(player);

        // Change password GUI — prevent closing by Escape, re-open instead
        if (AuthGUI.isChangePasswordPlayer(uuid)) {
            AuthGUI.cancelResetTask(uuid);
            player.sendMessage("§c❌ Вы не можете закрыть окно смены пароля!");
            // Re-open the change password GUI after a short delay (like login/register)
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    if (!AuthGUI.isChangePasswordPlayer(uuid)) return;
                    AuthGUI.openChangePassword(player);
                }
            }.runTaskLater(Main.getInstance(), 5L);
            return;
        }

        // Logout GUI — just clean up tracking
        if (AuthGUI.isLogoutPlayer(uuid)) {
            AuthGUI.cancelResetTask(uuid);
            AuthGUI.removeLogoutPlayer(uuid);
            player.sendMessage("§e✦ §7Выход из аккаунта отменён.");
            return;
        }

        // Login/Register GUI — re-open instead of kicking
        if (!needsAuth(player)) return;

        AuthGUI.cancelResetTask(uuid);
        player.sendMessage("§c❌ Вы не можете закрыть окно авторизации! Пожалуйста, введите пароль.");
        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            manager.reopenAfterDelay(player);
        }
    }

    // =========================
    // PREVENT OPENING OTHER INVENTORIES
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Allow change password GUI
        if (AuthGUI.isChangePasswordPlayer(player.getUniqueId())) {
            if (isAuthGUI(event)) return;
            event.setCancelled(true);
            return;
        }

        // Allow logout GUI for authenticated players
        if (AuthGUI.isLogoutPlayer(player.getUniqueId())) {
            if (isAuthGUI(event)) return;
            event.setCancelled(true);
            return;
        }

        if (!needsAuth(player)) return;
        // Allow our own GUI, block everything else
        if (isAuthGUI(event)) return;
        event.setCancelled(true);
    }
}
