package com.ultimateimprovments.command;

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
 * AskPosDialogScreen — dialog windows (Custom Screen) for the position request (/ui askpos).
 * <p>
 * Two dialogs:
 * <ul>
 *   <li>{@link #openRequest(Player, String)} — the sender enters the nickname of the player
 *       they want to send the request to, then presses ✔ Confirm / ✖ Cancel.</li>
 *   <li>{@link #openResponse(Player, String)} — the recipient sees «Player X requested
 *       your coordinates and world» with ✔ Confirm / ✖ Cancel buttons.</li>
 * </ul>
 * <p>
 * Оба диалога используют {@link DialogAction#CLOSE} (а НЕ {@link DialogAction#WAIT_FOR_RESPONSE}):
 * при {@code WAIT_FOR_RESPONSE} клиент после клика уходит на отдельный экран
 * {@code WaitingForResponseScreen} («Waiting for server…») и игнорирует
 * {@code ClientboundClearDialogPacket} (клиентский {@code clearDialog()} закрывает только
 * {@code DialogScreen}), поэтому окно висело бы ~4 секунды. С {@code CLOSE} клиент закрывает
 * окно СРАЗУ после клика сам (пакет клика с вводом при этом всё равно отправляется),
 * а сервер лишь выполняет действие. {@code close()} отправляет Clear-пакет как страховку —
 * на игровом экране он безвреден.
 */
public class AskPosDialogScreen {

    /** CustomAll action identifier — send the request (confirm nickname). */
    public static final Identifier ASKPOS_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "askpos_submit");
    /** CustomAll action identifier — cancel the request (close the dialog). */
    public static final Identifier ASKPOS_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "askpos_cancel");
    /** CustomAll action identifier — accept the request (show coordinates). */
    public static final Identifier ASKPOS_ACCEPT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "askpos_accept");
    /** CustomAll action identifier — decline the request. */
    public static final Identifier ASKPOS_DECLINE_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "askpos_decline");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AskPosDialogScreen() {}

    /**
     * Opens the request dialog: nickname input field + Confirm/Cancel buttons.
     *
     * @param player       the sender
     * @param errorMessage error message (null if none) — shown inside the dialog
     */
    public static void openRequest(Player player, String errorMessage) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AskPosDialog] Cannot open request dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.title", "<gold>✦ Position Request</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Request coordinates</gray>")
        );

        // ─── Body text ───
        String bodyStr;
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodyStr = "<red>❌ " + errorMessage + "</red>\n\n<white>Enter the nickname of the player:</white>";
        } else {
            bodyStr = Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.body",
                "<white>Enter the nickname of the player whose coordinates you want:</white>");
        }
        net.minecraft.network.chat.Component bodyText = toNative(MM.deserialize(bodyStr));

        // ─── Nickname input field ───
        net.minecraft.network.chat.Component inputLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.nick_label", "<gray>Player Nickname</gray>"))
        );
        TextInput nickInput = new TextInput(200, inputLabel, false, "", 16, Optional.empty());

        // ─── Buttons ───
        net.minecraft.network.chat.Component confirmLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.confirm_button", "<green>✔ Confirm</green>"))
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.cancel_button", "<red>✖ Cancel</red>"))
        );

        ActionButton confirmBtn = new ActionButton(
            new CommonButtonData(confirmLabel, 150),
            Optional.of(new CustomAll(ASKPOS_SUBMIT_ID, Optional.empty()))
        );
        ActionButton cancelBtn = new ActionButton(
            new CommonButtonData(cancelLabel, 150),
            Optional.of(new CustomAll(ASKPOS_CANCEL_ID, Optional.empty()))
        );

        // ─── Build the dialog ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            true,                            // canCloseWithEscape
            true,                            // pause
            DialogAction.CLOSE,               // клиент закрывает окно сам, мгновенно (без «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("target_name", nickInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(confirmBtn),
            Optional.of(cancelBtn),
            1
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[AskPosDialog] Opened request screen for " + player.getName());
    }

    /**
     * Opens the request dialog without an error message.
     */
    public static void openRequest(Player player) {
        openRequest(player, null);
    }

    /**
     * Opens the response dialog at the recipient: «Player X requested your coordinates and world».
     *
     * @param target     the request recipient
     * @param senderName the sender's nickname
     */
    public static void openResponse(Player target, String senderName) {
        if (!(target instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AskPosDialog] Cannot open response dialog for non-CraftPlayer: " + target.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.title", "<gold>✦ Position Request</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Incoming request</gray>")
        );

        // ─── Body text ───
        String bodyStr = Main.getInstance().getConfig().getString(
            "messages.askpos.dialog.response_body",
            "<white>Player </white><yellow>%player%</yellow><white> requests your coordinates and world.</white>")
            .replace("%player%", senderName == null ? "?" : senderName);
        net.minecraft.network.chat.Component bodyText = toNative(MM.deserialize(bodyStr));

        // ─── Buttons ───
        net.minecraft.network.chat.Component acceptLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.accept_button", "<green>✔ Confirm</green>"))
        );
        net.minecraft.network.chat.Component declineLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "messages.askpos.dialog.decline_button", "<red>✖ Cancel</red>"))
        );

        ActionButton acceptBtn = new ActionButton(
            new CommonButtonData(acceptLabel, 150),
            Optional.of(new CustomAll(ASKPOS_ACCEPT_ID, Optional.empty()))
        );
        ActionButton declineBtn = new ActionButton(
            new CommonButtonData(declineLabel, 150),
            Optional.of(new CustomAll(ASKPOS_DECLINE_ID, Optional.empty()))
        );

        // ─── Build the dialog (no input fields) ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            true,                            // canCloseWithEscape
            true,                            // pause
            DialogAction.CLOSE,               // клиент закрывает окно сам, мгновенно (без «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of()                        // no input fields
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(acceptBtn),
            Optional.of(declineBtn),
            1
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[AskPosDialog] Opened response screen for " + target.getName()
            + " (sender=" + senderName + ")");
    }

    /**
     * Closes the currently open dialog of a player (safety net). The primary closing
     * mechanism is {@link DialogAction#CLOSE}: the client closes the dialog by itself
     * right after the click, so no «Waiting for server…» screen appears. This packet is
     * sent for state sync in case the client is still on a dialog screen.
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
