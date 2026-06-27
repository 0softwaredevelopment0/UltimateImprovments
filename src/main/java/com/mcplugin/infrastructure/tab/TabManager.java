package com.mcplugin.infrastructure.tab;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.PlayerSettingsDB;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
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

/**
 * Управляет кастомным таб-листом:
 * <ul>
 *   <li>Header — текст над списком игроков (MiniMessage + плейсхолдеры)</li>
 *   <li>Footer — текст под списком игроков (MiniMessage + плейсхолдеры)</li>
 *   <li>PlayerList name — префикс/суффикс перед/после ника (пинг, PAPI и т.д.)</li> * <li>Hide spectators — скрытие игроков в спектаторе из таба</li>
 * <li>Sort mode — сортировка ников в табе (A-Z, Z-A, LuckPerms, OP)</li>
 * </ul>
 */
public class TabManager extends BukkitRunnable implements Listener {

    private static TabManager instance;
    private boolean enabled;
    private List<String> headerLines;
    private List<String> footerLines;
    private boolean objectiveEnabled;
    private String objectivePrefix;
    private String objectiveSuffix;
    private String objectiveFormat;
    private boolean hideSpectators;
    private int intervalTicks;
    private SortMode sortMode;

    public enum SortMode {
        NONE,       // no sorting
        A_Z,        // alphabetical A-Z
        Z_A,        // alphabetical Z-A
        LUCKPERMS,  // by LuckPerms primary group
        OP          // OP players first, then non-OP
    }

    public static void init() {
        instance = new TabManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        instance.reloadConfig();

        // Скрываем уже онлайн спектаторов при старте/reload
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
            instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.cancel();
            instance.reloadConfig();
            if (instance.enabled) {
                instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
            }
        }
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
     * Отправляет ClientboundPlayerInfoRemovePacket всем онлайн-игрокам,
     * чтобы скрыть спектатора из их tab list.
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
     * Возвращает спектатора в tab list всех онлайн-игроков.
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
     * Скрывает всех текущих спектаторов из таба данного игрока.
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

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!hideSpectators) return;

        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();
        GameMode oldMode = player.getGameMode();

        // Delay 1 tick so the game mode is actually applied
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;

            if (newMode == GameMode.SPECTATOR && oldMode != GameMode.SPECTATOR) {
                // Переключился в спектатор — скрыть из таба
                removeSpectatorFromTabList(player);
            } else if (oldMode == GameMode.SPECTATOR && newMode != GameMode.SPECTATOR) {
                // Вышел из спектатора — вернуть в таб
                addSpectatorToTabList(player);
            }
        }, 1L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!hideSpectators) return;

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;

            // Если сам новый игрок в спектаторе — скрыть его от всех
            if (player.getGameMode() == GameMode.SPECTATOR) {
                removeSpectatorFromTabList(player);
            }

            // Скрыть всех текущих спектаторов от нового игрока
            hideCurrentSpectatorsFrom(player);
        }, 1L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!hideSpectators) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // При смене мира клиент пере-добавляет в таб — скрываем снова
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                    removeSpectatorFromTabList(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!hideSpectators) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // При респавне клиент пере-добавляет в таб — скрываем снова
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

        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;

            // Проверка per-player toggle скорборда (через PlayerSettingsDB)
            // Это не влияет на таб, но команда /mp togglesb скрывает скорборд

            // Per-player header/footer (with player-specific placeholders)
            Component playerHeader = buildComponent(headerLines, player);
            Component playerFooter = buildComponent(footerLines, player);
            player.sendPlayerListHeaderAndFooter(playerHeader, playerFooter);

            // Player list name — кастомный формат или prefix+name+suffix
            if (objectiveEnabled) {
                if (!objectiveFormat.isEmpty()) {
                    // Кастомный формат — полный контроль: %luckperms_prefix%{player_name}...
                    String resolved = PlaceholderResolver.resolve(objectiveFormat, player);
                    player.playerListName(MessageUtil.parse(resolved));
                } else {
                    // Старая логика: prefix + name + suffix
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
        if (sortMode != SortMode.NONE) {
            applySortOrder(players);
        }
    }

    /**
     * Строит Component из списка строк MiniMessage + плейсхолдеры.
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
