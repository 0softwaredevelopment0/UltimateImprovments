package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * AuthDialogs — Custom Screens of {@code /ui auth} besides the login window:
 * <ul>
 *   <li>{@link #openLogoutConfirm(Player)} — confirmation before the session reset;</li>
 *   <li>{@link #openChangePassword(Player)} — old + new password (self-service);</li>
 *   <li>{@link #openAdminChangePassword(Player)} — player nickname + new password
 *       (admin recovery; the console keeps plain command arguments);</li>
 *   <li>{@link #openGithub2FA(Player, String, String)} — GitHub 2FA challenge with a
 *       button that opens the authorization link (no chat commands involved).</li>
 * </ul>
 * <p>
 * Every visible string comes from the composite config through
 * {@link MessagesManager}, i.e. it respects {@code messages.lang} and falls back
 * between the Russian ({@code messages.auth.dialog.*}) and English
 * ({@code messages_en.auth.dialog.*}) sections of {@code UI-Auth.toml}.
 * <p>
 * Same conventions as {@code KitDialogs}: {@link DialogAction#CLOSE} instead of
 * {@code WAIT_FOR_RESPONSE} (otherwise the client shows the “Waiting for server…”
 * screen and ignores the clear-dialog packet), one action identifier per button,
 * inputs read back through {@code DialogResponseView}. Passwords exist only inside
 * the dialog fields — never in chat and never in command arguments.
 */
public final class AuthDialogs {

    private static final String NS = "ultimateimprovments";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Logout confirmation → confirm (reset the session and kick). */
    public static final Identifier LOGOUT_CONFIRM = id("auth_logout_confirm");
    /** Logout confirmation → cancel. */
    public static final Identifier LOGOUT_CANCEL = id("auth_logout_cancel");
    /** Self password change → submit (old + new password). */
    public static final Identifier CHANGEPASS_SUBMIT = id("auth_changepass_submit");
    /** Self password change → cancel. */
    public static final Identifier CHANGEPASS_CANCEL = id("auth_changepass_cancel");
    /** Admin password reset → submit (player nickname + new password). */
    public static final Identifier CHGPASS_SUBMIT = id("auth_chgpass_submit");
    /** Admin password reset → cancel. */
    public static final Identifier CHGPASS_CANCEL = id("auth_chgpass_cancel");
    /** GitHub 2FA challenge → cancel (drop the pending challenge, back to the password window). */
    public static final Identifier TWOFA_CANCEL = id("auth_2fa_cancel");

    /** Input keys read back through {@code DialogResponseView}. */
    public static final String KEY_OLD = "auth_old";
    public static final String KEY_NEW = "auth_new";
    public static final String KEY_TARGET = "auth_target";

    private AuthDialogs() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    /**
     * Compares a click action id reported by {@code PlayerCustomClickEvent} with a dialog
     * action id.
     * <p>
     * ⚠ The event reports an Adventure {@link Key}, while the dialog API takes a
     * {@code net.minecraft.resources.Identifier}. Those two types are unrelated
     * ({@code Identifier} does not implement {@link Key}), so {@code key.equals(identifier)}
     * is <b>always false</b> — matching through this helper is the only reliable way, and
     * forgetting it silently disables the button (it clicks, nothing happens).
     */
    public static boolean matches(Key reported, Identifier actionId) {
        return reported != null && actionId != null
                && reported.namespace().equals(actionId.getNamespace())
                && reported.value().equals(actionId.getPath());
    }

    // =========================================================================
    // PUBLIC OPENERS
    // =========================================================================

    /** Logout confirmation: no inputs, just Confirm / Cancel. */
    public static void openLogoutConfirm(Player player) {
        open(player, "logout",
                cfg("logout_title", "<gold>✦ Account logout</gold>"),
                cfg("logout.body",
                        "<white>Reset the session and log out of the account?</white>\n"
                                + "<gray>The next login will ask for the password again.</gray>"),
                List.of(),
                cfg("logout_confirm_button", "<green>✔ Log out</green>"),
                cfg("cancel_button", "<red>❌ Cancel</red>"),
                LOGOUT_CONFIRM, LOGOUT_CANCEL);
    }

    /** Self password change: old password + new password. */
    public static void openChangePassword(Player player) {
        open(player, "changepassword",
                cfg("title", "<gold>✦ Authentication</gold>"),
                cfg("changepassword.body",
                        "<white>Enter your current password and the new password:</white>"),
                List.of(
                        input(KEY_OLD, cfg("old_password_label", "<gray>Current password</gray>"), maxPassword()),
                        input(KEY_NEW, cfg("new_password_label", "<gray>New password</gray>"), maxPassword())),
                cfg("continue_button", "<green>✔ Continue</green>"),
                cfg("cancel_button", "<red>❌ Cancel</red>"),
                CHANGEPASS_SUBMIT, CHANGEPASS_CANCEL);
    }

    /** Admin password reset: player nickname + new password (no old password needed). */
    public static void openAdminChangePassword(Player player) {
        open(player, "chgpass",
                cfg("title", "<gold>✦ Authentication</gold>"),
                cfg("chgpass.body",
                        "<white>Enter the player nickname and the new password:</white>"),
                List.of(
                        input(KEY_TARGET, cfg("target_label", "<gray>Player nickname</gray>"), 16),
                        input(KEY_NEW, cfg("new_password_label", "<gray>New password</gray>"), maxPassword())),
                cfg("continue_button", "<green>✔ Continue</green>"),
                cfg("cancel_button", "<red>❌ Cancel</red>"),
                CHGPASS_SUBMIT, CHGPASS_CANCEL);
    }

    /**
     * GitHub 2FA challenge: explains what is going on, offers a button that opens the
     * authorization page in the browser and a cancel button. The player stays frozen
     * until the callback / polling confirms the authorization.
     *
     * @param player         the player awaiting the challenge
     * @param githubUsername the linked GitHub account (may be null)
     * @param authUrl        the one-time GitHub authorization URL
     */
    public static void openGithub2FA(Player player, String githubUsername, String authUrl) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AuthDialog] Cannot open the 2FA dialog for a non-CraftPlayer: " + player.getName());
            return;
        }

        String account = (githubUsername == null || githubUsername.isBlank()) ? "—" : githubUsername;
        String body = cfg("twofa.body",
                "<white>Confirm the login through GitHub.</white>\n"
                        + "<gray>Account: <white>%account%</white>. The link is valid for 5 minutes.</gray>")
                .replace("%account%", account)
                .replace("%url%", authUrl == null ? "" : authUrl);

        ActionButton openBtn = new ActionButton(
                new CommonButtonData(nativeText(cfg("twofa_open_button", "<green>✔ Open GitHub</green>")), 200),
                Optional.of(openUrlAction(authUrl)));
        ActionButton cancelBtn = new ActionButton(
                new CommonButtonData(nativeText(cfg("twofa_cancel_button", "<red>❌ Cancel</red>")), 150),
                Optional.of(new CustomAll(TWOFA_CANCEL, Optional.empty())));

        CommonDialogData data = new CommonDialogData(
                nativeText(cfg("twofa_title", "<gold>✦ Two-Factor Authentication</gold>")),
                Optional.of(nativeText(cfg("external_title", "<gray>Server Authentication</gray>"))),
                true,               // canCloseWithEscape — closing keeps the challenge pending
                true,               // pause
                DialogAction.CLOSE,
                List.of(new PlainMessage(nativeText(body), 310)),
                List.of());

        MultiActionDialog dialog = new MultiActionDialog(data, List.of(openBtn), Optional.of(cancelBtn), 1);
        craftPlayer.getHandle().openDialog(Holder.direct(dialog));
        AuthPrompt.markOpened(player);
        ConsoleLogger.info("[AuthDialog] Opened 2FA challenge dialog for " + player.getName());
    }

    // =========================================================================
    // BUILDER
    // =========================================================================

    private static void open(Player player, String dialogKey, String titleMiniMessage, String bodyMiniMessage,
                             List<Input> inputs, String confirmMiniMessage, String cancelMiniMessage,
                             Identifier submitId, Identifier cancelId) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AuthDialog] Cannot open the dialog for a non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        List<ActionButton> main = List.of(new ActionButton(
                new CommonButtonData(nativeText(confirmMiniMessage), 150),
                Optional.of(new CustomAll(submitId, Optional.empty()))));
        Optional<ActionButton> exit = Optional.of(new ActionButton(
                new CommonButtonData(nativeText(cancelMiniMessage), 150),
                Optional.of(new CustomAll(cancelId, Optional.empty()))));

        CommonDialogData data = new CommonDialogData(
                nativeText(titleMiniMessage),
                Optional.of(nativeText(cfg("external_title", "<gray>Server Authentication</gray>"))),
                true,                 // canCloseWithEscape
                true,                 // pause
                DialogAction.CLOSE,   // the client closes the window itself, instantly (no "Waiting for server")
                List.of(new PlainMessage(nativeText(bodyMiniMessage), 310)),
                inputs);

        MultiActionDialog dialog = new MultiActionDialog(data, main, exit, 1);

        serverPlayer.openDialog(Holder.direct(dialog));
        AuthPrompt.markOpened(player);
        ConsoleLogger.info("[AuthDialog] Opened '" + dialogKey + "' dialog for " + player.getName());
    }

    private static Input input(String key, String labelMiniMessage, int maxLength) {
        TextInput field = new TextInput(200, nativeText(labelMiniMessage), false, "", maxLength, Optional.empty());
        return new Input(key, field);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Static open_url action; falls back to a "do nothing" custom action when the URL is broken. */
    private static net.minecraft.server.dialog.action.Action openUrlAction(String authUrl) {
        try {
            if (authUrl != null && !authUrl.isBlank()) {
                return new StaticAction(new ClickEvent.OpenUrl(URI.create(authUrl)));
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[AuthDialog] Invalid 2FA URL, button disabled: " + e.getMessage());
        }
        return new CustomAll(TWOFA_CANCEL, Optional.empty());
    }

    private static int maxPassword() {
        return Math.max(1, AuthConfig.getMaxPasswordLength());
    }

    /**
     * Dialog text: current language first, the other language second, then the
     * built-in default (see {@link MessagesManager#getString(String, String)}).
     */
    private static String cfg(String path, String def) {
        return MessagesManager.getString("auth.dialog." + path, def);
    }

    /** Adventure Component → Minecraft Component through §-codes (project convention). */
    private static net.minecraft.network.chat.Component nativeText(String miniMessage) {
        return nativeText(MM.deserialize(miniMessage));
    }

    private static net.minecraft.network.chat.Component nativeText(Component adventure) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(adventure);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
