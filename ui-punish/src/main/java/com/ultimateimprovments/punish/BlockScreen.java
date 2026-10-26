package com.ultimateimprovments.punish;

import com.ultimateimprovments.util.ConsoleLogger;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BlockScreen — the modal dialog shown to a player under the BLOCK punishment.
 * <p>
 * Built with the Minecraft Dialog API (the same pattern as {@code AuthDialogScreen}
 * and {@code RepDialogScreen}): a title, one body line per configured
 * {@code messages[_en].punishment.block} entry, and a single «Close» button.
 * The dialog uses {@link DialogAction#CLOSE} (NOT {@code WAIT_FOR_RESPONSE}):
 * the client closes the window itself, instantly, without the
 * «Waiting for server…» screen.
 * <p>
 * Both the Close button and the dialog exit action (ESC) fire the
 * {@link #BLOCK_CLOSE_ID} custom click, so {@link BlockEnforcer} can run the
 * server-side check: block still active → chat hint + re-open; block expired or
 * lifted → release the player.
 */
public final class BlockScreen {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Custom action fired by the Close button and by the dialog exit action (ESC). */
    public static final Identifier BLOCK_CLOSE_ID =
            Identifier.fromNamespaceAndPath("ultimateimprovments", "block_close");

    /** Same action as an Adventure {@link Key} — what {@code PlayerCustomClickEvent} delivers. */
    public static final Key BLOCK_CLOSE_KEY = Key.key("ultimateimprovments", "block_close");

    /** Width of a body line in the dialog (vanilla default for full-width text). */
    private static final int BODY_WIDTH = 310;

    private BlockScreen() {}

    /**
     * Opens (or re-opens) the block dialog for the player.
     *
     * @param player the blocked player
     * @param block  the active block record (provides punisher, reason and remaining time)
     */
    public static void open(Player player, PunishmentManager.PunishmentRecord block) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[Block] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        if (serverPlayer.connection == null) return;

        String duration = block.isPermanent()
                ? "Permanent"
                : PunishmentMessages.formatTime(block.getRemainingMs());

        // ─── Configurable texts ───
        net.minecraft.network.chat.Component title = toNative(MM.deserialize(
                PunishmentMessages.blockTitle()));
        net.minecraft.network.chat.Component externalTitle = toNative(MM.deserialize(
                PunishmentMessages.blockHeader()));

        List<DialogBody> body = new ArrayList<>();
        for (String line : PunishmentMessages.buildBlockLines(
                block.playerName, block.punishedBy, block.reason, duration,
                PunishmentMessages.getDiscordUrl())) {
            if (line.isEmpty()) {
                // An empty config line still keeps the visual spacing of the template.
                body.add(new PlainMessage(toNative(Component.empty()), BODY_WIDTH));
                continue;
            }
            body.add(new PlainMessage(toNative(MM.deserialize(line)), BODY_WIDTH));
        }
        if (body.isEmpty()) {
            body.add(new PlainMessage(toNative(Component.text("This account has been blocked!")), BODY_WIDTH));
        }

        // ─── Close button: fires the BLOCK_CLOSE custom action ───
        // The server decides whether the block is over (see BlockEnforcer#onCustomClick).
        ActionButton closeBtn = new ActionButton(
                new CommonButtonData(toNative(MM.deserialize(
                        PunishmentMessages.blockCloseButton())), 150),
                Optional.of(new CustomAll(BLOCK_CLOSE_ID, Optional.empty())));

        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                       // canCloseWithEscape — ESC leaves the dialog and triggers the exit action
                true,                       // pause
                DialogAction.CLOSE,         // client closes the window itself (no "Waiting for server")
                body,
                List.of()                   // no inputs
        );

        // The exit action (ESC / window close) runs the same server-side check as the Close button.
        MultiActionDialog dialog = new MultiActionDialog(
                data, List.of(closeBtn), Optional.of(closeBtn), 1);

        serverPlayer.openDialog(Holder.direct(dialog));
    }

    /**
     * Closes the open dialog for the player (used when the block is lifted).
     */
    public static void close(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        if (serverPlayer.connection == null) return;
        serverPlayer.connection.send(ClientboundClearDialogPacket.INSTANCE);
    }

    /**
     * Converts an Adventure Component → Minecraft Component (legacy-section text
     * inside a literal — the same proven pattern as {@code RepDialogScreen}).
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
