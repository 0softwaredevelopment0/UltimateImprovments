package com.mcplugin.mechanics.security.auth;

import com.mcplugin.core.Main;
import com.mcplugin.command.AskCordsManager;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

/**
 * Слушатель событий для системы авторизации (chat-based).
 * <p>
 * Блокирует действия неавторизованных игроков. GUI-handlers удалены —
 * все взаимодействие теперь происходит через чат-команды:
 * {@code /mp auth login/register/logout/chgpass/2fa}.
 */
public class AuthListener implements Listener {

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
        @SuppressWarnings("unused")
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
    // JOIN → start chat-based auth flow
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
    // Разрешаем только /mp auth (login/register/logout/chgpass/2fa) до входа.
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();

        // Разрешаем /mp auth login, register, chgpass, logout и 2fa
        if (msg.startsWith("/mp auth login") || msg.startsWith("/mp auth register")
                || msg.startsWith("/mp auth logout")
                || msg.startsWith("/mp auth chgpass")
                || msg.startsWith("/mp auth 2fa")) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("");
        player.sendMessage("§c❌ §fПожалуйста, авторизуйтесь!");
        player.sendMessage("§e/mp auth login <password> §7| §e/mp auth register <password>");
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
    // ⚠ DEPRECATED GUI HANDLERS УДАЛЕНЫ:
    // Реальная GUI-логика авторизации удалена (chat-based auth).
    // Игроки, требующие авторизации, заморожены, и им заблокированы ВСЕ действия.
    // =========================
}
