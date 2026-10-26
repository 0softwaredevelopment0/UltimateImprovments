package com.ultimateimprovments.mechanics.features.kits;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * KitDialogHandler — handles the Custom Screen clicks of {@code /ui kit}.
 * <p>
 * Every dialog button maps to its own action identifier
 * ({@code ultimateimprovments:kit_*}), so the {@code -f} flag needs no
 * server-side "pending dialog" state: {@code kit_give_submit_force} /
 * {@code kit_export_submit_force} simply carry the flag.
 * <p>
 * The client closes the dialog itself ({@link net.minecraft.server.dialog.DialogAction#CLOSE}),
 * so failures are reported as command errors 022-030 in chat — never by
 * re-opening the window.
 */
public class KitDialogHandler implements Listener {

    private static final String NS = "ultimateimprovments";

    private static final Key GIVE_SUBMIT = Key.key(NS, "kit_give_submit");
    private static final Key GIVE_SUBMIT_FORCE = Key.key(NS, "kit_give_submit_force");
    private static final Key CREATE_SUBMIT = Key.key(NS, "kit_create_submit");
    private static final Key RENAME_SUBMIT = Key.key(NS, "kit_rename_submit");
    private static final Key DELETE_SUBMIT = Key.key(NS, "kit_delete_submit");
    private static final Key IMPORT_SUBMIT = Key.key(NS, "kit_import_submit");
    private static final Key EXPORT_SUBMIT = Key.key(NS, "kit_export_submit");
    private static final Key EXPORT_SUBMIT_FORCE = Key.key(NS, "kit_export_submit_force");
    private static final Key COOLDOWN_SUBMIT = Key.key(NS, "kit_cooldown_submit");
    private static final Key GLOBALCD_SUBMIT = Key.key(NS, "kit_globalcd_submit");
    private static final Key RESETCD_SUBMIT = Key.key(NS, "kit_resetcd_submit");
    private static final Key RESETGLOBALCD_SUBMIT = Key.key(NS, "kit_resetglobalcd_submit");
    private static final Key CANCEL = Key.key(NS, "kit_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();
        if (identifier == null || !NS.equals(identifier.namespace())) return;
        if (!identifier.value().startsWith("kit_")) return;

        Player player = getPlayer(event);
        if (player == null) return;

        // ── Cancel ──
        if (identifier.equals(CANCEL)) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("kits.cancelled",
                    "<gray>✦ Kit action cancelled.</gray>"), player));
            return;
        }

        if (!KitStore.isReady()) {
            CommandErrors.moduleDisabled(player, "kits");
            return;
        }

        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[Kits] No dialog response from " + player.getName());
            CommandErrors.unknownError(player);
            return;
        }

        // ── Submit actions ──
        if (identifier.equals(GIVE_SUBMIT) || identifier.equals(GIVE_SUBMIT_FORCE)) {
            KitService.give(player,
                    text(response, KitDialogs.KEY_NAME),
                    text(response, KitDialogs.KEY_TARGET),
                    identifier.equals(GIVE_SUBMIT_FORCE));
            return;
        }

        if (identifier.equals(CREATE_SUBMIT)) {
            KitService.create(player,
                    text(response, KitDialogs.KEY_NAME),
                    text(response, KitDialogs.KEY_DESCRIPTION));
            return;
        }

        if (identifier.equals(RENAME_SUBMIT)) {
            KitService.rename(player,
                    text(response, KitDialogs.KEY_OLD),
                    text(response, KitDialogs.KEY_NEW));
            return;
        }

        if (identifier.equals(DELETE_SUBMIT)) {
            Boolean confirmed = response.getBoolean(KitDialogs.KEY_CONFIRM);
            KitService.delete(player, text(response, KitDialogs.KEY_NAME),
                    confirmed != null && confirmed);
            return;
        }
        if (identifier.equals(IMPORT_SUBMIT)) {
            KitService.importFromHand(player, text(response, KitDialogs.KEY_NAME), "");
            return;
        }

        if (identifier.equals(EXPORT_SUBMIT) || identifier.equals(EXPORT_SUBMIT_FORCE)) {
            KitService.exportToHand(player, text(response, KitDialogs.KEY_NAME), "",
                    identifier.equals(EXPORT_SUBMIT_FORCE));
            return;
        }

        if (identifier.equals(COOLDOWN_SUBMIT)) {
            KitService.setCooldown(player, text(response, KitDialogs.KEY_NAME),
                    text(response, KitDialogs.KEY_SECONDS));
            return;
        }

        if (identifier.equals(GLOBALCD_SUBMIT)) {
            KitService.setGlobalCooldown(player, text(response, KitDialogs.KEY_NAME),
                    text(response, KitDialogs.KEY_SECONDS));
            return;
        }

        if (identifier.equals(RESETCD_SUBMIT)) {
            Boolean confirmed = response.getBoolean(KitDialogs.KEY_CONFIRM);
            KitService.resetCooldowns(player, text(response, KitDialogs.KEY_TARGET),
                    confirmed != null && confirmed);
            return;
        }

        if (identifier.equals(RESETGLOBALCD_SUBMIT)) {
            Boolean confirmed = response.getBoolean(KitDialogs.KEY_CONFIRM);
            KitService.resetGlobalCooldown(player, text(response, KitDialogs.KEY_NAME),
                    confirmed != null && confirmed);
        }
    }

    /** Reads a text input; empty string when the field is missing. */
    private static String text(DialogResponseView response, String key) {
        String value = response.getText(key);
        return value == null ? "" : value;
    }

    /** Resolves the clicking player through the Paper connection API. */
    private static Player getPlayer(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[Kits] Could not resolve the player from the click event connection");
        return null;
    }
}
