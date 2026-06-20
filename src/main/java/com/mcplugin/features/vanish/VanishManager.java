package com.mcplugin.features.vanish;

import com.mcplugin.Main;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vanish-система: полностью скрывает игрока от других.
 * <p>
 * Возможности:
 * <ul>
 *   <li>Скрытие из tab-complete (AsyncTabCompleteEvent)</li>
 *   <li>Отмена join/quit сообщений</li>
 *   <li>Визуальная невидимость (hidePlayer)</li>
 *   <li>Отключение звуков (setSilent)</li>
 *   <li>Скрытие из tab list (ClientboundPlayerInfoRemovePacket)</li>
 *   <li>Фильтрация /list команды</li>
 *   <li>Работа с оффлайн-игроками (состояние сохраняется)</li>
 * </ul>
 */
public class VanishManager implements Listener {

    private static VanishManager instance;

    // UUIDs игроков в ванише (ConcurrentHashMap для потокобезопасности)
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new VanishManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        reloadConfig();
        Main.getInstance().getLogger().info("[Vanish] Manager initialized. " + vanishedPlayers.size() + " vanished player(s) loaded.");
    }

    public static void reloadConfig() {
        loadVanishedPlayers();
    }

    public static VanishManager getInstance() {
        return instance;
    }

    // =========================
    // PERSISTENCE (config.yml → vanish.vanished_players)
    // =========================
    private static void loadVanishedPlayers() {
        vanishedPlayers.clear();
        List<String> uuidStrings = Main.getInstance().getConfig().getStringList("vanish.vanished_players");
        if (uuidStrings != null) {
            for (String s : uuidStrings) {
                try {
                    vanishedPlayers.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                    Main.getInstance().getLogger().warning("[Vanish] Invalid UUID in config: " + s);
                }
            }
        }
    }

    public static void saveVanishedPlayers() {
        List<String> uuidStrings = vanishedPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        Main.getInstance().getConfig().set("vanish.vanished_players", uuidStrings);
        Main.getInstance().saveConfig();
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
     * Отправляет ClientboundPlayerInfoRemovePacket всем онлайн-игрокам,
     * чтобы скрыть target из их tab list (клавиша TAB).
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
     * Отправляет ClientboundPlayerInfoUpdatePacket (ADD_PLAYER) всем онлайн-игрокам,
     * чтобы вернуть target в их tab list.
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
     * Удаляет всех ванишнутых игроков из tab list данного игрока.
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
        // Скрываем от всех онлайн-игроков (entity)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            online.hidePlayer(Main.getInstance(), player);
        }
        // Удаляем из tab list (через пакет)
        removeFromTabList(player);
        // Отключаем звуки
        player.setSilent(true);
    }

    private static void removeVanish(Player player) {
        // Показываем всем онлайн-игрокам (entity)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            online.showPlayer(Main.getInstance(), player);
        }
        // Возвращаем в tab list (через пакет)
        addToTabList(player);
        // Включаем звуки обратно
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
        // Удаляем из tab list всех остальных игроков
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
                // Скрываем entity
                player.hidePlayer(Main.getInstance(), vanishedOnline);
            }
        }
        // Удаляем ванишнутых из tab list игрока
        removeVanishedPlayersFromTabList(player);
    }

    // =========================
    // LISTENERS
    // =========================

    /**
     * PlayerJoin — применяем ваниш при входе, отменяем join-сообщение.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isVanished(uuid)) {
            // Отменяем join message
            event.setJoinMessage(null);

            // Применяем ваниш (скрываем от других, отключаем звуки)
            // Задержка на 1 тик чтобы скрытие применилось после спавна
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    applyVanishOnJoin(player);
                }
            }, 1L);
        } else {
            // Игрок не в ванише, но нужно скрыть от него всех кто в ванише
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    hideVanishedPlayersFrom(player);
                }
            }, 1L);
        }
    }

    /**
     * PlayerQuit — отменяем quit-сообщение для ванишнутых.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer().getUniqueId())) {
            event.setQuitMessage(null);
        }
    }

    /**
     * PlayerChangedWorldEvent — при смене мира у ванишнутого игрок может
     * снова появиться в табе других игроков. Переприменяем удаление из таба.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isVanished(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline() && isVanished(player.getUniqueId())) {
                    // hidePlayer уже кросс-мировой, но пакет таба нужно отправить снова
                    removeFromTabList(player);
                }
            }, 2L);
        }
    }

    /**
     * PlayerRespawnEvent — при респавне игрок может снова появиться в табе.
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
     * AsyncTabComplete — фильтруем имена ванишнутых игроков из автодополнения.
     * Работает для TAB в чате и в командах.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncTabComplete(com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event) {
        if (vanishedPlayers.isEmpty()) return;

        // Only filter if the sender doesn't have vanish permission
        if (event.getSender().hasPermission("mcplugin.command.vanish")) return;

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
     * PlayerCommandPreprocessEvent — перехватываем /minecraft:list,
     * только для случая, когда команда вызвана через пространство имён.
     * Обычный /list теперь перехватывается через CommandRegistrar (VanishListCommand).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (vanishedPlayers.isEmpty()) return;

        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();

        // Только /minecraft:list — обычный /list уже переопределён через CommandMap
        if (!message.equals("/minecraft:list") && !message.startsWith("/minecraft:list ")) return;

        Player sender = event.getPlayer();
        boolean canSeeVanished = sender.hasPermission("mcplugin.command.vanish");

        List<String> visibleNames = new ArrayList<>();
        int vanishedCount = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isVanished(online.getUniqueId())) {
                vanishedCount++;
                if (canSeeVanished) {
                    visibleNames.add("§7" + online.getDisplayName() + "§r");
                }
            } else {
                visibleNames.add("§f" + online.getDisplayName() + "§r");
            }
        }

        int totalOnline = Bukkit.getOnlinePlayers().size();
        int visibleCount = totalOnline - (canSeeVanished ? 0 : vanishedCount);

        event.setCancelled(true);
        sender.sendMessage("§7На сервере §f" + visibleCount + "§7/§f" + Bukkit.getMaxPlayers() + " §7игроков:");
        sender.sendMessage(String.join("§7, ", visibleNames));
        if (vanishedCount > 0 && canSeeVanished) {
            sender.sendMessage("§7(" + vanishedCount + " в ванише)");
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
