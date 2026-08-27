package com.ultimateimprovments.display;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlaceholderResolver;
import com.ultimateimprovments.mechanics.features.player.VanishManager;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/** * Manages the custom tab list:
 * <ul>
 *   <li>Header — text above the player list (MiniMessage + placeholders)</li>
 *   <li>Footer — text below the player list (MiniMessage + placeholders)</li>
 *   <li>PlayerList name — prefix/suffix before/after the name (ping, PAPI, etc.)</li> * <li>Hide spectators — hides spectator players from the tab</li>
 *   <li>Sort mode — sorts names in the tab (A-Z, Z-A, LuckPerms, OP)</li>
 * </ul>
 */
public class TabManager extends BukkitRunnable implements Listener {

    private static TabManager instance;
    private static boolean listenersRegistered = false;
    private boolean enabled;
    private List<String> headerLines;
    private List<String> footerLines;
    private boolean objectiveEnabled;
    private String objectivePrefix;
    private String objectiveSuffix;
    private String objectiveFormat;
    private boolean hideSpectators;
    private int intervalTicks;
    private int playerListIntervalTicks;
    private int playerListTickCounter;
    private SortMode sortMode;

    public enum SortMode {
        NONE,       // no sorting
        A_Z,        // alphabetical A-Z
        Z_A,        // alphabetical Z-A
        LUCKPERMS,  // by LuckPerms primary group
        OP          // OP players first, then non-OP
    }

