package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
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
 * ChgDimDialogScreen — creates and opens a Custom Screen (Dialog) for teleporting between worlds.
 * <p>
 * Uses the Minecraft 26.2 Dialog API to show a screen with a world-name input field
 * and «✔ Teleport» / «✖ Cancel» buttons.
 * <p>
 * On an error (world not found, no permission, cooldown) it is called again with the error text
 * displayed right in the dialog window.
 * <p>
 * The dialog uses {@link DialogAction#CLOSE} (NOT {@link DialogAction#WAIT_FOR_RESPONSE}):
 * with {@code WAIT_FOR_RESPONSE} the client goes to the «Waiting for server…» screen
 * after a click and ignores {@code ClientboundClearDialogPacket}, so the window would
 * hang for ~4 seconds. With {@code CLOSE} the client closes the window itself immediately
 * after the click, and the server reopens it (on error) or performs the action.
 */
public class ChgDimDialogScreen {

    /** CustomAll action identifier for the teleport. */
    public static final Identifier CHGDIM_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "chgdim_submit");
    /** CustomAll action identifier for returning to the starting point. */
    public static final Identifier CHGDIM_RETURN_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "chgdim_return");
    /** CustomAll action identifier for the cancel. */
    public static final Identifier CHGDIM_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "chgdim_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChgDimDialogScreen() {}

    /**
     * Opens the teleport screen for the player.
     *
     * @param player the target player
     * @param errorMessage optional error message (null if there is no error)
     */
    public static void open(Player player, String errorMessage) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[ChgDimDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.title",
                "<gold>✦ Change Dimension</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Teleport to World</gray>")
        );

        // ─── Body text ───
        String bodyStr;
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // Show the error right in the dialog
            bodyStr = "<red>❌ " + errorMessage + "</red>\n\n<white>Enter a valid world name:</white>";
        } else {
            bodyStr = Main.getInstance().getConfig().getString(
                "changedimmension.dialog.body",
                "<white>Where do you want to teleport?</white>");
        }
        net.minecraft.network.chat.Component bodyText = toNative(MM.deserialize(bodyStr));

        // ─── World name input field ───
        net.minecraft.network.chat.Component inputLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.world_label",
                "<gray>World Name</gray>"))
        );
        TextInput worldInput = new TextInput(200, inputLabel, false, "", 64, Optional.empty());

        // ─── Buttons ───
        net.minecraft.network.chat.Component tpLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.tp_button",
                "<green>✔ Teleport</green>"))
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.cancel_button",
                "<red>✖ Cancel</red>"))
        );

        // TP → CustomAll action (sends the world name)
        ActionButton tpBtn = new ActionButton(
            new CommonButtonData(tpLabel, 150),
            Optional.of(new CustomAll(CHGDIM_SUBMIT_ID, Optional.empty()))
        );
        // Return → CustomAll action (back to the starting point, formerly /ui chgdim_return)
        net.minecraft.network.chat.Component returnLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.return_button",
                "<aqua>↩ Return back</aqua>"))
        );
        ActionButton returnBtn = new ActionButton(
            new CommonButtonData(returnLabel, 150),
            Optional.of(new CustomAll(CHGDIM_RETURN_ID, Optional.empty()))
        );
        // Cancel → CustomAll action (closes the dialog without teleporting)
        ActionButton cancelBtn = new ActionButton(
            new CommonButtonData(cancelLabel, 150),
            Optional.of(new CustomAll(CHGDIM_CANCEL_ID, Optional.empty()))
        );

        // ─── Build the dialog ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            true,                           // canCloseWithEscape — can close with ESC
            true,                           // pause
            DialogAction.CLOSE,               // afterAction — the client closes the window itself, instantly (no «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("world_name", worldInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(tpBtn, returnBtn), // mainActions
            Optional.of(cancelBtn),    // exitAction
            1                          // columns
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[ChgDimDialog] Opened dimension change screen for " + player.getName());
    }

    /**
     * Opens the teleport screen without an error (clean dialog).
     */
    public static void open(Player player) {
        open(player, null);
    }

    /**
     * Closes the open dialog of the player.
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
     * Serializes through legacy section codes, then creates a Minecraft Component.
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
