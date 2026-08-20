package com.ultimateimprovments.mechanics.security.check;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-cheat check manager.
 * <p>
 * Stores pairs (inspector → suspect).
 * On the inspector's quit — automatically ends the check.
 * On the suspect's quit — the check is paused
 * and automatically restored on reconnect.
 * The checked player is frozen (cannot move, interact, etc.).
 */
public class CheckManager {

    private static CheckManager instance;

    // inspector UUID → suspect UUID
    private final Map<UUID, UUID> activeChecks = new ConcurrentHashMap<>();

    // suspect UUID → inspector UUID (reverse index)
    private final Map<UUID, UUID> suspectToInspector = new ConcurrentHashMap<>();

    // inspector UUID → original location (to return to after the check)
    private final Map<UUID, Location> inspectorLocations = new ConcurrentHashMap<>();

    // suspect UUID → repeating title task ID (-1 if not started)
    private final Map<UUID, Integer> suspectTitleTasks = new ConcurrentHashMap<>();

    // suspect UUID → inspector name (to restore the title on reconnect)
    private final Map<UUID, String> suspectInspectorNames = new ConcurrentHashMap<>();

    private CheckManager() {}

    public static void init() {
        instance = new CheckManager();
        ConsoleLogger.info("[CheckManager] ✔ Initialized.");
    }

    public static CheckManager getInstance() {
        return instance;
    }

