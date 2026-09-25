package com.ultimateimprovments.command;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.reputation.ReputationManager.Status;
import com.ultimateimprovments.util.ConsoleLogger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import net.minecraft.core.Holder;
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
 * RepDialogScreen — native dialog (Minecraft 26.2 Dialog API) for viewing the
 * reputation of an account ({@code /ui rep [player]}).
 * <p>
 * At a glance it shows:
 * <ul>
 *   <li>the numeric reputation — colored by value (green ≥ 50, yellow ≥ 10,
 *       white ≥ 0, red &lt; 0);</li>
 *   <li>the Account Standing status — icon + localized name (All good,
 *       Limited, Very limited, At risk, Suspended).</li>
 * </ul>
 * One Close button. The dialog uses {@link DialogAction#CLOSE} (NOT
 * {@link DialogAction#WAIT_FOR_RESPONSE}): the client closes the window itself,
 * instantly, without the «Waiting for server…» screen. The Close button carries
 * no action at all — the click only triggers the afterAction (CLOSE), so there
 * is no server round-trip and no {@code PlayerCustomClickEvent} handler is needed.
 * <p>
 * All texts are customizable via messages ({@code reputation.view.dialog.*},
 * ru + en).
 */
public final class RepDialogScreen {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private RepDialogScreen() {}

    /**
     * Opens the reputation dialog for the given viewer.
     *
     * @param player     viewer (the dialog is opened on their screen)
     * @param targetName displayed name of the account whose reputation is shown
     * @param rep        numeric reputation value
     * @param status     Account Standing status
     */
    public static void open(Player player, String targetName, int rep, Status status) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[RepDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Title / external header ───
        net.minecraft.network.chat.Component title = toNative(MM.deserialize(
                msg("reputation.view.dialog.title",
                        "<gold>✦</gold> <white>Reputation — </white><yellow>%player%</yellow>")
                        .replace("%player%", targetName)));
        net.minecraft.network.chat.Component externalTitle = toNative(MM.deserialize(
                msg("reputation.view.dialog.header", "<dark_gray>Account Standing</dark_gray>")));

        // ─── Body lines ───
        List<DialogBody> body = new ArrayList<>();
        body.add(new PlainMessage(toNative(MM.deserialize(
                msg("reputation.view.dialog.rep", "<gray>Reputation: </gray>%color%%rep%</reset>")
                        .replace("%color%", repColor(rep))
                        .replace("%rep%", String.valueOf(rep)))), 310));
        body.add(new PlainMessage(toNative(MM.deserialize(
                msg("reputation.view.dialog.status", "<gray>Status: </gray>%status%")
                        .replace("%status%", status.fullMini()))), 310));

        // ─── Single Close button (no action → afterAction CLOSE closes instantly) ───
        ActionButton closeBtn = new ActionButton(
                new CommonButtonData(toNative(MM.deserialize(
                        msg("reputation.view.dialog.close", "<red>✖ Close</red>"))), 150),
                Optional.empty());

        // ─── Build the dialog ───
        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                             // canCloseWithEscape
                true,                             // pause
                DialogAction.CLOSE,               // the client closes the window itself, instantly (no "Waiting for server")
                body,
                List.of()                         // no inputs
        );

        MultiActionDialog dialog = new MultiActionDialog(data, List.of(closeBtn), Optional.empty(), 1);

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[RepDialog] Opened reputation dialog of " + targetName
                + " for " + player.getName());
    }

    /**
     * Rep value color by thresholds — the single source of truth shared by the
     * chat view and this dialog (green ≥ 50, yellow ≥ 10, white ≥ 0, red &lt; 0).
     */
    public static String repColor(int rep) {
        return rep >= 50 ? "<green>" : rep >= 10 ? "<yellow>" : rep >= 0 ? "<white>" : "<red>";
    }

    private static String msg(String path, String def) {
        return MessagesManager.getString(path, def);
    }

    /**
     * Converts an Adventure Component → Minecraft Component (legacy-section text
     * inside a literal — the same proven pattern as {@link GetPosDialogScreen}).
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
