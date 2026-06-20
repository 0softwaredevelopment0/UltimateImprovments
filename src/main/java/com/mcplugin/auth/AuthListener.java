package com.mcplugin.auth;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import com.mcplugin.util.MessageUtil;
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

import java.util.UUID;

/**
 * Слушатель событий для системы авторизации.
 * <p>
 * Блокирует действия неавторизованных игроков, обрабатывает клики по GUI,
 * управляет открытием/закрытием окон авторизации.
 */
public class AuthListener implements Listener {

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
    // 🔒 PRE-LOGIN — duplicate name check BEFORE Minecraft kicks the original player
    // AsyncPlayerPreLoginEvent fires EARLIER than the server decides to kick players
    // Critical for offline-mode servers where same name = same UUID
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!AuthConfig.isEnabled()) return;
        if (!AuthConfig.isDupNameCheckEnabled()) return;

        String newPlayerName = event.getName();
        UUID newPlayerUuid = event.getUniqueId();

        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(newPlayerName)) {
                String dupMessage = AuthConfig.getMessage("duplicate_name_kick",
                        "<red>❌ Игрок с таким ником уже на сервере!</red>\n<gray>Пожалуйста, зайдите под другим ником.</gray>");
                String dupParsed = MessageUtil.legacy(dupMessage);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        "§6✦ MC-Plugin\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        dupParsed + "\n\n" +
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
        if (!AuthConfig.isEnabled()) return;
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
        AuthGUITracker.cleanupPlayer(uuid);
    }

    // =========================
    // CHECK IF PLAYER NEEDS AUTH
    // =========================
    private boolean needsAuth(Player player) {
        return AuthPlayerState.getInstance() != null && AuthPlayerState.getInstance().needsAuth(player);
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
        player.sendMessage(MessageUtil.parse("<red>❌ Сначала авторизуйтесь! Введите пароль в открывшемся окне.</red>"));
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
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (Keys.AUTH_GUI == null) return;
        ItemStack item = event.getEntity().getItemStack();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(Keys.AUTH_GUI, PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
        }
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
        // CHANGE PASSWORD GUI
        // Slot 0: info, Slot 1: cancel, Slot 2: confirm
        // =========================
        if (AuthGUITracker.isChangePasswordPlayer(uuid)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2) {
                handleChangePasswordConfirm(player);
            } else if (event.getRawSlot() == 1) {
                handleChangePasswordCancel(player);
            }
            AuthGUITracker.antiDupCleanup(player);
            return;
        }

        // =========================
        // LOGOUT GUI (authenticated player)
        // =========================
        if (AuthGUITracker.isLogoutPlayer(uuid)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2) {
                handleLogout(player);
            }
            AuthGUITracker.antiDupCleanup(player);
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
        AuthGUITracker.antiDupCleanup(player);
    }

    // =========================
    // HANDLE LOGOUT CONFIRM
    // =========================
    private void handleLogout(Player player) {
        String password = AuthGUIAnvilReader.getAnvilRenameText(player);

        if (password == null || password.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<red>❌ Введите ваш пароль в поле названия предмета!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) return;

        if (manager.handleLogout(player, password)) {
            // handleLogout already kicks the player on success
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌ Неверный пароль! Попробуйте ещё раз.</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        }
    }

    // =========================
    // HANDLE CHANGE PASSWORD REQUEST
    // =========================
    private void handleChangePasswordRequest(Player player) {
        UUID uuid = player.getUniqueId();

        // Rate limit check
        if (!AuthManager.getInstance().checkRequestCooldown(player)) return;

        // Only registered players can change password
        if (!AuthDatabase.isRegistered(uuid)) {
            player.sendMessage(MessageUtil.parse("<red>❌ Вы ещё не зарегистрированы! Сначала придумайте пароль.</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        String currentPassword = AuthGUIAnvilReader.getAnvilRenameText(player);

        if (currentPassword == null || currentPassword.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<red>❌ Введите ваш текущий пароль в поле названия предмета!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        // Verify current password
        if (!AuthDatabase.checkPassword(uuid, currentPassword)) {
            player.sendMessage(MessageUtil.parse("<red>❌ Неверный пароль!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        // Open change password GUI
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);
        AuthGUI.openChangePassword(player);
    }

    // =========================
    // HANDLE CHANGE PASSWORD CANCEL
    // =========================
    private void handleChangePasswordCancel(Player player) {
        UUID uuid = player.getUniqueId();

        AuthGUITracker.cancelResetTask(uuid);
        AuthGUITracker.removeChangePasswordPlayer(uuid);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 1.0f);
        player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <gray>Смена пароля отменена.</gray>"));

        // Mark as transitioning so InventoryCloseEvent doesn't fire re-open logic
        AuthGUITracker.addTransitioningPlayer(uuid);

        // Open login GUI directly (not via reopenAfterDelay) to avoid self-loop
        AuthManager manager = AuthManager.getInstance();
        if (manager != null && !manager.isAuthenticated(uuid)) {
            if (AuthDatabase.isRegistered(uuid)) {
                AuthGUI.openLogin(player);
            } else {
                AuthGUI.openRegister(player);
            }
        }

        AuthGUITracker.removeTransitioningPlayer(uuid);
    }

    // =========================
    // HANDLE CHANGE PASSWORD CONFIRM
    // =========================
    private void handleChangePasswordConfirm(Player player) {
        String newPassword = AuthGUIAnvilReader.getAnvilRenameText(player);
        UUID uuid = player.getUniqueId();

        // Rate limit check
        if (!AuthManager.getInstance().checkRequestCooldown(player)) return;

        if (newPassword == null || newPassword.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<red>❌ Введите новый пароль в поле названия предмета!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();
        if (newPassword.length() < minLen) {
            player.sendMessage("§c❌ Пароль должен быть не менее " + minLen + " символов!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage("§c❌ Пароль не должен превышать " + maxLen + " символов!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            AuthGUITracker.cancelResetTask(uuid);
            AuthGUITracker.removeChangePasswordPlayer(uuid);
            manager.handleSelfChangePassword(player, newPassword);
        }
    }

    // =========================
    // HANDLE ENTER — read password from anvil via reflection
    // =========================
    private void handleEnter(Player player) {
        String password = AuthGUIAnvilReader.getAnvilRenameText(player);

        if (password == null || password.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<red>❌ Введите пароль в поле названия предмета!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();
        if (password.length() < minLen) {
            player.sendMessage("§c❌ Пароль должен быть не менее " + minLen + " символов!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }
        if (password.length() > maxLen) {
            player.sendMessage("§c❌ Пароль не должен превышать " + maxLen + " символов!");
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

        // Skip if transitioning between GUIs
        if (AuthGUITracker.isTransitioning(uuid)) {
            return;
        }

        // Anti-dup
        AuthGUITracker.removeAuthItemsFromPlayer(player);

        // Change password GUI — prevent closing by Escape
        if (AuthGUITracker.isChangePasswordPlayer(uuid)) {
            AuthGUITracker.cancelResetTask(uuid);
            player.sendMessage(MessageUtil.parse("<red>❌ Вы не можете закрыть окно смены пароля!</red>"));
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    if (!AuthGUITracker.isChangePasswordPlayer(uuid)) return;
                    AuthGUI.openChangePassword(player);
                }
            }.runTaskLater(Main.getInstance(), 5L);
            return;
        }

        // Logout GUI — just clean up
        if (AuthGUITracker.isLogoutPlayer(uuid)) {
            AuthGUITracker.cancelResetTask(uuid);
            AuthGUITracker.removeLogoutPlayer(uuid);
            player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <gray>Выход из аккаунта отменён.</gray>"));
            return;
        }

        // Login/Register GUI — re-open
        if (!needsAuth(player)) return;

        AuthGUITracker.cancelResetTask(uuid);
        player.sendMessage(MessageUtil.parse("<red>❌ Вы не можете закрыть окно авторизации! Пожалуйста, введите пароль.</red>"));
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

        UUID uuid = player.getUniqueId();

        // Allow if we are currently opening an auth GUI
        if (AuthGUITracker.isOpeningAuthPlayer(uuid)) return;

        // Allow change password GUI
        if (AuthGUITracker.isChangePasswordPlayer(uuid)) {
            if (isAuthGUI(event)) return;
            event.setCancelled(true);
            return;
        }

        // Allow logout GUI for authenticated players
        if (AuthGUITracker.isLogoutPlayer(uuid)) {
            if (isAuthGUI(event)) return;
            event.setCancelled(true);
            return;
        }

        if (!needsAuth(player)) return;

        if (isAuthGUI(event)) return;
        event.setCancelled(true);
    }
}
