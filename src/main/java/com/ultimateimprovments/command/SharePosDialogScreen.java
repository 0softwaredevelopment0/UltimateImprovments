package com.ultimateimprovments.command;

import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * SharePosDialogScreen — confirmation dialog for /ui sharepos.
 * <p>
 * Asks the player «Are you sure you want to share your coordinates?» with
 * ✔ Share / ✖ Cancel buttons. On ✔ Share the player's world and coordinates
 * are broadcast to the whole server (in the plugin's name, in English).
 * <p>
 * Uses {@link DialogAction#CLOSE} — the client closes the window immediately
 * after the click (no ~4s «Waiting for server…» hang).
 */
public class SharePosDialogScreen {

    /** CustomAll action identifier — confirm sharing. */
    public static final Identifier SHAREPOS_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "sharepos_submit");
    /** CustomAll action identifier — cancel (close the dialog). */
    public static final Identifier SHAREPOS_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "sharepos_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SharePosDialogScreen() {}

    /**
     * Opens the sharepos confirmation dialog.
     *
     * @param player the sender
     */
    public static void openRequest(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[SharePosDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
                MM.deserialize("<gold>✦ Share Position</gold>")
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
                MM.deserialize("<gray>Share your coordinates</gray>")
        );

        // ─── Body text ───
        net.minecraft.network.chat.Component bodyText = toNative(
                MM.deserialize("<white>Are you sure you want to share your coordinates with everyone?</white>")
        );

        // ─── Buttons ───
        net.minecraft.network.chat.Component shareLabel = toNative(
                MM.deserialize("<green>✔ Share</green>")
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
                MM.deserialize("<red>✖ Cancel</red>")
        );

        ActionButton shareBtn = new ActionButton(
                new CommonButtonData(shareLabel, 150),
                Optional.of(new CustomAll(SHAREPOS_SUBMIT_ID, Optional.empty()))
        );
        ActionButton cancelBtn = new ActionButton(
                new CommonButtonData(cancelLabel, 150),
                Optional.of(new CustomAll(SHAREPOS_CANCEL_ID, Optional.empty()))
        );

        // ─── Build the dialog ───
        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                            // canCloseWithEscape
                true,                            // pause
                DialogAction.CLOSE,               // the client closes the window itself, instantly
                List.of(new PlainMessage(bodyText, 310)),
                List.of()
        );

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                List.of(shareBtn),
                Optional.of(cancelBtn),
                1
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[SharePosDialog] Opened dialog for " + player.getName());
    }

    /**
     * Closes the currently open dialog of a player (safety net).
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
