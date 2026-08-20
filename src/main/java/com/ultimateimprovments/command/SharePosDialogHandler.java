package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * SharePosDialogHandler — listens for {@link PlayerCustomClickEvent} and handles
 * clicks from the /ui sharepos confirmation dialog.
 * <p>
 * Identifiers:
 * <ul>
 *   <li>{@code ultimateimprovments:sharepos_submit} — the ✔ Share button; broadcasts
 *       the player's world + coordinates to the whole server</li>
 *   <li>{@code ultimateimprovments:sharepos_cancel} — the ✖ Cancel button</li>
 * </ul>
 */
public class SharePosDialogHandler implements Listener {

    private static final Key SHAREPOS_SUBMIT_KEY = Key.key("ultimateimprovments", "sharepos_submit");
    private static final Key SHAREPOS_CANCEL_KEY = Key.key("ultimateimprovments", "sharepos_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel (player closed the dialog) ───
        if (identifier.equals(SHAREPOS_CANCEL_KEY)) {
            SharePosDialogScreen.close(player);
            player.sendMessage(MessageUtil.parse("<gray>✦ Sharing cancelled.</gray>"));
            ConsoleLogger.info("[SharePosDialog] " + player.getName() + " cancelled.");
            return;
        }

        // ─── ✔ Share ───
        if (!identifier.equals(SHAREPOS_SUBMIT_KEY)) return;

        SharePosDialogScreen.close(player);

        Location loc = player.getLocation();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Bukkit.broadcast(MessageUtil.parse(
                "<dark_gray>[<gold>✦</gold>]</dark_gray> <white>Player</white> <yellow>" + player.getName()
                        + "</yellow> <white>shared their coordinates:</white> "
                        + "<gray>World:</gray> <white>" + worldName + "</white> "
                        + "<gray>X:</gray> <white>" + x + "</white> "
                        + "<gray>Y:</gray> <white>" + y + "</white> "
                        + "<gray>Z:</gray> <white>" + z + "</white>"
        ));

        ConsoleLogger.info("[SharePosDialog] " + player.getName() + " shared coordinates: "
                + worldName + " " + x + " " + y + " " + z);
    }

    /**
     * Gets the Bukkit Player from a PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[SharePosDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Registers the listener in the plugin.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new SharePosDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[SharePosDialog] SharePosDialogHandler registered");
    }
}
