package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanish system: completely hides a player from others.
 * <p>
 * Features:
 * <ul>
 *   <li>Hidden from tab-complete (AsyncTabCompleteEvent)</li>
 *   <li>Join/quit messages cancelled</li>
 *   <li>Visual invisibility (hidePlayer)</li>
 *   <li>Sounds disabled (setSilent)</li>
 *   <li>Hidden from the tab list (ClientboundPlayerInfoRemovePacket)</li>
 *   <li>/list command filtering</li>
 *   <li>Works with offline players (state persists)</li>
 * </ul>
 */
public class VanishManager implements Listener {

    private static VanishManager instance;

    // UUIDs of vanished players (ConcurrentHashMap for thread safety)
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new VanishManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        reloadConfig();
        ConsoleLogger.info("[Vanish] Manager initialized. " + vanishedPlayers.size() + " vanished player(s) loaded.");
    }

    public static void reloadConfig() {
        loadVanishedPlayers();
    }

    public static VanishManager getInstance() {
        return instance;
    }

    // =========================
    // PERSISTENCE (DB → vanished_players)
    // =========================
    private static void loadVanishedPlayers() {
        vanishedPlayers.clear();

        // 1. Migrate old data from config.yml, if any
        migrateFromConfig();

        // 2. Load from the DB
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT uuid FROM vanished_players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    vanishedPlayers.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    ConsoleLogger.warn("[Vanish] Invalid UUID in database: " + rs.getString("uuid"));
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Vanish] Failed to load vanished players from DB: " + e.getMessage());
        }
    }

    /** Migrates vanished_players from config.yml into the DB (once, on the first start after the update). */
    private static void migrateFromConfig() {
        List<String> uuidStrings = Main.getInstance().getConfig().getStringList("vanish.vanished_players");
        if (uuidStrings == null || uuidStrings.isEmpty()) return;

        // Check — is the UUID already in the DB? If so, the migration already ran.
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try (PreparedStatement check = con.prepareStatement(
                "SELECT COUNT(*) FROM vanished_players");
             ResultSet rs = check.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                // The DB already has data — clean the config and exit
                clearConfigSection();
                return;
            }
        } catch (Exception ignored) {}

        // Copy from config into the DB
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT OR IGNORE INTO vanished_players (uuid) VALUES (?)")) {
            for (String s : uuidStrings) {
                try {
                    UUID.fromString(s); // validation
                    ps.setString(1, s);
                    ps.executeUpdate();
                } catch (IllegalArgumentException ignored) {
                    ConsoleLogger.warn("[Vanish] Skipping invalid UUID in config: " + s);
                }
            }
            ConsoleLogger.info("[Vanish] Migrated " + uuidStrings.size() + " vanished player(s) from config.yml to database.");
        } catch (Exception e) {
            ConsoleLogger.warn("[Vanish] Migration failed: " + e.getMessage());
        }

        // Clean the config of old data
        clearConfigSection();
    }

    /** Removes the outdated vanish.vanished_players section from config.yml. */
    private static void clearConfigSection() {
        Main.getInstance().getConfig().set("vanish.vanished_players", null);
        Main.getInstance().saveConfig();
    }

    public static void saveVanishedPlayers() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try {
            con.setAutoCommit(false);

            try (PreparedStatement del = con.prepareStatement("DELETE FROM vanished_players")) {
                del.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO vanished_players (uuid) VALUES (?)")) {
                for (UUID uuid : vanishedPlayers) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            }

            con.commit();
            con.setAutoCommit(true);
        } catch (Exception e) {
            try { con.rollback(); } catch (Exception ignored) {}
            try { con.setAutoCommit(true); } catch (Exception ignored) {}
            ConsoleLogger.warn("[Vanish] Failed to save vanished players to DB: " + e.getMessage());
        }
    }

    // =========================
    // VANISH TOGGLE / SET
    // =========================
    public static boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public static void setVanished(OfflinePlayer offlinePlayer, boolean vanished) {
        UUID uuid = offlinePlayer.getUniqueId();
        if (vanished) {
            if (vanishedPlayers.add(uuid)) {
                Player online = offlinePlayer.getPlayer();
                if (online != null && online.isOnline()) {
                    applyVanish(online);
                }
            }
        } else {
            if (vanishedPlayers.remove(uuid)) {
                Player online = offlinePlayer.getPlayer();
                if (online != null && online.isOnline()) {
                    removeVanish(online);
                }
            }
        }
        saveVanishedPlayers();
    }

    public static void toggleVanish(OfflinePlayer offlinePlayer) {
        setVanished(offlinePlayer, !isVanished(offlinePlayer.getUniqueId()));
    }

    // =========================
    // PACKET HELPERS (tab list)
    // =========================

    /**
     * Sends ClientboundPlayerInfoRemovePacket to all online players
     * to hide the target from their tab list (TAB key).
     */
    private static void removeFromTabList(Player target) {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(
                List.of(target.getUniqueId())
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(target.getUniqueId())) continue;
            try {
                ((CraftPlayer) online).getHandle().connection.send(packet);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Sends ClientboundPlayerInfoUpdatePacket (ADD_PLAYER) to all online players
     * to return the target to their tab list.
     */
    private static void addToTabList(Player target) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) target).getHandle();
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(
                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
                    ),
                    List.of(serverPlayer)
            );
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(target.getUniqueId())) continue;
                try {
                    ((CraftPlayer) online).getHandle().connection.send(packet);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Removes all vanished players from the given player's tab list.
     */
    private static void removeVanishedPlayersFromTabList(Player viewer) {
        if (vanishedPlayers.isEmpty()) return;
        List<UUID> uuids = new ArrayList<>();
        for (UUID vanishedUuid : vanishedPlayers) {
            if (vanishedUuid.equals(viewer.getUniqueId())) continue;
            uuids.add(vanishedUuid);
        }
        if (uuids.isEmpty()) return;
        try {
            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(uuids);
            ((CraftPlayer) viewer).getHandle().connection.send(packet);
        } catch (Exception ignored) {}
    }

    // =========================
    // APPLY / REMOVE VANISH
    // =========================
    private static void applyVanish(Player player) {
        // Hide from all online players (entity)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            online.hidePlayer(Main.getInstance(), player);
        }
        // Remove from the tab list (via packet)
        removeFromTabList(player);
        // Disable sounds
        player.setSilent(true);
    }

    private static void removeVanish(Player player) {
        // Show to all online players (entity)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            online.showPlayer(Main.getInstance(), player);
        }
        // Return to the tab list (via packet)
        addToTabList(player);
        // Re-enable sounds
        player.setSilent(false);
    }

    /**
     * Apply vanish to an online player that was marked as vanished while offline.
     * Also hides all currently vanished players from this newly joined player.
     */
    private static void applyVanishOnJoin(Player player) {
        // Hide this vanished player from all others
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            online.hidePlayer(Main.getInstance(), player);
        }
        // Remove from all other players' tab lists
        removeFromTabList(player);
        player.setSilent(true);

        // Also hide all other vanished players from this player
        hideVanishedPlayersFrom(player);
    }

    /**
     * Hide all currently vanished players from the given player.
     */
    private static void hideVanishedPlayersFrom(Player player) {
        for (UUID vanishedUuid : vanishedPlayers) {
            if (vanishedUuid.equals(player.getUniqueId())) continue;
            Player vanishedOnline = Bukkit.getPlayer(vanishedUuid);
            if (vanishedOnline != null && vanishedOnline.isOnline()) {
                // Hide the entity
                player.hidePlayer(Main.getInstance(), vanishedOnline);
            }
        }
        // Remove the vanished from the player's tab list
        removeVanishedPlayersFromTabList(player);
    }

    // =========================
    // LISTENERS
    // =========================

    /**
     * PlayerJoin — apply vanish on join, cancel the join message.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isVanished(uuid)) {
            // Cancel the join message
            event.setJoinMessage(null);

            // Apply vanish (hide from others, disable sounds)
            // 1-tick delay so hiding applies after the spawn
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    applyVanishOnJoin(player);
                }
            }, 1L);
        } else {
            // The player isn't vanished, but must hide all vanished players from them
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    hideVanishedPlayersFrom(player);
                }
            }, 1L);
        }
    }

    /**
     * PlayerQuit — cancel the quit message for the vanished.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer().getUniqueId())) {
            event.setQuitMessage(null);
        }
    }

    /**
     * PlayerChangedWorldEvent — on world change a vanished player may
     * reappear in other players' tabs. Re-apply the tab removal.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isVanished(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline() && isVanished(player.getUniqueId())) {
                    // hidePlayer is already cross-world, but the tab packet must be sent again
                    removeFromTabList(player);
                }
            }, 2L);
        }
    }

    /**
     * PlayerRespawnEvent — on respawn the player may reappear in the tab.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isVanished(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline() && isVanished(player.getUniqueId())) {
                    removeFromTabList(player);
                }
            }, 2L);
        }
    }

    /**
     * AsyncTabComplete — filter vanished players' names from autocomplete.
     * Works for TAB in chat and in commands.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncTabComplete(com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event) {
        if (vanishedPlayers.isEmpty()) return;

        // Only filter if the sender doesn't have vanish permission
        if (event.getSender().hasPermission("ui.command.vanish")) return;

        // Pre-compute set of vanished player names (lowercased) — O(1) lookup per completion
        Set<String> vanishedNames = new HashSet<>();
        for (UUID uuid : vanishedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                vanishedNames.add(p.getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (vanishedNames.isEmpty()) return;

        List<String> filtered = new ArrayList<>();
        for (String completion : event.getCompletions()) {
            if (!vanishedNames.contains(completion.toLowerCase(java.util.Locale.ROOT))) {
                filtered.add(completion);
            }
        }
        event.setCompletions(filtered);
    }

    /**
     * PlayerCommandPreprocessEvent — intercept /minecraft:list,
     * only for the case where the command is called via the namespace.
     * A plain /list is now intercepted via CommandRegistrar (VanishListCommand).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (vanishedPlayers.isEmpty()) return;

        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();

        // Only /minecraft:list — a plain /list is already overridden via CommandMap
        if (!message.equals("/minecraft:list") && !message.startsWith("/minecraft:list ")) return;

        Player sender = event.getPlayer();
        boolean canSeeVanished = sender.hasPermission("ui.command.vanish");

        List<String> visibleNames = new ArrayList<>();
        int vanishedCount = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isVanished(online.getUniqueId())) {
                vanishedCount++;
                if (canSeeVanished) {
                    visibleNames.add("<gray>" + online.getDisplayName() + "<reset>");
                }
            } else {
                visibleNames.add("<white>" + online.getDisplayName() + "<reset>");
            }
        }

        int totalOnline = Bukkit.getOnlinePlayers().size();
        int visibleCount = totalOnline - (canSeeVanished ? 0 : vanishedCount);

        event.setCancelled(true);
        sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<gray>На сервере <white>" + visibleCount + "<gray>/<white>" + Bukkit.getMaxPlayers() + " <gray>игроков:"));
        sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse(String.join("<gray>, ", visibleNames)));
        if (vanishedCount > 0 && canSeeVanished) {
            sender.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<gray>(" + vanishedCount + " в ванише)"));
        }
    }

    // =========================
    // GETTERS
    // =========================
    public static Set<UUID> getVanishedPlayers() {
        return Collections.unmodifiableSet(vanishedPlayers);
    }

    public static int getVanishedCount() {
        return vanishedPlayers.size();
    }
}
