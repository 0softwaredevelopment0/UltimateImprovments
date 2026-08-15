package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CodePanelDialogScreen — the code panel as a Custom Screen (Dialog).
 * <p>
 * Instead of a double chest / chat keyboard — a native dialog window
 * from the Minecraft 26.2 Dialog API with digit buttons 0-9, an entered-code screen,
 * «←», «✔ Confirm» and «✖ Cancel» buttons.
 * <p>
 * Clicks are handled in {@link CodePanelDialogHandler} via
 * {@link io.papermc.paper.event.player.PlayerCustomClickEvent}.
 * <p>
 * The dialog uses {@link DialogAction#CLOSE} (NOT {@link DialogAction#WAIT_FOR_RESPONSE}):
 * with {@code WAIT_FOR_RESPONSE} the client goes to the «Waiting for server…» screen
 * after a click and ignores {@code ClientboundClearDialogPacket}, so the window would
 * hang for ~4 seconds (e.g. on confirm/cancel). With {@code CLOSE} the client closes
 * the window IMMEDIATELY after the click, and the server reopens it with the updated
 * code (Handler#reopen).
 */
public class CodePanelDialogScreen {

    /** Prefix of the digit-button identifiers: ultimateimprovments:codepanel_digit_N. */
    public static final String DIGIT_PREFIX = "codepanel_digit_";
    /** Identifier of the «←» button (delete the last digit). */
    public static final Identifier BACKSPACE_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "codepanel_backspace");
    /** Identifier of the «✔ Confirm» button. */
    public static final Identifier CONFIRM_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "codepanel_confirm");
    /** Identifier of the «✖ Cancel» button. */
    public static final Identifier CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "codepanel_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CodePanelDialogScreen() {}

    /** CustomAll identifier for the digit button N. */
    public static Identifier digitId(int digit) {
        return Identifier.fromNamespaceAndPath("ultimateimprovments", DIGIT_PREFIX + digit);
    }

    /**
     * Opens the code panel dialog with the current input state from {@link CodePanelSession}.
     *
     * @param player the player
     */
    public static void open(Player player) {
        open(player, null);
    }

    /**
     * Opens the code panel dialog with the current input state from {@link CodePanelSession}.
     *
     * @param player       the player
     * @param errorMessage optional error text shown right in the dialog (null if there is no error)
     */
    public static void open(Player player, String errorMessage) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[CodePanelDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(player.getUniqueId());
        if (code.length() > max) code = code.substring(0, max);

        // ─── Titles ───
        net.minecraft.network.chat.Component title = toNative(
                MM.deserialize("<gold>✦ Code Panel</gold>")
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
                MM.deserialize("<gray>Enter Code</gray>")
        );

        // ─── Screen: entered code (+ error if any) ───
        String display = buildCodeDisplay(code, max);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // errorMessage is already a ready MiniMessage string — must not wrap it again
            display = errorMessage + "\n\n" + display;
        }
        net.minecraft.network.chat.Component bodyText = toNative(
                MM.deserialize(display)
        );

        // ─── Buttons: 1-9 ───
        List<ActionButton> actions = new ArrayList<>();
        for (int d = 1; d <= 9; d++) {
            actions.add(digitButton(d));
        }
        // ─── 0, ←, ✔ ───
        actions.add(digitButton(0));
        actions.add(backspaceButton());
        actions.add(confirmButton());

        // ─── ✖ Cancel (exitAction) ───
        ActionButton cancelBtn = new ActionButton(
                new CommonButtonData(toNative(MM.deserialize("<red>✖ Cancel</red>")), 150),
                Optional.of(new CustomAll(CANCEL_ID, Optional.empty()))
        );

        // ─── Build the dialog ───
        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                           // canCloseWithEscape
                true,                           // pause
                DialogAction.CLOSE,               // the client closes the window itself, instantly (no «Waiting for server»)
                List.of(new PlainMessage(bodyText, 310)),
                List.of()                        // no text inputs — buttons only
        );

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                actions,                        // mainActions (3 columns × 4 rows)
                Optional.of(cancelBtn),         // exitAction
                3                               // columns
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[CodePanelDialog] Opened code panel for " + player.getName());
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

    // =========================
    // BUILDERS
    // =========================

    /**
     * Builds the code display string, e.g.: {@code <gray>[1][2][-][-]</gray>}.
     */
    private static String buildCodeDisplay(String code, int max) {
        StringBuilder sb = new StringBuilder("<white>Code: </white>");
        for (int i = 0; i < max; i++) {
            if (i < code.length()) {
                sb.append("<green>[").append(code.charAt(i)).append("]</green>");
            } else {
                sb.append("<dark_gray>[-]</dark_gray>");
            }
        }
        return sb.toString();
    }

    private static ActionButton digitButton(int digit) {
        net.minecraft.network.chat.Component label = toNative(
                MM.deserialize("<white>" + digit + "</white>")
        );
        return new ActionButton(
                new CommonButtonData(label, 60),
                Optional.of(new CustomAll(digitId(digit), Optional.empty()))
        );
    }

    private static ActionButton backspaceButton() {
        net.minecraft.network.chat.Component label = toNative(
                MM.deserialize("<yellow>←</yellow>")
        );
        return new ActionButton(
                new CommonButtonData(label, 60),
                Optional.of(new CustomAll(BACKSPACE_ID, Optional.empty()))
        );
    }

    private static ActionButton confirmButton() {
        net.minecraft.network.chat.Component label = toNative(
                MM.deserialize("<green>✔</green>")
        );
        return new ActionButton(
                new CommonButtonData(label, 60),
                Optional.of(new CustomAll(CONFIRM_ID, Optional.empty()))
        );
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Converts an Adventure Component → Minecraft Component.
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
