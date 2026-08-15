package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.command.AskCordsManager;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

/**
 * Event listener for the authentication system (chat-based).
 * <p>
 * Blocks actions of unauthenticated players. GUI handlers removed —
 * all interaction now happens through chat commands:
 * {@code /ui auth login/register/logout/chgpass/2fa}.
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
                        "§6✦ UltimateImprovments\n" +
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
        // Restore pre-freeze state and clear savedStates to prevent stuck-frozen on
        // reconnect (see AuthAuthenticator.handleQuit javadoc for the full bug chain).
        AuthAuthenticator auth = AuthAuthenticator.getInstance();
        if (auth != null) {
            auth.handleQuit(event.getPlayer());
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
    // Allow only /ui auth (login/register/logout/chgpass/2fa) before login.
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();

        // Allow /ui auth login, register, chgpass, logout and 2fa
        if (msg.startsWith("/ui auth login") || msg.startsWith("/ui auth register")
                || msg.startsWith("/ui auth logout")
                || msg.startsWith("/ui auth chgpass")
                || msg.startsWith("/ui auth 2fa")) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("");
        player.sendMessage("§c❌ §fПожалуйста, авторизуйтесь!");
        player.sendMessage("§e/ui auth login <password> §7| §e/ui auth register <password>");
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
    // ⚠ DEPRECATED GUI HANDLERS REMOVED:
    // The actual GUI auth logic is gone (chat-based auth).
    // Players requiring auth are frozen, and ALL actions are blocked for them.
    // =========================
}
