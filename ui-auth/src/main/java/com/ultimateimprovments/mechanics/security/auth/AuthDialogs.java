package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * AuthDialogs — Custom Screens of {@code /ui auth} besides the login window:
 * <ul>
 *   <li>{@link #openLogoutConfirm(Player)} — confirmation before the session reset;</li>
 *   <li>{@link #openChangePassword(Player)} — old + new password (self-service);</li>
 *   <li>{@link #openAdminChangePassword(Player)} — player nickname + new password
 *       (admin recovery; the console keeps plain command arguments).</li>
 * </ul>
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

    /** Input keys read back through {@code DialogResponseView}. */
    public static final String KEY_OLD = "auth_old";
    public static final String KEY_NEW = "auth_new";
    public static final String KEY_TARGET = "auth_target";

    private AuthDialogs() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    // =========================================================================
    // PUBLIC OPENERS
    // =========================================================================

    /** Logout confirmation: no inputs, just Confirm / Cancel. */
    public static void openLogoutConfirm(Player player) {
        open(player, "logout",
                "<white>Сбросить сессию и выйти из аккаунта?</white>\n"
                        + "<gray>Следующий вход снова потребует пароль.</gray>",
                List.of(),
                cfg("messages.auth.dialog.logout_confirm_button", "<green>✔ Выйти</green>"),
                cfg("messages.auth.dialog.cancel_button", "<red>❌ Отмена</red>"),
                LOGOUT_CONFIRM, LOGOUT_CANCEL);
    }

    /** Self password change: old password + new password. */
    public static void openChangePassword(Player player) {
        open(player, "changepassword",
                cfg("messages.auth.dialog.change_password_body",
                        "<white>Введите текущий пароль и новый пароль:</white>"),
                List.of(
                        input(KEY_OLD, cfg("messages.auth.dialog.old_password_label", "<gray>Текущий пароль</gray>"), maxPassword()),
                        input(KEY_NEW, cfg("messages.auth.dialog.new_password_label", "<gray>Новый пароль</gray>"), maxPassword())),
                cfg("messages.auth.dialog.continue_button", "<green>✔ Сменить пароль</green>"),
                cfg("messages.auth.dialog.cancel_button", "<red>❌ Отмена</red>"),
                CHANGEPASS_SUBMIT, CHANGEPASS_CANCEL);
    }

    /** Admin password reset: player nickname + new password (no old password needed). */
    public static void openAdminChangePassword(Player player) {
        open(player, "chgpass",
                cfg("messages.auth.dialog.admin_change_password_body",
                        "<white>Укажите ник игрока и новый пароль:</white>"),
                List.of(
                        input(KEY_TARGET, cfg("messages.auth.dialog.target_label", "<gray>Ник игрока</gray>"), 16),
                        input(KEY_NEW, cfg("messages.auth.dialog.new_password_label", "<gray>Новый пароль</gray>"), maxPassword())),
                cfg("messages.auth.dialog.continue_button", "<green>✔ Сменить пароль</green>"),
                cfg("messages.auth.dialog.cancel_button", "<red>❌ Отмена</red>"),
                CHGPASS_SUBMIT, CHGPASS_CANCEL);
    }

    // =========================================================================
    // BUILDER
    // =========================================================================

    private static void open(Player player, String dialogKey, String bodyDefault,
                             List<Input> inputs, String confirmMiniMessage, String cancelMiniMessage,
                             Identifier submitId, Identifier cancelId) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AuthDialog] Cannot open the dialog for a non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        var title = nativeText(cfg("messages.auth.dialog.title", "<gold>✦ Авторизация</gold>"));
        var externalTitle = nativeText(cfg("messages.auth.dialog.external_title", "<gray>Server Authentication</gray>"));
        var bodyText = nativeText(cfg("messages.auth.dialog." + dialogKey + ".body", bodyDefault));

        ActionButton confirmBtn = new ActionButton(
                new CommonButtonData(nativeText(confirmMiniMessage), 150),
                Optional.of(new CustomAll(submitId, Optional.empty())));
        ActionButton cancelBtn = new ActionButton(
                new CommonButtonData(nativeText(cancelMiniMessage), 150),
                Optional.of(new CustomAll(cancelId, Optional.empty())));

        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                 // canCloseWithEscape
                true,                 // pause
                DialogAction.CLOSE,   // the client closes the window itself, instantly (no "Waiting for server")
                List.of(new PlainMessage(bodyText, 310)),
                inputs);

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                List.of(confirmBtn),
                Optional.of(cancelBtn),
                1);

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[AuthDialog] Opened '" + dialogKey + "' dialog for " + player.getName());
    }

    private static Input input(String key, String labelMiniMessage, int maxLength) {
        TextInput field = new TextInput(200, nativeText(labelMiniMessage), false, "", maxLength, Optional.empty());
        return new Input(key, field);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static int maxPassword() {
        return Math.max(1, AuthConfig.getMaxPasswordLength());
    }

    private static String cfg(String key, String def) {
        String value = Main.getInstance().getConfig().getString(key, def);
        return value == null ? def : value;
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
