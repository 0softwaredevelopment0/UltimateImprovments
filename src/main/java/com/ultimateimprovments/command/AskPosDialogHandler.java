package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * AskPosDialogHandler — listens for {@link PlayerCustomClickEvent} and handles
 * clicks from the /ui askpos dialogs.
 * <p>
 * Identifiers:
 * <ul>
 *   <li>{@code ultimateimprovments:askpos_submit} — the sender confirmed the target nickname</li>
 *   <li>{@code ultimateimprovments:askpos_cancel} — the sender cancelled (closed) the dialog</li>
 *   <li>{@code ultimateimprovments:askpos_accept} — the recipient accepted the request</li>
 *   <li>{@code ultimateimprovments:askpos_decline} — the recipient declined the request</li>
 * </ul>
 */
public class AskPosDialogHandler implements Listener {

    private static final Key ASKPOS_SUBMIT_KEY = Key.key("ultimateimprovments", "askpos_submit");
    private static final Key ASKPOS_CANCEL_KEY = Key.key("ultimateimprovments", "askpos_cancel");
    private static final Key ASKPOS_ACCEPT_KEY = Key.key("ultimateimprovments", "askpos_accept");
    private static final Key ASKPOS_DECLINE_KEY = Key.key("ultimateimprovments", "askpos_decline");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel request (sender closed the dialog) ───
        if (identifier.equals(ASKPOS_CANCEL_KEY)) {
            AskPosDialogScreen.close(player);
            player.sendMessage(MessageUtil.parse("<gray>✦ Request cancelled.</gray>"));
            ConsoleLogger.info("[AskPosDialog] " + player.getName() + " cancelled the request.");
            return;
        }

        // ─── Decline (recipient refused) ───
        if (identifier.equals(ASKPOS_DECLINE_KEY)) {
            AskPosDialogScreen.close(player);
            AskCordsManager.decline(player);
            return;
        }

        // ─── Accept (recipient showed the coordinates) ───
        if (identifier.equals(ASKPOS_ACCEPT_KEY)) {
            AskPosDialogScreen.close(player);
            AskCordsManager.accept(player);
            return;
        }

        // ─── Confirm nickname (sender) ───
        if (!identifier.equals(ASKPOS_SUBMIT_KEY)) return;

        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[AskPosDialog] No dialog response view from " + player.getName());
            AskCordsManager.reopenRequest(player, "No response from dialog. Please try again.");
            return;
        }

        String targetName = response.getText("target_name");
        if (targetName == null) targetName = "";
        targetName = targetName.trim();

        if (targetName.isEmpty()) {
            AskCordsManager.reopenRequest(player, "Nickname cannot be empty!");
            return;
        }

        ConsoleLogger.info("[AskPosDialog] " + player.getName() + " submitted request for \"" + targetName + "\"");
        AskCordsManager.handleSubmit(player, targetName);
    }

    /**
     * Gets the Bukkit Player from a PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[AskPosDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Registers the listener in the plugin.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new AskPosDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[AskPosDialog] AskPosDialogHandler registered");
    }
}
