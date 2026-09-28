package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
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
 * SudoDialogScreen — «second authentication dialog»: a Custom Screen
 * with a field for the sudo password (modeled after {@code AuthDialogScreen}).
 * <p>
 * Two modes:
 * <ul>
 *   <li>{@code registered=true} — the player has already set a sudo password → enter it;</li>
 *   <li>{@code registered=false} — no password yet → ask to set one and not forget it.</li>
 * </ul>
 * <p>
 * The dialog uses {@link DialogAction#CLOSE} (NOT {@link DialogAction#WAIT_FOR_RESPONSE}):
 * with {@code WAIT_FOR_RESPONSE} the client moves to the «Waiting for server…» screen after
 * clicking and ignores {@code ClientboundClearDialogPacket}, so the window would linger ~4s.
 * With {@code CLOSE} the client closes the window immediately after clicking, and the server
 * re-opens it or performs the action (sudo commands are still intercepted while the session is inactive).
 */
public class SudoDialogScreen {

    /** Identifier of the CustomAll action that submits the sudo password. */
    public static final Identifier SUDO_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "sudo_submit");
    /** Identifier of the CustomAll action for cancellation (close the dialog). */
    public static final Identifier SUDO_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "sudo_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SudoDialogScreen() {}

    /**
     * Opens the sudo password dialog.
     *
     * @param player     the player
     * @param registered whether a sudo password already exists (true → login, false → creation)
     */
    public static void open(Player player, boolean registered) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[SudoDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.title",
                "<gold>✦ Sudo Mode Required</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Server Sudo</gray>")
        );

        // ─── Body text ───
        net.minecraft.network.chat.Component bodyText = toNative(
            MM.deserialize(registered
                ? Main.getInstance().getConfig().getString("messages.sudo.dialog.body_login",
                    "<white>Enter your sudo password to continue.</white>")
                : Main.getInstance().getConfig().getString("messages.sudo.dialog.body_register",
                    "<white>Set a sudo password. <yellow>Do not forget it!</yellow></white>"))
        );

        // ─── Password input field ───
        net.minecraft.network.chat.Component pwLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.password_label",
                "<gray>Sudo Password</gray>"))
        );
        int maxPwLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        TextInput passwordInput = new TextInput(200, pwLabel, true, "", maxPwLen, Optional.empty());

        // ─── Buttons ───
        net.minecraft.network.chat.Component continueLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.continue_button",
                "<green>✔ Continue</green>"))
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.cancel_button",
                "<gray>✖ Cancel</gray>"))
        );

        ActionButton continueBtn = new ActionButton(
            new CommonButtonData(continueLabel, 150),
            Optional.of(new CustomAll(SUDO_SUBMIT_ID, Optional.empty()))
        );
        ActionButton cancelBtn = new ActionButton(
            new CommonButtonData(cancelLabel, 150),
            Optional.of(new CustomAll(SUDO_CANCEL_ID, Optional.empty()))
        );

        // ─── Dialog assembly ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            false,                          // canCloseWithEscape
            true,                           // pause
            DialogAction.CLOSE,               // client closes the window itself instantly (no «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("password", passwordInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(continueBtn),
            Optional.of(cancelBtn),
            1
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[SudoDialog] Opened sudo screen for " + player.getName()
            + " (registered=" + registered + ")");
    }

    /**
     * Closes the open dialog for the player.
     */
    public static void close(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        if (craftPlayer.getHandle().connection == null) return;
        craftPlayer.getHandle().connection.send(
            net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE
        );
    }

    /**
     * Converts an Adventure Component → Minecraft Component.
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
