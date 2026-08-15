package com.ultimateimprovments.command;

import com.ultimateimprovments.command.subcommands.GetPosSubcommand;
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
 * GetPosDialogHandler — listens for {@link PlayerCustomClickEvent} and handles
 * clicks from the /ui getpos dialog.
 * <p>
 * Identifiers:
 * <ul>
 *   <li>{@code ultimateimprovments:getpos_submit} — the ✔ Get button; resolves the nickname
 *       and prints the target's world + coordinates to the sender's chat</li>
 *   <li>{@code ultimateimprovments:getpos_cancel} — the ✖ Cancel button</li>
 * </ul>
 */
public class GetPosDialogHandler implements Listener {

    private static final Key GETPOS_SUBMIT_KEY = Key.key("ultimateimprovments", "getpos_submit");
    private static final Key GETPOS_CANCEL_KEY = Key.key("ultimateimprovments", "getpos_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel (player closed the dialog) ───
        if (identifier.equals(GETPOS_CANCEL_KEY)) {
            GetPosDialogScreen.close(player);
            player.sendMessage(MessageUtil.parse("<gray>✦ Cancelled.</gray>"));
            ConsoleLogger.info("[GetPosDialog] " + player.getName() + " cancelled.");
            return;
        }

        // ─── ✔ Get (look up the nickname) ───
        if (!identifier.equals(GETPOS_SUBMIT_KEY)) return;

        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[GetPosDialog] No dialog response view from " + player.getName());
            reopen(player, "No response from dialog. Please try again.");
            return;
        }

        String targetName = response.getText("target_name");
        if (targetName == null) targetName = "";
        targetName = targetName.trim();

        if (targetName.isEmpty()) {
            reopen(player, "Nickname cannot be empty!");
            return;
        }

        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            reopen(player, "Player \"" + targetName + "\" is not online!");
            return;
        }

        ConsoleLogger.info("[GetPosDialog] " + player.getName() + " requested position of \"" + targetName + "\"");
        GetPosDialogScreen.close(player);
        GetPosSubcommand.showPosition(player, target);
    }

    /**
     * Closes and reopens the dialog with an error message.
     */
    private static void reopen(Player player, String errorMessage) {
        GetPosDialogScreen.close(player);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                GetPosDialogScreen.openRequest(player, errorMessage);
            }
        }, 10L);
    }

    /**
     * Gets the Bukkit Player from a PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[GetPosDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Registers the listener in the plugin.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new GetPosDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[GetPosDialog] GetPosDialogHandler registered");
    }
}
