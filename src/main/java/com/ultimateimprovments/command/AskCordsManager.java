package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AskCordsManager — coordinate request via dialogs (/ui askpos).
 * <p>
 * Mechanics:
 * <ol>
 *   <li>Player A runs {@code /ui askpos} — a dialog opens with an input field for the
 *       nickname of the player to send the request to (the nickname must be online).</li>
 *   <li>After «✔ Confirm», recipient B gets a dialog «Player A requested your coordinates
 *       and world» with ✔ Confirm / ✖ Cancel buttons.</li>
 *   <li>If B confirms — A receives the world and coordinates of player B; if cancelled —
 *       A gets a refusal message.</li>
 *   <li>Command cooldown: 30 seconds (against dialog spam).</li>
 * </ol>
 */
public class AskCordsManager {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, UUID> pendingRequests = new HashMap<>(); // target -> sender

    private static final long COOLDOWN_MS = 30_000L; // 30 seconds

    private AskCordsManager() {}

    /**
     * Executes /ui askpos: checks the cooldown and opens the request dialog.
     */
    public static boolean execute(Player sender) {
        UUID senderUuid = sender.getUniqueId();

        // =========================
        // COOLDOWN CHECK (set immediately — against dialog spam)
        // =========================
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(senderUuid);
        if (lastUse != null && (now - lastUse) < COOLDOWN_MS) {
            long remaining = ((COOLDOWN_MS - (now - lastUse)) / 1000) + 1;
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askpos.cooldown",
                            "<red>❌ Please wait </red><yellow>%seconds%</yellow><red> seconds before using this again!</red>")
                            .replace("%seconds%", String.valueOf(remaining))));
            sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return true;
        }

        cooldowns.put(senderUuid, now);
        AskPosDialogScreen.openRequest(sender);
        return true;
    }

    /**
     * Sends the request after the nickname is confirmed in the dialog.
     */
    public static void handleSubmit(Player sender, String targetName) {
        // =========================
        // FIND TARGET (must be online)
        // =========================
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            reopenRequest(sender, "Player \"" + targetName + "\" is not online!");
            return;
        }

        // Cannot send a request to yourself
        if (sender.equals(target)) {
            reopenRequest(sender, "You cannot request your own coordinates!");
            return;
        }

        // =========================
        // SEND REQUEST
        // =========================
        pendingRequests.put(target.getUniqueId(), sender.getUniqueId());

        // Message to the sender
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.request_sent",
                        "<green>✔</green> <white>Position request sent to</white> <yellow>%player%</yellow><white>.</white>")
                        .replace("%player%", target.getName())));
        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        // Close the sender's dialog and open the response dialog for the recipient
        AskPosDialogScreen.close(sender);
        AskPosDialogScreen.openResponse(target, sender.getName());
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
    }

    /**
     * Closes and reopens the request dialog with an error message.
     */
    public static void reopenRequest(Player player, String errorMessage) {
        AskPosDialogScreen.close(player);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                AskPosDialogScreen.openRequest(player, errorMessage);
            }
        }, 10L);
    }

    /**
     * Handles the recipient accepting the request (button in the response dialog).
     */
    public static boolean accept(Player target) {
        UUID targetUuid = target.getUniqueId();
        UUID senderUuid = pendingRequests.remove(targetUuid);

        if (senderUuid == null) {
            target.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askpos.no_pending_request",
                            "<red>❌ You have no pending position request!</red>")));
            return true;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askpos.sender_offline",
                            "<red>❌ The player who requested the coordinates is no longer online!</red>")));
            return true;
        }

        // Get the world and coordinates of the target
        Location targetLoc = target.getLocation();
        String worldName = targetLoc.getWorld().getName();
        int x = targetLoc.getBlockX();
        int y = targetLoc.getBlockY();
        int z = targetLoc.getBlockZ();

        // Message to the sender with the coordinates
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_header",
                        "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_title",
                        "<gold>  ✦ </gold><green>Coordinates received!</green>")));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_header",
                        "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_player",
                        "<gray>Player: </gray><yellow>%player%</yellow>")
                        .replace("%player%", target.getName())));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_world",
                        "<gray>World: </gray><white>%world%</white>")
                        .replace("%world%", worldName)));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_coords",
                        "<gray>Coordinates: </gray><white>%x% / %y% / %z%</white>")
                        .replace("%x%", String.valueOf(x))
                        .replace("%y%", String.valueOf(y))
                        .replace("%z%", String.valueOf(z))));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.result_header",
                        "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage("");

        sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // Message to the target
        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.accepted_notify",
                        "<green>✔</green> <white>You shared your coordinates with</white> <yellow>%player%</yellow><white>.</white>")
                        .replace("%player%", sender.getName())));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.2f);

        return true;
    }

    /**
     * Handles the recipient declining the request (button in the response dialog).
     */
    public static boolean decline(Player target) {
        UUID targetUuid = target.getUniqueId();
        UUID senderUuid = pendingRequests.remove(targetUuid);

        if (senderUuid == null) {
            target.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askpos.no_pending_request",
                            "<red>❌ You have no pending position request!</red>")));
            return true;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askpos.declined_notify_sender",
                            "<red>❌ Player </red><yellow>%player%</yellow><red> declined your position request.</red>")
                            .replace("%player%", target.getName())));
            sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        }

        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askpos.declined_notify_target",
                        "<yellow>✦</yellow> <white>You declined the position request.</white>")));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);

        return true;
    }

    /**
     * Clears the player's data (on quit).
     */
    public static void cleanup(UUID uuid) {
        cooldowns.remove(uuid);
        pendingRequests.remove(uuid);
        // Also remove waiting requests where this player is the sender
        pendingRequests.values().removeIf(v -> v.equals(uuid));
    }
}