    // =========================
    // START CHECK
    // =========================
    public static boolean startCheck(Player inspector, Player suspect) {
        if (instance == null) return false;
        if (inspector.equals(suspect)) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You cannot check yourself!</red>"));
            return false;
        }
        if (instance.activeChecks.containsKey(inspector.getUniqueId())) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You already have an active check!</red>"));
            return false;
        }
        if (instance.suspectToInspector.containsKey(suspect.getUniqueId())) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ This player is already being checked!</red>"));
            return false;
        }

        UUID inspectorId = inspector.getUniqueId();
        UUID suspectId = suspect.getUniqueId();

        instance.activeChecks.put(inspectorId, suspectId);
        instance.suspectToInspector.put(suspectId, inspectorId);

        // Save inspector's location and teleport to suspect
        instance.inspectorLocations.put(inspectorId, inspector.getLocation());
        inspector.teleport(suspect.getLocation());

        // Freeze suspect
        freezePlayer(suspect);

        // Save inspector name for rejoin
        instance.suspectInspectorNames.put(suspectId, inspector.getName());

        // Start repeating title task (every 3 seconds)
        startTitleTask(suspect, inspector.getName());

        // Chat instructions
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Verification"));
        suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
        suspect.sendMessage(MessageUtil.parse("<white>Inspector: <yellow>" + inspector.getName()));
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse("<gray>You cannot move or interact with anything"));
        suspect.sendMessage(MessageUtil.parse("<gray>until the check is complete."));
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse("<gray>If you have any prohibited modifications —"));
        suspect.sendMessage(MessageUtil.parse("<gray>disable them now. It is in your best interest."));
        suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
        suspect.sendMessage("");

        // Notify inspector
        inspector.sendMessage(MessageUtil.parse("<green>✔</green> <white>Check started for</white> <yellow>" + suspect.getName() + "</yellow><white>.</white>"));
        inspector.sendMessage(MessageUtil.parse("<gray>Use </gray><white>/ui uncheck " + suspect.getName() + "</white><gray> when finished.</gray>"));

        ConsoleLogger.info(
                "[CheckManager] " + inspector.getName() + " started checking " + suspect.getName());
        return true;
    }

    // =========================
    // END CHECK
    // =========================
    public static boolean endCheck(Player inspector, Player suspect) {
        if (instance == null) return false;

        UUID inspectorId = inspector.getUniqueId();
        UUID suspectId = suspect.getUniqueId();

        UUID storedSuspect = instance.activeChecks.get(inspectorId);
        if (storedSuspect == null || !storedSuspect.equals(suspectId)) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You are not checking this player!</red>"));
            return false;
        }

        instance.activeChecks.remove(inspectorId);
        instance.suspectToInspector.remove(suspectId);
        instance.suspectInspectorNames.remove(suspectId);

        // Teleport inspector back to original location
        Location originalLoc = instance.inspectorLocations.remove(inspectorId);
        if (originalLoc != null && inspector.isOnline()) {
            inspector.teleport(originalLoc);
            inspector.sendMessage(MessageUtil.parse("<gray>You have been teleported back to your original location.</gray>"));
        }

        // Cancel repeating title task and clear title
        cancelTitleTask(suspectId);
        if (suspect.isOnline()) {
            suspect.sendTitle(" ", " ", 0, 1, 0);
            unfreezePlayer(suspect);
            suspect.sendMessage("");
            suspect.sendMessage(MessageUtil.parse("<green>✔ <white><bold>CHECK COMPLETE!"));
            suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            suspect.sendMessage(MessageUtil.parse("<white>Inspector: <yellow>" + inspector.getName()));
            suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            suspect.sendMessage("");
        }

        inspector.sendMessage(MessageUtil.parse("<green>✔</green> <white>Check ended for</white> <yellow>" + suspect.getName() + "</yellow><white>.</white>"));

        ConsoleLogger.info(
                "[CheckManager] " + inspector.getName() + " ended check for " + suspect.getName());
        return true;
    }

    // =========================
    // FORCE END CHECK — end the check without the suspect object (they may be offline)
    // =========================
    public static boolean forceEndCheck(Player inspector) {
        if (instance == null) return false;

        UUID inspectorId = inspector.getUniqueId();
        UUID suspectId = instance.activeChecks.get(inspectorId);
        if (suspectId == null) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You don't have an active check!</red>"));
            return false;
        }

        String suspectName = Bukkit.getOfflinePlayer(suspectId).getName();
        if (suspectName == null) suspectName = suspectId.toString();

        instance.activeChecks.remove(inspectorId);
        instance.suspectToInspector.remove(suspectId);
        instance.suspectInspectorNames.remove(suspectId);

        // Teleport inspector back
        Location originalLoc = instance.inspectorLocations.remove(inspectorId);
        if (originalLoc != null) {
            inspector.teleport(originalLoc);
            inspector.sendMessage(MessageUtil.parse("<gray>You have been teleported back to your original location.</gray>"));
        }

        // Cancel title task
        cancelTitleTask(suspectId);

        // If the suspect is online — unfreeze
        Player suspect = Bukkit.getPlayer(suspectId);
        if (suspect != null && suspect.isOnline()) {
            suspect.sendTitle(" ", " ", 0, 1, 0);
            unfreezePlayer(suspect);
            suspect.sendMessage("");
            suspect.sendMessage(MessageUtil.parse("<green>✔ <white><bold>CHECK COMPLETE!"));
            suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            suspect.sendMessage(MessageUtil.parse("<white>Inspector: <yellow>" + inspector.getName()));
            suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            suspect.sendMessage("");
        }

        inspector.sendMessage(MessageUtil.parse("<green>✔</green> <white>Check ended for</white> <yellow>" + suspectName + "</yellow><white>.</white>"));

        ConsoleLogger.info(
                "[CheckManager] " + inspector.getName() + " force-ended check (suspect: " + suspectName + ")");
        return true;
    }

    // =========================
    // CLEANUP BY INSPECTOR QUIT
    // =========================
    public static void cleanupByInspector(UUID inspectorId) {
        if (instance == null) return;

        UUID suspectId = instance.activeChecks.remove(inspectorId);
        if (suspectId == null) return;

        instance.suspectToInspector.remove(suspectId);
        instance.inspectorLocations.remove(inspectorId);
        cancelTitleTask(suspectId);

        Player suspect = Bukkit.getPlayer(suspectId);
        if (suspect != null && suspect.isOnline()) {
            suspect.sendTitle(" ", " ", 0, 1, 0);
            unfreezePlayer(suspect);
            suspect.sendMessage("");
            suspect.sendMessage(MessageUtil.parse("<yellow>⚠ <white><bold>CHECK INTERRUPTED!"));
            suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            suspect.sendMessage(MessageUtil.parse("<white>Inspector disconnected."));
            suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
            suspect.sendMessage("");
        }

        ConsoleLogger.info(
                "[CheckManager] Auto-cleaned check (inspector disconnected): " + suspectId);
    }

    // =========================
    // CLEANUP BY SUSPECT QUIT — pauses the check, does not delete the data
    // =========================
    public static void cleanupBySuspect(UUID suspectId) {
        if (instance == null) return;

        // If the suspect is not in a check — ignore
        UUID inspectorId = instance.suspectToInspector.get(suspectId);
        if (inspectorId == null) return;

        // Cancel the title task (the player is offline)
        cancelTitleTask(suspectId);

        // Notify the inspector
        Player inspector = Bukkit.getPlayer(inspectorId);
        if (inspector != null && inspector.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Checked player</white> <yellow>" +
                    (Bukkit.getOfflinePlayer(suspectId).getName() != null ?
                     Bukkit.getOfflinePlayer(suspectId).getName() : suspectId.toString()) +
                    "</yellow> <white>disconnected. Check will resume on rejoin.</white>"));
        }

        ConsoleLogger.info(
                "[CheckManager] Suspect disconnected — check paused: " + suspectId);
    }

    // =========================
    // REJOIN CHECK — restore the check on the suspect's reconnect
    // =========================
    public static void rejoinCheck(Player suspect) {
        if (instance == null) return;

        UUID suspectId = suspect.getUniqueId();
        UUID inspectorId = instance.suspectToInspector.get(suspectId);
        if (inspectorId == null) return;

        UUID storedSuspect = instance.activeChecks.get(inspectorId);
        if (storedSuspect == null || !storedSuspect.equals(suspectId)) return;

        // Freeze the suspect
        freezePlayer(suspect);

        // Restore the title
        String inspectorName = instance.suspectInspectorNames.get(suspectId);
        if (inspectorName == null) {
            Player inspector = Bukkit.getPlayer(inspectorId);
            inspectorName = inspector != null ? inspector.getName() : "Unknown";
        }
        startTitleTask(suspect, inspectorName);

        // Send the messages
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse("<dark_red>❌ <red><bold>VERIFICATION RESUMED"));
        suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
        suspect.sendMessage(MessageUtil.parse("<white>Inspector: <yellow>" + inspectorName));
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse("<gray>Your check was paused while you were offline."));
        suspect.sendMessage(MessageUtil.parse("<gray>It has now resumed. You are still frozen."));
        suspect.sendMessage(MessageUtil.parse("<gray>━━━━━━━━━━━━━━━━━━━━━"));
        suspect.sendMessage("");

        // Notify the inspector
        Player inspector = Bukkit.getPlayer(inspectorId);
        if (inspector != null && inspector.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Player</white> <yellow>" + suspect.getName() +
                    "</yellow> <white>rejoined — check resumed automatically.</white>"));
            // Teleport the inspector to the suspect if they are far
            if (!inspector.getWorld().equals(suspect.getWorld())
                    || inspector.getLocation().distance(suspect.getLocation()) > 50) {
                inspector.teleport(suspect.getLocation());
                inspector.sendMessage(MessageUtil.parse(
                        "<gray>Teleported to suspect's new location.</gray>"));
            }
        }

        ConsoleLogger.info(
                "[CheckManager] Check resumed for suspect: " + suspect.getName()
                + " (inspector: " + inspectorName + ")");
    }

    // =========================
    // QUERY
    // =========================
    public static boolean isBeingChecked(Player player) {
        if (instance == null) return false;
        return instance.suspectToInspector.containsKey(player.getUniqueId());
    }

    public static boolean isInspector(Player player) {
        if (instance == null) return false;
        return instance.activeChecks.containsKey(player.getUniqueId());
    }

    public static Player getInspector(Player suspect) {
        if (instance == null) return null;
        UUID inspectorId = instance.suspectToInspector.get(suspect.getUniqueId());
        if (inspectorId == null) return null;
        return Bukkit.getPlayer(inspectorId);
    }

    public static Player getSuspect(Player inspector) {
        if (instance == null) return null;
        UUID suspectId = instance.activeChecks.get(inspector.getUniqueId());
        if (suspectId == null) return null;
        return Bukkit.getPlayer(suspectId);
    }

    // =========================
    // REPEATING TITLE TASK (every 3 seconds)
    // =========================
    private static void startTitleTask(Player suspect, String inspectorName) {
        if (instance == null) return;
        UUID suspectId = suspect.getUniqueId();
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!suspect.isOnline()) {
                    cancel();
                    return;
                }
                suspect.sendTitle(
                        MessageUtil.legacy("<red>Verification"),
                        MessageUtil.legacy("<white>All instructions are in the chat"),
                        5, 50, 10
                );
            }
        }.runTaskTimer(Main.getInstance(), 0L, 60L).getTaskId(); // every 3 seconds (60 ticks)
        instance.suspectTitleTasks.put(suspectId, taskId);
    }

    private static void cancelTitleTask(UUID suspectId) {
        if (instance == null) return;
        Integer taskId = instance.suspectTitleTasks.remove(suspectId);
        if (taskId != null && taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    // =========================
    // FREEZE / UNFREEZE (similar to AuthAuthenticator)
    // =========================
    private static void freezePlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
    }

    private static void unfreezePlayer(Player player) {
        if (!player.isOnline()) return;
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
    }

    // =========================
    // SHUTDOWN — unfreeze everyone
    // =========================
    public static void shutdown() {
        if (instance == null) return;
        // Cancel all title tasks
        for (UUID suspectId : instance.suspectTitleTasks.keySet()) {
            cancelTitleTask(suspectId);
        }
        for (UUID suspectId : instance.suspectToInspector.keySet()) {
            Player suspect = Bukkit.getPlayer(suspectId);
            if (suspect != null && suspect.isOnline()) {
                suspect.sendTitle(" ", " ", 0, 1, 0);
                unfreezePlayer(suspect);
                suspect.sendMessage(MessageUtil.parse("<yellow>⚠ Check interrupted (plugin reload)."));
            }
        }
        instance.activeChecks.clear();
        instance.suspectToInspector.clear();
        instance.inspectorLocations.clear();
        instance.suspectTitleTasks.clear();
        instance.suspectInspectorNames.clear();
        instance = null;
    }
}
