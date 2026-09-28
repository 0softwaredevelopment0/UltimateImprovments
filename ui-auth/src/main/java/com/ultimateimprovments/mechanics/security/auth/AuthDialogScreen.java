package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * AuthDialogScreen — creates and opens the Custom Screen (Dialog) for authentication.
 * <p>
 * Uses the Minecraft 26.2 Dialog API to show a screen with a password field
 * and «Continue» / «Exit» (kick) buttons.
 * <p>
 * The dialog uses {@link DialogAction#CLOSE} (NOT {@link DialogAction#WAIT_FOR_RESPONSE}):
 * with {@code WAIT_FOR_RESPONSE} the client moves to the «Waiting for server…» screen after
 * clicking and ignores {@code ClientboundClearDialogPacket}, so the window would linger ~4s.
 * With {@code CLOSE} the client closes the window immediately after clicking; the player
 * stays frozen (freezePlayer) until a successful login, and on error the dialog is
 * re-opened by the server.
 */
public class AuthDialogScreen {

    /** Identifier of the CustomAll action that submits the form (login/register). */
    public static final Identifier AUTH_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "auth_submit");
    /** Identifier of the CustomAll action for cancellation (leave the server). */
    public static final Identifier AUTH_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "auth_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AuthDialogScreen() {}

    /**
     * Opens the authentication screen for the player.
     */
    public static void open(Player player, boolean isRegistered) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AuthDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(isRegistered
                ? Main.getInstance().getConfig().getString("messages.auth.dialog.login_title",
                    "<gold>✦ Login Required</gold>")
                : Main.getInstance().getConfig().getString("messages.auth.dialog.register_title",
                    "<gold>✦ Registration Required</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Server Authentication</gray>")
        );

        // ─── Body text ───
        net.minecraft.network.chat.Component bodyText = toNative(
            MM.deserialize(isRegistered
                ? Main.getInstance().getConfig().getString("messages.auth.dialog.body_login",
                    "<white>Please enter your password to log in.</white>")
                : Main.getInstance().getConfig().getString("messages.auth.dialog.body_register",
                    "<white>Please choose a password to register.</white>"))
        );

        // ─── Password input field ───
        net.minecraft.network.chat.Component pwLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.auth.dialog.password_label",
                "<gray>Password</gray>"))
        );
        int maxPwLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        TextInput passwordInput = new TextInput(200, pwLabel, true, "", maxPwLen, Optional.empty());

        // ─── Buttons ───
        net.minecraft.network.chat.Component continueLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.auth.dialog.continue_button",
                "<green>✔ Continue</green>"))
        );
        net.minecraft.network.chat.Component exitLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.auth.dialog.exit_button",
                "<red>✖ Exit Server</red>"))
        );

        // Continue → CustomAll action (submits the form with the password)
        ActionButton continueBtn = new ActionButton(
            new CommonButtonData(continueLabel, 150),
            Optional.of(new CustomAll(AUTH_SUBMIT_ID, Optional.empty()))
        );
        // Exit → CustomAll action (sends a cancel signal → server kicks the player)
        ActionButton exitBtn = new ActionButton(
            new CommonButtonData(exitLabel, 150),
            Optional.of(new CustomAll(AUTH_CANCEL_ID, Optional.empty()))
        );

        // ─── Dialog assembly ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            false,                          // canCloseWithEscape — block ESC from closing
            true,                           // pause
            DialogAction.CLOSE,               // afterAction — client closes the window itself instantly (no «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("password", passwordInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(continueBtn),   // mainActions
            Optional.of(exitBtn),   // exitAction (exit button press)
            1                       // columns
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[AuthDialog] Opened auth screen for " + player.getName()
            + " (registered=" + isRegistered + ")");
    }

    /**
     * Closes the open dialog for the player (after successful/failed login).
     */
    public static void close(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        if (craftPlayer.getHandle().connection == null) return;
        craftPlayer.getHandle().connection.send(
            net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE
        );
    }

    /**
     * Parses an SNBT string into a CompoundTag.
     */
    public static CompoundTag parseTag(String snbt) {
        try {
            return net.minecraft.nbt.TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            ConsoleLogger.warn("[AuthDialog] Failed to parse SNBT: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts an Adventure Component → Minecraft Component.
     * Serializes through legacy section codes, then creates a Minecraft Component.
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
