package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * AuthDialogHandler — listens for {@link PlayerCustomClickEvent} and handles
 * password form submission or authentication cancellation from the Custom Screen.
 * <p>
 * Two modes:
 * <ul>
 *   <li>{@code ultimateimprovments:auth_submit} — submit the password (Continue)</li>
 *   <li>{@code ultimateimprovments:auth_cancel} — cancel authentication (Exit → kick)</li>
 * </ul>
 */
public class AuthDialogHandler implements Listener {

    private static final Key AUTH_SUBMIT_KEY = Key.key("ultimateimprovments", "auth_submit");
    private static final Key AUTH_CANCEL_KEY = Key.key("ultimateimprovments", "auth_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        // Get the player through PlayerGameConnection
        Player player = getPlayerFromConnection(event);
        if (player == null) return;
        if (identifier == null) return;

        // The client closes a dialog the moment any button is pressed and never tells
        // the server — remember it, otherwise AuthPrompt would assume the auth screen
        // is still on the player's display.
        if (identifier.equals(AUTH_SUBMIT_KEY) || identifier.equals(AUTH_CANCEL_KEY)) {
            AuthPrompt.markClosed(player);
        }

        // ─── GitHub 2FA challenge: cancel → back to the password window ───
        if (AuthDialogs.matches(identifier, AuthDialogs.TWOFA_CANCEL)) {
            Auth2FA twofa = Auth2FA.getInstance();
            if (twofa != null) twofa.clearPending(player.getUniqueId());
            sendCancelled(player);
            AuthPrompt.openLater(player, 500L);
            return;
        }

        // ─── Logout confirmation ───
        if (AuthDialogs.matches(identifier, AuthDialogs.LOGOUT_CONFIRM)) {
            AuthManager manager = AuthManager.getInstance();
            if (manager != null) manager.logout(player);
            return;
        }
        if (AuthDialogs.matches(identifier, AuthDialogs.LOGOUT_CANCEL)) {
            sendCancelled(player);
            return;
        }

        // ─── Self password change (old + new password) ───
        if (AuthDialogs.matches(identifier, AuthDialogs.CHANGEPASS_SUBMIT)) {
            DialogResponseView response = event.getDialogResponseView();
            if (response == null) return;
            String oldPassword = response.getText(AuthDialogs.KEY_OLD);
            String newPassword = response.getText(AuthDialogs.KEY_NEW);
            if (oldPassword == null || oldPassword.isEmpty() || newPassword == null || newPassword.isEmpty()) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                        "auth.messages.changepass_fields_required",
                        "<red>❌ Old and new password are required.</red>")));
                return;
            }
            AuthAuthenticator authenticator = AuthAuthenticator.getInstance();
            if (authenticator != null) {
                authenticator.handleSelfChangePassword(player, oldPassword, newPassword);
            }
            return;
        }
        if (AuthDialogs.matches(identifier, AuthDialogs.CHANGEPASS_CANCEL)) {
            sendCancelled(player);
            return;
        }

        // ─── Admin password reset (nickname + new password) ───
        if (AuthDialogs.matches(identifier, AuthDialogs.CHGPASS_SUBMIT)) {
            if (!player.hasPermission("ui.command.auth.chgpass")) {
                CommandErrors.noPermission(player, "ui.command.auth.chgpass");
                return;
            }
            DialogResponseView response = event.getDialogResponseView();
            if (response == null) return;
            changePasswordFor(player, response.getText(AuthDialogs.KEY_TARGET),
                    response.getText(AuthDialogs.KEY_NEW));
            return;
        }
        if (AuthDialogs.matches(identifier, AuthDialogs.CHGPASS_CANCEL)) {
            sendCancelled(player);
            return;
        }

        // ─── Cancel (Exit Server) ───
        if (identifier.equals(AUTH_CANCEL_KEY)) {
            String kickMsg = MessagesManager.getString("auth.dialog.kick_cancelled",
                    "<red>❌ Authentication cancelled.\n<gray>You cancelled the authentication process.</red>");
            // Small delay so the client has time to close the dialog
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    player.kick(MessageUtil.parse(kickMsg));
                }
            }, 5L);
            ConsoleLogger.info("[AuthDialog] Player " + player.getName() + " cancelled authentication (Exit button).");
            return;
        }

        // ─── Submit (Continue) ───
        if (!identifier.equals(AUTH_SUBMIT_KEY)) return;

        // Extract the password from DialogResponseView (Paper API)
        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[AuthDialog] No dialog response view in auth submit from " + player.getName());
            showErrorAndReopen(player);
            return;
        }

        String password = response.getText("password");
        if (password == null || password.isEmpty()) {
            showErrorAndReopen(player);
            return;
        }

        ConsoleLogger.info("[AuthDialog] Password submitted by " + player.getName()
            + " (len=" + password.length() + ")");

        // Delegate to the existing authentication system
        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            manager.handlePasswordSubmit(player, password);
        }
    }

    /**
     * Applies the admin password reset submitted through the {@code auth_chgpass_submit} dialog.
     */
    @SuppressWarnings("deprecation")
    private static void changePasswordFor(Player actor, String targetName, String newPassword) {
        if (targetName == null || targetName.isBlank() || newPassword == null || newPassword.isEmpty()) {
            actor.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.admin.chgpass_dialog_fields",
                    "<red>❌ Player nickname and new password are required.</red>")));
            return;
        }
        String policyError = PasswordPolicy.validate(newPassword);
        if (policyError != null) {
            actor.sendMessage(MessageUtil.parse(policyError));
            return;
        }
        UUID targetUuid = org.bukkit.Bukkit.getOfflinePlayer(targetName.trim()).getUniqueId();
        if (!AuthDatabase.isRegistered(targetUuid)) {
            actor.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.admin.chgpass_dialog_not_registered",
                    "<red>❌ Player </red><yellow>%player%</yellow><red> is not registered!</red>")
                    .replace("%player%", targetName.trim())));
            return;
        }
        AuthManager manager = AuthManager.getInstance();
        if (manager == null) {
            actor.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "auth.admin.chgpass_dialog_not_initialized",
                    "<red>❌ Authorization system is not initialized!</red>")));
            return;
        }
        boolean changed = manager.changePassword(targetUuid, newPassword);
        actor.sendMessage(changed
                ? MessageUtil.parse(MessagesManager.getString(
                        "auth.admin.chgpass_dialog_success",
                        "<green>✔</green> <white>Password of player </white><yellow>%player%</yellow><white> changed.</white>")
                        .replace("%player%", targetName.trim()))
                : MessageUtil.parse(MessagesManager.getString(
                        "auth.admin.chgpass_dialog_fail",
                        "<dark_red>❌</dark_red> <red>Failed to change the password (check the length).</red>")));
        ConsoleLogger.info("[Auth] Password reset by " + actor.getName() + " for " + targetName.trim()
                + " (" + targetUuid + "), success=" + changed);
    }

    /**
     * Gets the Bukkit Player from PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[AuthDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Sends the shared "action cancelled" notice — config key {@code auth.messages.cancelled}.
     */
    private static void sendCancelled(Player player) {
        player.sendMessage(MessageUtil.parse(MessagesManager.getString("auth.messages.cancelled",
                "<gray>Cancelled.</gray>")));
    }

    /**
     * Shows an error and re-opens the dialog.
     */
    private void showErrorAndReopen(Player player) {
        AuthPrompt.openLater(player, 500L);
    }



    /**
     * Registers the listener in the plugin.
     */
    public static void register(org.bukkit.plugin.Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new AuthDialogHandler(), plugin);
        ConsoleLogger.info("[AuthDialog] AuthDialogHandler registered");
    }
}
