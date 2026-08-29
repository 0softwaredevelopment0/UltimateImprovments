package com.ultimateimprovments.mechanics.security.check;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.api.CheckBridge;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-cheat check manager.
 * <p>
 * Stores pairs (inspector → suspect). On the inspector's quit the check is
 * ended automatically; on the suspect's quit it is paused and resumed on
 * reconnect. The checked player is frozen (cannot move/interact/etc.).
 * <p>
 * Active checks are persisted to the DB ({@code active_checks}) so they survive
 * a server restart and resume when the players log back in.
 */
public class CheckManager {

    private static final long PERSISTED_CHECK_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000; // 7 days

    private static CheckManager instance;

    // inspector UUID → suspect UUID
    private final Map<UUID, UUID> activeChecks = new ConcurrentHashMap<>();

    // suspect UUID → inspector UUID (reverse index)
    private final Map<UUID, UUID> suspectToInspector = new ConcurrentHashMap<>();

    // inspector UUID → original location (to return to after the check)
    private final Map<UUID, Location> inspectorLocations = new ConcurrentHashMap<>();

    // suspect UUID → saved pre-freeze state (restored when the check ends)
    private final Map<UUID, PlayerState> frozenStates = new ConcurrentHashMap<>();

    // suspect UUID → inspector name (to restore the title on reconnect/restart)
    private final Map<UUID, String> suspectInspectorNames = new ConcurrentHashMap<>();

    private CheckManager() {}

    public static void init() {
        instance = new CheckManager();
        instance.loadPersistedChecks();
        // Bridge so the chat module (UI-Chat) can route the [C] check channel
        // to the inspector without depending on UI-Other.
        CheckBridge.register(new CheckBridge() {
            @Override
            public Player getInspector(Player suspect) {
                return CheckManager.getInspector(suspect);
            }

            @Override
            public boolean isBeingChecked(Player player) {
                return CheckManager.isBeingChecked(player);
            }
        });
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
            inspector.sendMessage(MessageUtil.parse(msg("start.cannot_check_self", "<red>❌ You cannot check yourself!</red>")));
            return false;
        }
        if (instance.activeChecks.containsKey(inspector.getUniqueId())) {
            inspector.sendMessage(MessageUtil.parse(msg("start.already_checking",
                    "<red>❌ You already have an active check!</red>")));
            return false;
        }
        if (instance.suspectToInspector.containsKey(suspect.getUniqueId())) {
            inspector.sendMessage(MessageUtil.parse(msg("start.already_checked",
                    "<red>❌ This player is already being checked!</red>")));
            return false;
        }

        UUID inspectorId = inspector.getUniqueId();
        UUID suspectId = suspect.getUniqueId();

        instance.activeChecks.put(inspectorId, suspectId);
        instance.suspectToInspector.put(suspectId, inspectorId);

        // Save inspector's location and teleport to suspect
        instance.inspectorLocations.put(inspectorId, inspector.getLocation());
        inspector.teleport(suspect.getLocation());

        // Freeze suspect (saving the pre-freeze state)
        instance.frozenStates.put(suspectId, PlayerState.capture(suspect));
        PlayerState.freeze(suspect);

        // Save inspector name for rejoin/restart
        instance.suspectInspectorNames.put(suspectId, inspector.getName());

        // Persist the check so it survives a restart
        persistCheck(inspector, suspect);

        // Send the persistent title
        sendCheckTitle(suspect, inspector.getName());

