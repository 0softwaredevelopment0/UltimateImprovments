package com.mcplugin.mechanics.security.auth;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.commands.AskCordsManager;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
                        "<yellow>❌ A player with this name is already on the server!</yellow>\n<white>Please join with a different name.</white>");
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
        AskCordsManager.cleanup(uuid);
    }

    // =========================
    // CHECK IF PLAYER NEEDS AUTH
    // =========================
    private boolean needsAuth(Player player) {
        return AuthPlayerState.getInstance() != null && AuthPlayerState.getInstance().needsAuth(player);
    }

    // =========================
    // BLOCK DAMAGE TO ENTITIES if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!needsAuth(player)) return;
        event.setCancelled(true);
    }

    // =========================
    // BLOCK BUCKET USE if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        event.setCancelled(true);
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

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();

        // Разрешаем /mp auth login и /mp auth register в замороженном состоянии
        if (msg.startsWith("/mp auth login") || msg.startsWith("/mp auth register")) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("");
        player.sendMessage("§c❌ §fПожалуйста, авторизуйтесь!");
        player.sendMessage("§e/mp auth login §7<§opassword§7> §f| §e/mp auth register §7<§opassword§7>");
        player.sendMessage("");
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
    // 🛡 БЛОКИРОВКА ПЕРЕТАСКИВАНИЯ ПРЕДМЕТОВ В ANVIL GUI
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getMenuType() != MenuType.ANVIL) return;

        UUID uuid = player.getUniqueId();

        // 🛡 Только для наших auth-GUI, не для ванильной наковальни
        if (!AuthGUITracker.isChangePasswordPlayer(uuid) &&
            !AuthGUITracker.isLogoutPlayer(uuid) &&
            !needsAuth(player)) return;

        // Блокируем drag в любые слоты anvil (raw slots 0-2)
        for (int slot : event.getRawSlots()) {
            if (slot < 3) {
                event.setCancelled(true);
                AuthGUITracker.antiDupCleanup(player);
                return;
            }
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

        // 🛡 Если игрок не в auth-состоянии — не трогаем!
        // isAuthGUI() проверяет только MenuType.ANVIL, но ванильная наковальня
        // тоже имеет MenuType.ANVIL. Без этой проверки мы будем чистить курсор
        // и в обычной наковальне.
        if (!AuthGUITracker.isChangePasswordPlayer(uuid) &&
            !AuthGUITracker.isLogoutPlayer(uuid) &&
            !needsAuth(player)) return;

        // ⚡ Очищаем курсор ДО обработки — Paper может положить предмет на курсор
        // ДО того, как событие будет отменено (особенно для result slot anvil)
        player.setItemOnCursor(null);

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
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.enter_password_field", "<red>❌ Enter your password in the item name field!</red>")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        AuthManager manager = AuthManager.getInstance();
        if (manager == null) return;

        if (manager.handleLogout(player, password)) {
            // handleLogout already kicks the player on success
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.wrong_password", "<red>❌ Incorrect password! Try again.</red>")));

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
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.not_registered", "<red>❌ You are not registered yet! First, create a password.</red>")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        String currentPassword = AuthGUIAnvilReader.getAnvilRenameText(player);

        if (currentPassword == null || currentPassword.isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.enter_current_password", "<red>❌ Enter your current password in the item name field!</red>")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        // Verify current password
        if (!AuthDatabase.checkPassword(uuid, currentPassword)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.wrong_password_short", "<red>❌ Incorrect password!</red>")));
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
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_change_cancelled", "<yellow>✦</yellow> <gray>Password change cancelled.</gray>")));

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
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.enter_new_password_field", "<red>❌ Enter a new password in the item name field!</red>")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();
        if (newPassword.length() < minLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_short", "<red>❌ Password must be at least </red><yellow>{min}</yellow><red> characters!</red>").replace("{min}", String.valueOf(minLen))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }
        if (newPassword.length() > maxLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_long", "<red>❌ Password must not exceed </red><yellow>{max}</yellow><red> characters!</red>").replace("{max}", String.valueOf(maxLen))));
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
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.enter_password_field_generic", "<red>❌ Enter your password in the item name field!</red>")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }

        int minLen = AuthConfig.getMinPasswordLength();
        int maxLen = AuthConfig.getMaxPasswordLength();
        if (password.length() < minLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_short", "<red>❌ Password must be at least </red><yellow>{min}</yellow><red> characters!</red>").replace("{min}", String.valueOf(minLen))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return;
        }
        if (password.length() > maxLen) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.password_too_long", "<red>❌ Password must not exceed </red><yellow>{max}</yellow><red> characters!</red>").replace("{max}", String.valueOf(maxLen))));
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

        // 🛡 Не трогаем закрытие ванильной наковальни
        if (!AuthGUITracker.isChangePasswordPlayer(uuid) &&
            !AuthGUITracker.isLogoutPlayer(uuid) &&
            !needsAuth(player)) return;

        // Skip if transitioning between GUIs
        if (AuthGUITracker.isTransitioning(uuid)) {
            return;
        }

        // Anti-dup
        AuthGUITracker.removeAuthItemsFromPlayer(player);

        // Change password GUI — prevent closing by Escape
        if (AuthGUITracker.isChangePasswordPlayer(uuid)) {
            AuthGUITracker.cancelResetTask(uuid);
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.cannot_close_change_password", "<red>❌ You cannot close the password change window!</red>")));
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
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.logout_cancelled", "<yellow>✦</yellow> <gray>Account logout cancelled.</gray>")));
            return;
        }

        // Login/Register GUI — re-open
        if (!needsAuth(player)) return;

        AuthGUITracker.cancelResetTask(uuid);
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.cannot_close_auth", "<red>❌ You cannot close the authorization window! Please enter your password.</red>")));
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
