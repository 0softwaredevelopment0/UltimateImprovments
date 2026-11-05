package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
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
 * Event listener for the authentication system.
 * <p>
 * Blocks actions of unauthenticated players. Login and registration happen
 * exclusively through the auth dialog (Custom Screen) with a password field;
 * before login only {@code /ui auth 2fa} and {@code /ui auth logout} are allowed.
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
                        MessageUtil.parse("<gold>✦ UltimateImprovments\n" +
                        "<gray>━━━━━━━━━━━━━━━━━━━━━\n\n") +
                        dupParsed + "\n\n" +
                        MessageUtil.legacy("<gray>━━━━━━━━━━━━━━━━━━━━━")
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
        // Drop the dialog-prompt bookkeeping (throttle timestamps) for a clean rejoin.
        AuthPrompt.removePlayer(uuid);
    }

    // =========================
    // CHECK IF PLAYER NEEDS AUTH
    // =========================
    private boolean needsAuth(Player player) {
        return AuthPlayerState.getInstance() != null && AuthPlayerState.getInstance().needsAuth(player);
    }

    /**
     * Blocks an action of a not-yet-authorized player and re-sends the auth screen.
     * The client owns the dialog: it closes it on submit and never reports the close,
     * so without this the player can end up frozen with no window and no way to log in.
     */
    private void blockAndPrompt(org.bukkit.event.Cancellable event, Player player) {
        event.setCancelled(true);
        AuthPrompt.onBlockedAction(player);
    }

    // =========================
    // BLOCK DAMAGE TO ENTITIES if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
    }

    // =========================
    // BLOCK BUCKET USE if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
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
            blockAndPrompt(event, player);
        }
    }

    // =========================
    // BLOCK INTERACT if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
    }

    // =========================
    // BLOCK BREAK if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
    }

    // =========================
    // BLOCK PLACE if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
    }

    // =========================
    // BLOCK CHAT / COMMANDS if not authed
    // Before login only /ui auth 2fa and /ui auth logout are allowed: the password
    // itself is entered in the auth dialog (Custom Screen), never in chat.
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;

        String msg = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();

        // Only /ui auth 2fa and /ui auth logout are useful before login
        if (msg.startsWith("/ui auth 2fa") || msg.startsWith("/ui auth logout")) {
            return;
        }

        event.setCancelled(true);
        // Not authorized → re-send the auth screen (throttled) and remind the player
        // that a reconnect is the way out when the window never appears.
        AuthPrompt.onBlockedAction(player);
    }

    // =========================
    // BLOCK ITEM DROP if not authed
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!needsAuth(player)) return;
        blockAndPrompt(event, player);
    }

    // =========================
    // ⚠ DEPRECATED GUI HANDLERS REMOVED:
    // The actual GUI auth logic is gone (chat-based auth).
    // Players requiring auth are frozen, and ALL actions are blocked for them.
    // =========================
}
