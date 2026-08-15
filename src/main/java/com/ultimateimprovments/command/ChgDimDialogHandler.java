package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * ChgDimDialogHandler — listens for {@link PlayerCustomClickEvent} and handles
 * world-name submission or cancellation from the Custom Screen teleportation.
 * <p>
 * Two modes:
 * <ul>
 *   <li>{@code ultimateimprovments:chgdim_submit} — teleport to the specified world</li>
 *   <li>{@code ultimateimprovments:chgdim_return} — return to the starting point (formerly /ui chgdim_return)</li>
 *   <li>{@code ultimateimprovments:chgdim_cancel} — cancel the teleportation</li>
 * </ul>
 * <p>
 * On error (world not found, no permission, cooldown) — re-opens the dialog
 * with the error text right inside the window.
 */
public class ChgDimDialogHandler implements Listener {

    private static final Key CHGDIM_SUBMIT_KEY = Key.key("ultimateimprovments", "chgdim_submit");
    private static final Key CHGDIM_RETURN_KEY = Key.key("ultimateimprovments", "chgdim_return");
    private static final Key CHGDIM_CANCEL_KEY = Key.key("ultimateimprovments", "chgdim_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        // Get the player through PlayerGameConnection
        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Return — go back to the starting point (formerly /ui chgdim_return) ───
        if (identifier.equals(CHGDIM_RETURN_KEY)) {
            ChgDimDialogScreen.close(player);
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    ChgDimCommand.teleportBack(player);
                }
            });
            ConsoleLogger.info("[ChgDimDialog] Player " + player.getName() + " used Return back.");
            return;
        }

        // ─── Cancel ───
        if (identifier.equals(CHGDIM_CANCEL_KEY)) {
            ChgDimDialogScreen.close(player);
            player.sendMessage(MessageUtil.parse(
                "<gray>✦ Teleportation cancelled.</gray>"
            ));
            ConsoleLogger.info("[ChgDimDialog] Player " + player.getName() + " cancelled teleportation.");
            return;
        }

        // ─── Submit (TP) ───
        if (!identifier.equals(CHGDIM_SUBMIT_KEY)) return;

        // Extract the world name from DialogResponseView (Paper API)
        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[ChgDimDialog] No dialog response view from " + player.getName());
            reopenWithError(player, "No response from dialog. Please try again.");
            return;
        }

        String worldName = response.getText("world_name");
        if (worldName == null || worldName.trim().isEmpty()) {
            reopenWithError(player, "World name cannot be empty!");
            return;
        }

        worldName = worldName.trim();

        ConsoleLogger.info("[ChgDimDialog] World name submitted by " + player.getName()
            + ": \"" + worldName + "\"");

        // Check permission for the specific world
        if (!player.hasPermission("ui.command.chgdim." + worldName)) {
            reopenWithError(player, "You do not have permission to teleport to \"" + worldName + "\"!");
            return;
        }

        // Check the cooldown
        String cooldownError = checkCooldown(player);
        if (cooldownError != null) {
            reopenWithError(player, cooldownError);
            return;
        }

        // Check whether the world is configured
        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || !worldsSection.contains(worldName)) {
            reopenWithError(player, "World \"" + worldName + "\" is not configured!");
            return;
        }

        // Check whether the world exists on the server
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            reopenWithError(player, "World \"" + worldName + "\" not found on the server!");
            return;
        }

        // ─── Success — teleport ───
        ChgDimDialogScreen.close(player);

        String finalWorldName = worldName;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            ChgDimCommand.teleport(player, finalWorldName);
        });
    }

    /**
     * Checks the teleportation cooldown for the player.
     *
     * @return null if the cooldown has passed, or an error message string
     */
    private String checkCooldown(Player player) {
        java.util.UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis() / 1000;
        int cooldownSecs = Main.getInstance().getConfig()
            .getInt("changedimmension.cooldown_seconds", 10);

        if (ChgDimCommand.cooldowns.containsKey(playerUuid)) {
            long lastUse = ChgDimCommand.cooldowns.get(playerUuid);
            long elapsed = now - lastUse;
            if (elapsed < cooldownSecs) {
                long remaining = cooldownSecs - elapsed;
                return "Please wait " + remaining + " seconds before teleporting again!";
            }
        }
        return null;
    }

    /**
     * Gets the Bukkit Player from PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[ChgDimDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Closes the dialog and re-opens it with an error message.
     */
    private void reopenWithError(Player player, String errorMessage) {
        ChgDimDialogScreen.close(player);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                ChgDimDialogScreen.open(player, errorMessage);
            }
        }, 10L);
    }

    /**
     * Registers the listener in the plugin.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new ChgDimDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[ChgDimDialog] ChgDimDialogHandler registered");
    }
}
