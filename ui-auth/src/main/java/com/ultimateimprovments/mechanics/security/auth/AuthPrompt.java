package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthPrompt — keeps the authentication screen in front of a player who is not
 * authorized yet.
 * <p>
 * Why this exists: the auth dialog is a Minecraft Custom Screen. The client
 * closes it on its own ({@link AuthDialogScreen} uses {@code DialogAction.CLOSE}
 * so there is no «Waiting for server» screen), and it <b>never tells the server</b>
 * about it. So whenever the dialog closes without a follow-up open — a wrong
 * password during the rate-limit lock, a submit while the account is locked,
 * a client that dropped the screen — the player ends up frozen with every action
 * blocked and no window to type the password in. The only way out used to be
 * a reconnect, and nothing told the player that.
 * <p>
 * This class closes that hole:
 * <ul>
 *   <li>{@link #onBlockedAction(Player)} — called from {@link AuthListener} for every
 *       action that was cancelled because the player is not authorized: it re-opens
 *       the screen (throttled) and sends a prefixed hint about reconnecting when the
 *       window does not appear;</li>
 *   <li>{@link #openLater(Player, long)} — re-opens the screen after a delay, used for
 *       error paths (wrong password, rate-limit lock expiration);</li>
 *   <li>{@link #sendJoinHint(Player)} — the prefixed hint shown right after joining.</li>
 * </ul>
 * Detection of «is the screen on the client's display»: a dialog opened with
 * {@code pause=true} swallows player input, so a cancelled action proves the screen
 * is <b>not</b> shown. While the screen is believed to be open the re-open is delayed
 * ({@link #OPEN_SCREEN_FALLBACK_MS}) instead of firing immediately, so a failed
 * render still gets recovered without ever wiping a password being typed.
 */
public final class AuthPrompt {

    /** Re-open gap when the dialog is known to be closed (an action got through). */
    private static final long CLOSED_SCREEN_REOPEN_MS = 1500L;

    /**
     * Fallback gap while the dialog is believed to be open. A cancelled action while
     * the screen is open means the client never rendered it, so after this delay the
     * screen is re-sent anyway.
     */
    private static final long OPEN_SCREEN_FALLBACK_MS = 8000L;

    /** Gap between two «the window is missing, reconnect» hints. */
    private static final long HINT_THROTTLE_MS = 15000L;

    /** Per-player prompt state. */
    private static final class PromptState {
        long openedAt;
        boolean dialogOpen;
        long lastHintAt;
    }

    private static final Map<UUID, PromptState> states = new ConcurrentHashMap<>();

    private AuthPrompt() {}

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /** Marks that the auth screen has just been sent to the client. */
    public static void markOpened(Player player) {
        if (player == null) return;
        PromptState state = state(player.getUniqueId());
        state.openedAt = System.currentTimeMillis();
        state.dialogOpen = true;
    }

    /**
     * Marks that the client closed the auth screen (submit / exit button).
     * Called from {@link AuthDialogHandler} the moment a custom click arrives.
     */
    public static void markClosed(Player player) {
        if (player == null) return;
        PromptState state = states.get(player.getUniqueId());
        if (state != null) state.dialogOpen = false;
    }

    /**
     * Called for every action cancelled because the player is not authorized.
     * Re-opens the auth screen (throttled) and reminds the player that a reconnect
     * is the way out when no window shows up.
     *
     * @param player the still-unauthenticated player that tried to do something
     */
    public static void onBlockedAction(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!AuthConfig.isEnabled()) return;
        if (!needsPrompt(player)) return;
        // A GitHub 2FA challenge in flight has its own instructions — do not cover
        // them with the password screen.
        if (isWaitingFor2FA(player)) return;

        PromptState state = states.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        long gap = (state != null && state.dialogOpen) ? OPEN_SCREEN_FALLBACK_MS : CLOSED_SCREEN_REOPEN_MS;
        if (state != null && now - state.openedAt < gap) {
            sendMissingHint(player);
            return;
        }

        openNow(player);
        sendMissingHint(player);
    }

    /** Opens the auth screen immediately (registration state is read from the database). */
    public static void openNow(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!needsPrompt(player)) return;
        AuthDialogScreen.open(player, AuthDatabase.isRegistered(player.getUniqueId()));
    }

    /**
     * Opens the auth screen after {@code delayMs} milliseconds — used by error paths
     * where the client has just closed the dialog and needs a moment before the
     * replacement screen arrives.
     */
    public static void openLater(Player player, long delayMs) {
        if (player == null) return;
        long ticks = Math.max(1L, delayMs / 50L);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> openNow(player), ticks);
    }

    /**
     * Prefixed hint shown right after joining: where to type the password and what
     * to do when the window is missing.
     */
    public static void sendJoinHint(Player player) {
        if (player == null || !player.isOnline()) return;
        player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX + MessagesManager.getString(
                "auth.messages.dialog_join_hint",
                "<gray>Enter your password in the authorization window that has just opened.</gray>\n"
                        + "<gray>If no window appeared, reconnect to the server.</gray>"))); 
    }

    /**
     * Prefixed hint for a player that keeps being blocked: the window is not open,
     * the way out is a reconnect. Throttled so a moving player is not spammed.
     */
    public static void sendMissingHint(Player player) {
        if (player == null || !player.isOnline()) return;
        PromptState state = state(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (now - state.lastHintAt < HINT_THROTTLE_MS) return;
        state.lastHintAt = now;

        player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX + MessagesManager.getString(
                "auth.messages.dialog_missing_hint",
                "<red>❌ Action blocked — you are not authorized yet.</red>\n"
                        + "<gray>If the authorization window did not open, reconnect to the server.</gray>")));
    }

    /** Drops all prompt bookkeeping for a player (quit / login success). */
    public static void removePlayer(UUID uuid) {
        if (uuid == null) return;
        states.remove(uuid);
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    /** The player must still authenticate and the system must be able to serve them. */
    private static boolean needsPrompt(Player player) {
        AuthPlayerState playerState = AuthPlayerState.getInstance();
        if (playerState == null) return false;
        UUID uuid = player.getUniqueId();
        if (playerState.isAuthenticated(uuid)) return false;
        // Without a ready database the auth flow never starts — no screen to show.
        return AuthDatabase.isTableReady();
    }

    private static boolean isWaitingFor2FA(Player player) {
        AuthManager manager = AuthManager.getInstance();
        if (manager == null) return false;
        try {
            return manager.hasPending2FA(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PromptState state(UUID uuid) {
        return states.computeIfAbsent(uuid, key -> new PromptState());
    }
}