        // Chat instructions
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse(msg("suspect.title", "<dark_red>❌ <red>Verification")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.inspector", "<white>Inspector: <yellow>%inspector%")
                .replace("%inspector%", inspector.getName())));
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse(msg("suspect.no_move", "<gray>You cannot move or interact with anything")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.until_done", "<gray>until the check is complete.")));
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse(msg("suspect.mod_info", "<gray>If you have any prohibited modifications —")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.disable_now", "<gray>disable them now. It is in your best interest.")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
        suspect.sendMessage("");

        // Notify inspector
        inspector.sendMessage(MessageUtil.parse(msg("inspector.started", "<green>✔</green> <white>Check started for</white> <yellow>%suspect%</yellow><white>.</white>")
                .replace("%suspect%", suspect.getName())));
        inspector.sendMessage(MessageUtil.parse(msg("inspector.howto", "<gray>Use </gray><white>/ui uncheck %suspect%</white><gray> when finished.</gray>")
                .replace("%suspect%", suspect.getName())));

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
            inspector.sendMessage(MessageUtil.parse(msg("end.not_checking", "<red>❌ You are not checking this player!</red>")));
            return false;
        }

        instance.activeChecks.remove(inspectorId);
        instance.suspectToInspector.remove(suspectId);
        instance.suspectInspectorNames.remove(suspectId);
        removePersistedCheck(inspectorId);

        // Teleport inspector back to original location
        Location originalLoc = instance.inspectorLocations.remove(inspectorId);
        if (originalLoc != null && inspector.isOnline()) {
            inspector.teleport(originalLoc);
            inspector.sendMessage(MessageUtil.parse(msg("inspector.returned", "<gray>You have been teleported back to your original location.</gray>")));
        }

        // Clear title, unfreeze, complete messages
        clearCheckTitle(suspect);
        if (suspect.isOnline()) {
            PlayerState.restore(suspect, instance.frozenStates.remove(suspectId));
            suspect.sendMessage("");
            suspect.sendMessage(MessageUtil.parse(msg("suspect.completed", "<green>✔ <white><bold>CHECK COMPLETE!")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.inspector", "<white>Inspector: <yellow>%inspector%")
                    .replace("%inspector%", inspector.getName())));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
            suspect.sendMessage("");
        }

        inspector.sendMessage(MessageUtil.parse(msg("inspector.ended", "<green>✔</green> <white>Check ended for</white> <yellow>%suspect%</yellow><white>.</white>")
                .replace("%suspect%", suspect.getName())));

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
            inspector.sendMessage(MessageUtil.parse(msg("end.no_active", "<red>❌ You don't have an active check!</red>")));
            return false;
        }

        String suspectName = Bukkit.getOfflinePlayer(suspectId).getName();
        if (suspectName == null) suspectName = suspectId.toString();

        instance.activeChecks.remove(inspectorId);
        instance.suspectToInspector.remove(suspectId);
        instance.suspectInspectorNames.remove(suspectId);
        removePersistedCheck(inspectorId);

        // Teleport inspector back
        Location originalLoc = instance.inspectorLocations.remove(inspectorId);
        if (originalLoc != null) {
            inspector.teleport(originalLoc);
            inspector.sendMessage(MessageUtil.parse(msg("inspector.returned", "<gray>You have been teleported back to your original location.</gray>")));
        }

        // If the suspect is online — unfreeze
        Player suspect = Bukkit.getPlayer(suspectId);
        clearCheckTitle(suspect);
        if (suspect != null && suspect.isOnline()) {
            PlayerState.restore(suspect, instance.frozenStates.remove(suspectId));
            suspect.sendMessage("");
            suspect.sendMessage(MessageUtil.parse(msg("suspect.completed", "<green>✔ <white><bold>CHECK COMPLETE!")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.inspector", "<white>Inspector: <yellow>%inspector%")
                    .replace("%inspector%", inspector.getName())));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
            suspect.sendMessage("");
        }

        inspector.sendMessage(MessageUtil.parse(msg("inspector.ended", "<green>✔</green> <white>Check ended for</white> <yellow>%suspect%</yellow><white>.</white>")
                .replace("%suspect%", suspectName)));

        ConsoleLogger.info(
                "[CheckManager] " + inspector.getName() + " force-ended check (suspect: " + suspectName + ")");
        return true;
    }

    // =========================
    // CLEANUP BY INSPECTOR QUIT — the check is ended
    // =========================
    public static void cleanupByInspector(UUID inspectorId) {
        if (instance == null) return;

        UUID suspectId = instance.activeChecks.remove(inspectorId);
        if (suspectId == null) return;

        instance.suspectToInspector.remove(suspectId);
        instance.inspectorLocations.remove(inspectorId);
        instance.suspectInspectorNames.remove(suspectId); // fix leak
        removePersistedCheck(inspectorId);

        Player suspect = Bukkit.getPlayer(suspectId);
        clearCheckTitle(suspect);
        if (suspect != null && suspect.isOnline()) {
            PlayerState.restore(suspect, instance.frozenStates.remove(suspectId));
            suspect.sendMessage("");
            suspect.sendMessage(MessageUtil.parse(msg("suspect.interrupted", "<yellow>⚠ <white><bold>CHECK INTERRUPTED!")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.inspector_left", "<white>Inspector disconnected.")));
            suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
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

        // Clear the title (the player is offline)
        Player suspect = Bukkit.getPlayer(suspectId);
        clearCheckTitle(suspect);

        // Freeze state stays in memory + DB so it can resume on rejoin.
        // Notify the inspector
        Player inspector = Bukkit.getPlayer(inspectorId);
        if (inspector != null && inspector.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(msg("inspector.suspect_left",
                    "<yellow>⚠</yellow> <white>Checked player</white> <yellow>%suspect%</yellow> <white>disconnected. Check will resume on rejoin.</white>")
                    .replace("%suspect%", Bukkit.getOfflinePlayer(suspectId).getName() != null
                            ? Bukkit.getOfflinePlayer(suspectId).getName() : suspectId.toString())));
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

        // Freeze the suspect (save the fresh state)
        instance.frozenStates.put(suspectId, PlayerState.capture(suspect));
        PlayerState.freeze(suspect);

        // Restore the title
        String inspectorName = instance.suspectInspectorNames.get(suspectId);
        if (inspectorName == null) {
            Player inspector = Bukkit.getPlayer(inspectorId);
            inspectorName = inspector != null ? inspector.getName() : msg("suspect.unknown_inspector", "Unknown");
        }
        sendCheckTitle(suspect, inspectorName);

        // Send the messages
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse(msg("suspect.resumed", "<dark_red>❌ <red><bold>VERIFICATION RESUMED")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.inspector", "<white>Inspector: <yellow>%inspector%")
                .replace("%inspector%", inspectorName)));
        suspect.sendMessage("");
        suspect.sendMessage(MessageUtil.parse(msg("suspect.paused_note", "<gray>Your check was paused while you were offline.")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.frozen_note", "<gray>It has now resumed. You are still frozen.")));
        suspect.sendMessage(MessageUtil.parse(msg("suspect.separator", "<gray>━━━━━━━━━━━━━━━━━━━━━")));
        suspect.sendMessage("");

        // Notify the inspector
        Player inspector = Bukkit.getPlayer(inspectorId);
        if (inspector != null && inspector.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(msg("inspector.rejoined", "<green>✔</green> <white>Player</white> <yellow>%suspect%</yellow> <white>rejoined — check resumed automatically.</white>")
                    .replace("%suspect%", suspect.getName())));
            // Teleport the inspector to the suspect if they are far
            if (!inspector.getWorld().equals(suspect.getWorld())
                    || inspector.getLocation().distance(suspect.getLocation()) > 50) {
                inspector.teleport(suspect.getLocation());
                inspector.sendMessage(MessageUtil.parse(msg("inspector.teleported", "<gray>Teleported to suspect's new location.</gray>")));
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
    // PERSISTENT TITLE (fade-in 0, stay MAX, fade-out 0 — no flicker, no packet spam)
    // =========================
    private static void sendCheckTitle(Player suspect, String inspectorName) {
        // Delayed by 1 tick so the client applies it after (re)join.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!suspect.isOnline()) return;
                suspect.sendTitle(
                        MessageUtil.legacy(msg("title.title", "<red>Verification")),
                        MessageUtil.legacy(msg("title.subtitle", "<white>All instructions are in the chat")),
                        0, Integer.MAX_VALUE, 0
                );
            }
        }.runTask(Main.getInstance());
    }

    private static void clearCheckTitle(Player suspect) {
        if (suspect == null || !suspect.isOnline()) return;
        suspect.resetTitle();
    }

    // =========================
    // CONFIG MESSAGES (English defaults)
    // =========================
    private static String msg(String path, String enDefault) {
        return MessagesManager.getString("check." + path, enDefault);
    }

    // =========================
    // DATABASE PERSISTENCE — checks survive a server restart
    // =========================
    private static void persistCheck(Player inspector, Player suspect) {
        Location loc = inspector.getLocation();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR REPLACE INTO active_checks (inspector_uuid, inspector_name, suspect_uuid, suspect_name, " +
                     "inspector_world, inspector_x, inspector_y, inspector_z, inspector_yaw, inspector_pitch, started_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            st.setString(1, inspector.getUniqueId().toString());
            st.setString(2, inspector.getName());
            st.setString(3, suspect.getUniqueId().toString());
            st.setString(4, suspect.getName());
            st.setString(5, loc.getWorld() != null ? loc.getWorld().getUID().toString() : "");
            st.setInt(6, loc.getBlockX());
            st.setInt(7, loc.getBlockY());
            st.setInt(8, loc.getBlockZ());
            st.setFloat(9, loc.getYaw());
            st.setFloat(10, loc.getPitch());
            st.setLong(11, System.currentTimeMillis());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[CheckManager] Failed to persist check: " + e.getMessage());
        }
    }

    private static void removePersistedCheck(UUID inspectorId) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM active_checks WHERE inspector_uuid = ?")) {
            st.setString(1, inspectorId.toString());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[CheckManager] Failed to remove persisted check: " + e.getMessage());
        }
    }

    /** Loads persisted checks into memory at startup. Stale checks older than a week are dropped. */
    private void loadPersistedChecks() {
        long now = System.currentTimeMillis();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT inspector_uuid, inspector_name, suspect_uuid, suspect_name, started_at " +
                     "FROM active_checks");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                long startedAt = rs.getLong("started_at");
                if (now - startedAt > PERSISTED_CHECK_MAX_AGE_MS) {
                    UUID inspectorId = UUID.fromString(rs.getString("inspector_uuid"));
                    removePersistedCheck(inspectorId);
                    continue;
                }
                UUID inspectorId = UUID.fromString(rs.getString("inspector_uuid"));
                UUID suspectId = UUID.fromString(rs.getString("suspect_uuid"));
                activeChecks.put(inspectorId, suspectId);
                suspectToInspector.put(suspectId, inspectorId);
                String inspectorName = rs.getString("inspector_name");
                if (inspectorName == null || inspectorName.isEmpty()) {
                    inspectorName = Bukkit.getOfflinePlayer(inspectorId).getName();
                    if (inspectorName == null) inspectorName = "Unknown";
                }
                suspectInspectorNames.put(suspectId, inspectorName);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[CheckManager] Failed to load persisted checks: " + e.getMessage());
        }
    }

    // =========================
    // SHUTDOWN — unfreeze everyone, but KEEP the DB rows so checks resume after restart
    // =========================
    public static void shutdown() {
        if (instance == null) return;
        for (Map.Entry<UUID, UUID> entry : instance.activeChecks.entrySet()) {
            UUID suspectId = entry.getValue();
            Player suspect = Bukkit.getPlayer(suspectId);
            clearCheckTitle(suspect);
            if (suspect != null && suspect.isOnline()) {
                PlayerState.restore(suspect, instance.frozenStates.get(suspectId));
            }
        }
        instance.activeChecks.clear();
        instance.suspectToInspector.clear();
        instance.inspectorLocations.clear();
        instance.frozenStates.clear();
        instance.suspectInspectorNames.clear();
        instance = null;
        // Cleanup stale binds for the chat bridge on the next init.
        try {
            CheckBridge.register(null);
        } catch (Throwable ignored) { }
    }
}