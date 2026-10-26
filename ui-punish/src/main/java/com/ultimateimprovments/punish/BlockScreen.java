package com.ultimateimprovments.punish;

import com.ultimateimprovments.util.ConsoleLogger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
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
 * Closing the dialog does not lift the block — {@link BlockEnforcer} re-opens it
 * while the punishment is active.
 */
public final class BlockScreen {

    private static final MiniMessage MM = MiniMessage.miniMessage();

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

        // ─── Single Close button: no action → afterAction (CLOSE) closes instantly ───
        ActionButton closeBtn = new ActionButton(
                new CommonButtonData(toNative(MM.deserialize(
                        PunishmentMessages.blockCloseButton())), 150),
                Optional.empty());

        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                       // canCloseWithEscape — the player may dismiss it, we re-open
                true,                       // pause
                DialogAction.CLOSE,         // client closes the window itself (no "Waiting for server")
                body,
                List.of()                   // no inputs
        );

        MultiActionDialog dialog = new MultiActionDialog(data, List.of(closeBtn), Optional.empty(), 1);

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
