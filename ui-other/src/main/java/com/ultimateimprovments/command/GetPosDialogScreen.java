package com.ultimateimprovments.command;

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
 * GetPosDialogScreen — dialog window (Custom Screen) for the coordinate lookup (/ui getpos).
 * <p>
 * A single dialog: the player enters the nickname of the target, then presses
 * ✔ Get / ✖ Cancel. On ✔ Get the target's world and coordinates are printed to chat.
 * <p>
 * Uses {@link DialogAction#CLOSE} (NOT {@link DialogAction#WAIT_FOR_RESPONSE}): with
 * {@code WAIT_FOR_RESPONSE} the client switches to the «Waiting for server…» screen and
 * ignores {@code ClientboundClearDialogPacket}, so the window would hang ~4 seconds.
 * With {@code CLOSE} the client closes the window immediately after the click itself
 * (the click-with-input packet is still sent), while the server only performs the action.
 */
public class GetPosDialogScreen {

    /** CustomAll action identifier — look up the nickname (show coordinates). */
    public static final Identifier GETPOS_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "getpos_submit");
    /** CustomAll action identifier — cancel (close the dialog). */
    public static final Identifier GETPOS_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "getpos_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GetPosDialogScreen() {}

    /**
     * Opens the getpos dialog: nickname input field + ✔ Get / ✖ Cancel buttons.
     *
     * @param player       the sender
     * @param errorMessage error message (null if none) — shown inside the dialog
     */
    public static void openRequest(Player player, String errorMessage) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[GetPosDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize("<gold>✦ Get Position</gold>")
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Get player position</gray>")
        );

        // ─── Body text ───
        String bodyStr;
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodyStr = "<red>❌ " + errorMessage + "</red>\n\n<white>Enter the nickname of the player:</white>";
        } else {
            bodyStr = "<white>Enter the nickname of the player whose coordinates you want:</white>";
        }
        net.minecraft.network.chat.Component bodyText = toNative(MM.deserialize(bodyStr));

        // ─── Nickname input field ───
        net.minecraft.network.chat.Component inputLabel = toNative(
            MM.deserialize("<gray>Player Nickname</gray>")
        );
        TextInput nickInput = new TextInput(200, inputLabel, false, "", 16, Optional.empty());

        // ─── Buttons ───
        net.minecraft.network.chat.Component getLabel = toNative(
            MM.deserialize("<green>✔ Get</green>")
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
            MM.deserialize("<red>✖ Cancel</red>")
        );

        ActionButton getBtn = new ActionButton(
            new CommonButtonData(getLabel, 150),
            Optional.of(new CustomAll(GETPOS_SUBMIT_ID, Optional.empty()))
        );
        ActionButton cancelBtn = new ActionButton(
            new CommonButtonData(cancelLabel, 150),
            Optional.of(new CustomAll(GETPOS_CANCEL_ID, Optional.empty()))
        );

        // ─── Build the dialog ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            true,                            // canCloseWithEscape
            true,                            // pause
            DialogAction.CLOSE,               // the client closes the window itself, instantly (without "Waiting for server")
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("target_name", nickInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(getBtn),
            Optional.of(cancelBtn),
            1
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[GetPosDialog] Opened dialog for " + player.getName());
    }

    /**
     * Opens the getpos dialog without an error message.
     */
    public static void openRequest(Player player) {
        openRequest(player, null);
    }

    /**
     * Closes the currently open dialog of a player (safety net). The primary closing
     * mechanism is {@link DialogAction#CLOSE}: the client closes the dialog by itself
     * right after the click, so no «Waiting for server…» screen appears.
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
