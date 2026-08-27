package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * SudoDialogHandler — listens to {@link PlayerCustomClickEvent} and handles
 * sudo password submission or cancellation from the Custom Screen.
 * <p>
 * Two modes:
 * <ul>
 *   <li>{@code ultimateimprovments:sudo_submit} — password submission (Continue)</li>
 *   <li>{@code ultimateimprovments:sudo_cancel} — cancel (close the dialog, no kick)</li>
 * </ul>
 */
public class SudoDialogHandler implements Listener {

    private static final Key SUDO_SUBMIT_KEY = Key.key("ultimateimprovments", "sudo_submit");
    private static final Key SUDO_CANCEL_KEY = Key.key("ultimateimprovments", "sudo_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel (close the dialog, reset the pending command) ───
        if (identifier.equals(SUDO_CANCEL_KEY)) {
            SudoDialogScreen.close(player);
            SudoManager manager = SudoManager.getInstance();
            if (manager != null) {
                manager.discardPending(player.getUniqueId());
            }
            player.sendMessage(com.ultimateimprovments.util.MessageUtil.parse(
                    "<gray>✖ Sudo cancelled. The command was not executed.</gray>"));
            return;
        }

        // ─── Submit (Continue) ───
        if (!identifier.equals(SUDO_SUBMIT_KEY)) return;

        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[SudoDialog] No dialog response view in sudo submit from " + player.getName());
            reopen(player);
            return;
        }

        String password = response.getText("password");
        if (password == null || password.isEmpty()) {
            reopen(player);
            return;
        }

        ConsoleLogger.info("[SudoDialog] Sudo password submitted by " + player.getName()
            + " (len=" + password.length() + ")");

        SudoManager manager = SudoManager.getInstance();
        if (manager != null) {
            manager.handlePasswordSubmit(player, password);
        }
    }

    private void reopen(Player player) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                boolean registered = SudoDatabase.isRegistered(player.getUniqueId());
                SudoDialogScreen.open(player, registered);
            }
        }, 10L);
    }

    /**
     * Gets the Bukkit Player from PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[SudoDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Registers the listener in the plugin.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new SudoDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[SudoDialog] SudoDialogHandler registered");
    }
}