    /**
     * Initializes TabManager. If it was already running — clean up the previous one.
     */
    public static void init() {
        // Clean up the previous BukkitRunnable (but NOT cancelTasks — that kills ALL plugin tasks)
        if (instance != null) {
            try { instance.cancel(); } catch (Exception ignored) {}
        }

        instance = new TabManager();

        // Register the Listener ONLY once for the whole plugin lifetime
        if (!listenersRegistered) {
            Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
            listenersRegistered = true;
        }

        instance.reloadConfig();

        // Hide already-online spectators on start/reload
        if (instance.hideSpectators) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) {
                        removeSpectatorFromTabList(p);
                    }
                }
            }, 1L);
        }

        if (instance.enabled) {
            // A BukkitRunnable CANNOT be reused after cancel() — always a new instance
            instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
        }
    }

    public static void shutdown() {
        if (instance != null) {
            try { instance.cancel(); } catch (Exception ignored) {}
            instance = null;
        }
    }

    /**
     * Resets the listener registration flag (called from Main.onDisable()).
     * Needed so listeners get re-registered on the next plugin start (after /reload).
     */
    public static void resetListenerState() {
        listenersRegistered = false;
    }

    /**
     * Config reload. A BukkitRunnable can't be reused after cancel(),
     * so on reload we create a new instance via init().
     */
    public static void reload() {
        // Just re-initialize — a new BukkitRunnable is created
        init();
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();
        this.enabled = config.getBoolean("tab.enabled", false);
        this.headerLines = config.getStringList("tab.header");
        this.footerLines = config.getStringList("tab.footer");
        this.objectiveEnabled = config.getBoolean("tab.player_list.objective_enabled", false);
        this.objectivePrefix = config.getString("tab.player_list.objective_prefix", "");
        this.objectiveSuffix = config.getString("tab.player_list.objective_suffix", "");
        this.objectiveFormat = config.getString("tab.player_list.format", "");
        this.hideSpectators = config.getBoolean("tab.hide_spectators", false);
        this.intervalTicks = Math.max(10, config.getInt("tab.update_interval_ticks", 20));
        this.playerListIntervalTicks = config.getInt("tab.player_list.update_interval_ticks", 0);
        // If 0 — updates at the same frequency as the header/footer
        if (playerListIntervalTicks <= 0) {
            playerListIntervalTicks = intervalTicks;
        } else {
            playerListIntervalTicks = Math.max(5, playerListIntervalTicks);
        }
        this.playerListTickCounter = 0;

        // Sort mode
        String sortStr = config.getString("tab.sort.mode", "none").toUpperCase().replace("-", "_");
        try {
            this.sortMode = SortMode.valueOf(sortStr);
        } catch (IllegalArgumentException e) {
            this.sortMode = SortMode.NONE;
        }
    }

    // ── Spectator hide / show ──

    /**
     * Sends ClientboundPlayerInfoRemovePacket to all online players
     * to hide the spectator from their tab list.
     */
    private static void removeSpectatorFromTabList(Player spectator) {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(
                List.of(spectator.getUniqueId())
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(spectator.getUniqueId())) continue;
            try {
                ((CraftPlayer) online).getHandle().connection.send(packet);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Sends ClientboundPlayerInfoRemovePacket with a batch of all vanished UUIDs
     * to all online players (except the vanished themselves).
     */
    private static void batchRemoveVanishedFromTabList(List<UUID> vanishedUuids) {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(vanishedUuids);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (vanishedUuids.contains(online.getUniqueId())) continue;
            try {
                ((CraftPlayer) online).getHandle().connection.send(packet);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Returns the spectator to all online players' tab lists.
     */
    private static void addSpectatorToTabList(Player spectator) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) spectator).getHandle();
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
                if (online.getUniqueId().equals(spectator.getUniqueId())) continue;
                try {
                    ((CraftPlayer) online).getHandle().connection.send(packet);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Hides all current spectators from the given player's tab.
     */
    private static void hideCurrentSpectatorsFrom(Player viewer) {
        List<UUID> spectatorUuids = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) continue;
            if (online.getGameMode() == GameMode.SPECTATOR) {
                spectatorUuids.add(online.getUniqueId());
            }
        }
        if (spectatorUuids.isEmpty()) return;
        try {
            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(spectatorUuids);
            ((CraftPlayer) viewer).getHandle().connection.send(packet);
        } catch (Exception ignored) {}
    }

    // ── Listeners ──
    // IMPORTANT: all event handlers use TabManager.getInstance() instead of this,
    // because after /ui reload Bukkit still holds a reference to the OLD instance
    // (listenersRegistered=true → registerEvents() isn't called again).
    // If handlers used this.hideSpectators, they'd read stale values from the
    // old object instead of the current one.

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        TabManager tab = instance;
        if (tab == null || !tab.hideSpectators) return;

        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();
        GameMode oldMode = player.getGameMode();

        // Delay 1 tick so the game mode is actually applied
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;

            if (newMode == GameMode.SPECTATOR && oldMode != GameMode.SPECTATOR) {
                // Switched to spectator — hide from the tab
                removeSpectatorFromTabList(player);
            } else if (oldMode == GameMode.SPECTATOR && newMode != GameMode.SPECTATOR) {
                // Left spectator — return to the tab
                addSpectatorToTabList(player);
            }
        }, 1L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        TabManager tab = instance;
        if (tab == null || !tab.hideSpectators) return;

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;

            // If the new player is a spectator — hide them from everyone
            if (player.getGameMode() == GameMode.SPECTATOR) {
                removeSpectatorFromTabList(player);
            }

            // Hide all current spectators from the new player
            hideCurrentSpectatorsFrom(player);
        }, 1L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        TabManager tab = instance;
        if (tab == null || !tab.hideSpectators) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // On world change the client re-adds to the tab — hide again
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                    removeSpectatorFromTabList(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        TabManager tab = instance;
        if (tab == null || !tab.hideSpectators) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // On respawn the client re-adds to the tab — hide again
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                    removeSpectatorFromTabList(player);
                }
            }, 2L);
        }
    }

    @Override
    public void run() {
        if (!enabled) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        sortPlayers(players);

        // playerListTickCounter tracks REAL ticks, not run() calls
        // run() is called every intervalTicks ticks
        playerListTickCounter += intervalTicks;
        boolean updatePlayerList = (playerListTickCounter >= playerListIntervalTicks);
        if (updatePlayerList) {
            playerListTickCounter = 0;
        }

        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;

            // Per-player header/footer (with player-specific placeholders)
            // Updated every tick (every intervalTicks)
            Component playerHeader = buildComponent(headerLines, player);
            Component playerFooter = buildComponent(footerLines, player);
            player.sendPlayerListHeaderAndFooter(playerHeader, playerFooter);

            // Player list name — updated on a separate interval
            if (objectiveEnabled && updatePlayerList) {
                if (!objectiveFormat.isEmpty()) {
                    // Custom format — full control: %luckperms_prefix%%player_name%...
                    String resolved = PlaceholderResolver.resolve(objectiveFormat, player);
                    player.playerListName(MessageUtil.parse(resolved));
                } else {
                    // Old logic: prefix + name + suffix
                    String prefix = PlaceholderResolver.resolve(objectivePrefix, player);
                    String suffix = PlaceholderResolver.resolve(objectiveSuffix, player);

                    Component prefixComp = prefix.isEmpty() ? Component.empty() : MessageUtil.parse(prefix);
                    Component nameComp = Component.text(player.getName());
                    Component suffixComp = suffix.isEmpty() ? Component.empty() : MessageUtil.parse(suffix);

                    player.playerListName(prefixComp.append(nameComp).append(suffixComp));
                }
            }
        }

        // Apply sorting via playerListOrder (Paper API)
        // Sorting updates every tick because players join/leave
        if (sortMode != SortMode.NONE) {
            applySortOrder(players);
        }

        // Re-hide spectators — setPlayerListOrder() and other operations may
        // make the client re-add a hidden spectator to the tab
        if (hideSpectators) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null && p.getGameMode() == GameMode.SPECTATOR) {
                    removeSpectatorFromTabList(p);
                }
            }
        }

        // Re-hide vanished players — same thing: any tab operations may
        // make the client re-add the vanished to the tab list
        List<UUID> vanishedUuids = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null && VanishManager.isVanished(p.getUniqueId())) {
                vanishedUuids.add(p.getUniqueId());
            }
        }
        if (!vanishedUuids.isEmpty()) {
            batchRemoveVanishedFromTabList(vanishedUuids);
        }
    }

    /**
     * Builds a Component from a list of MiniMessage strings + placeholders.
     */
    private Component buildComponent(List<String> lines, Player player) {
        if (lines == null || lines.isEmpty()) return Component.empty();

        String joined = String.join("\n", lines);
        String resolved = PlaceholderResolver.resolve(joined, player);
        return MessageUtil.parse(resolved);
    }

    // =========================
    // TAB SORTING
    // =========================

    /**
     * Sorts the player list according to sortMode.
     */
    private void sortPlayers(List<Player> players) {
        switch (sortMode) {
            case A_Z -> players.sort(Comparator.comparing(Player::getName));
            case Z_A -> players.sort(Comparator.comparing(Player::getName).reversed());
            case OP -> players.sort((a, b) -> {
                boolean aOp = a.isOp();
                boolean bOp = b.isOp();
                if (aOp == bOp) return a.getName().compareToIgnoreCase(b.getName());
                return aOp ? -1 : 1;
            });
            case LUCKPERMS -> {
                // Sort by LuckPerms primary group (weight), fallback to name
                players.sort((a, b) -> {
                    int aWeight = getLuckPermsWeight(a);
                    int bWeight = getLuckPermsWeight(b);
                    if (aWeight != bWeight) return Integer.compare(bWeight, aWeight); // higher weight first
                    return a.getName().compareToIgnoreCase(b.getName());
                });
            }
        }
    }

    /**
     * Applies the sort order using Paper's playerListOrder API.
     * Uses the index in the sorted list as the order value.
     */
    private void applySortOrder(List<Player> sortedPlayers) {
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            try {
                // Paper API: setPlayerListOrder(int) controls tab list position
                player.setPlayerListOrder(i);
            } catch (Exception ignored) {
                // Fallback: older Paper versions may not have this method
            }
        }
    }

    /**
     * Gets LuckPerms primary group weight via PAPI placeholder.
     * Falls back to 0 if not available.
     */
    private int getLuckPermsWeight(Player player) {
        if (!PlaceholderResolver.isPapiAvailable()) return 0;
        String weightStr = PlaceholderResolver.resolve("%luckperms_primary_group_weight%", player);
        if (weightStr == null || weightStr.isEmpty() || weightStr.equals("%luckperms_primary_group_weight%")) {
            return 0;
        }
        try {
            return Integer.parseInt(weightStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
