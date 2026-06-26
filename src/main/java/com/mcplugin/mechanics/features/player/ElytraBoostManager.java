package com.mcplugin.mechanics.features.player;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.database.DatabaseManager;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ElytraBoost — нажатие пробела во время полёта на элитрах даёт
 * МАКСИМАЛЬНЫЙ буст скорости (без расхода фейерверков).
 * <p>
 * В Paper 1.21.4 нажатие пробела в полёте НЕ триггерит PlayerToggleFlightEvent.
 * Вместо этого сервер вызывает {@code jumpFromElytra()}, который резко меняет
 * Y-скорость игрока с отрицательной (падение) на положительную (подъём).
 * <p>
 * Детектим это через {@link PlayerMoveEvent}: если игрок резко перестал падать
 * и начал подниматься — значит был нажат пробел.
 * <p>
 * Дополнительно обрабатываем {@link PlayerToggleFlightEvent} на случай,
 * если в будущих версиях Paper он снова начнёт срабатывать.
 */
public class ElytraBoostManager implements Listener {

    private static ElytraBoostManager instance;

    /** Предыдущая Y-дельта для каждого игрока (для детекции рывка). */
    private static final Map<UUID, Double> lastYDelta = new HashMap<>();

    /** Кулдаун буста (мс) — небольшой, чтобы не спамить тики. */
    private static final long BOOST_COOLDOWN_MS = 200;

    /** Время последнего буста для каждого игрока. */
    private static final Map<UUID, Long> lastBoostTime = new HashMap<>();

    /** Игроки, отключившие автоматический буст при прыжке (/mp togglefly). */
    private static final Set<UUID> flyDisabled = ConcurrentHashMap.newKeySet();

    // =========================
    // TOGGLE FLY
    // =========================
    public static boolean isFlyEnabled(UUID uuid) {
        return !flyDisabled.contains(uuid);
    }

    public static void toggleFlyEnabled(UUID uuid) {
        if (flyDisabled.contains(uuid)) {
            flyDisabled.remove(uuid);
            deleteFlyDisabledFromDb(uuid);
        } else {
            flyDisabled.add(uuid);
            saveFlyDisabledToDb(uuid);
        }
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new ElytraBoostManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        loadFlyDisabledFromDb();
        plugin.getLogger().info("[ElytraBoost] ✔ Enabled — press SPACE while gliding to boost.");
    }

    public static ElytraBoostManager getInstance() {
        return instance;
    }

    // =========================
    // DETECT SPACE VIA Y-DELTA (работает в Paper 1.21.4)
    // =========================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // Проверка toggle: если игрок отключил буст — пропускаем
        if (flyDisabled.contains(player.getUniqueId())) return;

        // Должны быть элитры на груди
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return;

        // Должны быть в воздухе
        if (player.isOnGround()) return;

        double yDelta = event.getTo().getY() - event.getFrom().getY();
        Double prevDelta = lastYDelta.get(player.getUniqueId());
        lastYDelta.put(player.getUniqueId(), yDelta);

        if (prevDelta == null) return;

        // Кулдаун
        long now = System.currentTimeMillis();
        if (now - lastBoostTime.getOrDefault(player.getUniqueId(), 0L) < BOOST_COOLDOWN_MS) return;

        // Детекция: резкая смена с падения на подъём
        // jumpFromElytra() даёт Y-скорость ~0.4
        // Используем разницу prevDelta - yDelta, чтобы не зависеть от тикрейта
        if (prevDelta < -0.1 && yDelta - prevDelta > 0.4) {
            applyBoost(player, now);
        }
    }

    // =========================
    // FALLBACK: PlayerToggleFlightEvent (на случай если сработает)
    // =========================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // Проверка toggle: если игрок отключил буст — пропускаем
        if (flyDisabled.contains(player.getUniqueId())) return;

        // Проверяем элитры
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return;

        // Защита: если игрок НЕ летел до этого — пропускаем (начало полёта)
        if (!player.isGliding() && !lastYDelta.containsKey(player.getUniqueId())) return;

        // Кулдаун
        long now = System.currentTimeMillis();
        if (now - lastBoostTime.getOrDefault(player.getUniqueId(), 0L) < BOOST_COOLDOWN_MS) return;

        // Отменяем событие и возвращаем полёт
        event.setCancelled(true);
        player.setGliding(true);
        applyBoost(player, now);
    }

    // =========================
    // BOOST LOGIC
    // =========================

    private static void applyBoost(Player player, long now) {
        lastBoostTime.put(player.getUniqueId(), now);

        // МАКСИМАЛЬНЫЙ буст: сильный разгон вперёд + вверх
        Vector direction = player.getLocation().getDirection();
        Vector boost = direction.clone().multiply(5.0).setY(Math.max(direction.getY() * 0.5 + 1.2, 0.8));
        player.setVelocity(player.getVelocity().add(boost));

        // Убеждаемся что полёт продолжается
        player.setGliding(true);

        // Эффекты
        var loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.FIREWORK, loc, 40, 1.0, 1.0, 1.0, 0.1);
        player.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.5, 0.5, 0.5, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.8f);
        player.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.5f);
    }

    // =========================
    // CLEANUP
    // =========================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastYDelta.remove(uuid);
        lastBoostTime.remove(uuid);
        // Keep flyDisabled in memory — DB persistence handles server restarts.
        // Player will re-check on next join via the in-memory set.
    }

    // =========================
    // DB PERSISTENCE
    // =========================

    private static void loadFlyDisabledFromDb() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        try (PreparedStatement ps = con.prepareStatement("SELECT uuid FROM elytra_boost_disabled");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    flyDisabled.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    // Corrupted UUID in DB — skip
                }
            }
            Main.getInstance().getLogger().info(
                    "[ElytraBoost] Loaded " + flyDisabled.size() + " disabled players from DB");
        } catch (SQLException e) {
            Main.getInstance().getLogger().severe("[ElytraBoost] DB load error: " + e.getMessage());
        }
    }

    private static void saveFlyDisabledToDb(UUID uuid) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT OR REPLACE INTO elytra_boost_disabled (uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().severe("[ElytraBoost] DB save error: " + e.getMessage());
        }
    }

    private static void deleteFlyDisabledFromDb(UUID uuid) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM elytra_boost_disabled WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            Main.getInstance().getLogger().severe("[ElytraBoost] DB delete error: " + e.getMessage());
        }
    }

    // =========================
    // HELPERS
    // =========================
}
