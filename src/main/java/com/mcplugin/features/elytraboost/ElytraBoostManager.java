package com.mcplugin.features.elytraboost;

import com.mcplugin.Main;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new ElytraBoostManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        plugin.getLogger().info("[ElytraBoost] ✓ Enabled — press SPACE while gliding to boost.");
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

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

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
        player.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
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
    }

    // =========================
    // HELPERS (unused — keep for reference)
    // =========================
}
