package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер эндер-сундуков.
 * <p>
 * При ломании — блок ведёт себя как обычный эндер-сундук (дропает обсидиан).
 * При открытии (ПКМ) — есть шанс {@code explosion_chance} (0.001 = 0.1%),
 * что сундук взорвётся как заряженный крипер, нанеся урон игроку.
 * <p>
 * При быстром открытии и закрытии эндер-сундука (быстрее {@code quick_close_threshold_ms}):
 * <ul>
 *   <li>Если игрок смотрит на сундук — получает {@code quick_close_damage} урона</li>
 *   <li>Если игрок отвёл взгляд (дальше {@code look_away.angle_threshold} градусов) — сундук взрывается</li>
 * </ul>
 */
public class EnderChestManager implements Listener {

    private static boolean enabled = true;
    private static double explosionChance = 0.001; // 0.1%
    private static double damage = 8192;
    private static double explosionPower = 10.0; // сила взрыва (разрушает блоки)
    private static final Random RANDOM = new Random();

    // Быстрое открытие/закрытие
    private static boolean quickCloseEnabled = true;
    private static long quickCloseThresholdMs = 1000; // 1 секунда
    private static double quickCloseDamage = 1;

    // Взрыв при отведённом взгляде
    private static boolean lookAwayEnabled = true;
    private static double lookAwayAngleThreshold = 45.0; // градусов
    private static double lookAwayExplosionDamage = 20;

    // Хранит время открытия + позицию сундука для каждого игрока
    private static final Map<UUID, EnderChestOpenData> enderChestOpenData = new ConcurrentHashMap<>();

    /**
     * Данные об открытии эндер-сундука игроком.
     */
    private static class EnderChestOpenData {
        final long openTime;
        final Location chestLocation;

        EnderChestOpenData(long openTime, Location chestLocation) {
            this.openTime = openTime;
            this.chestLocation = chestLocation.clone();
        }
    }

    public static void init(Main plugin) {
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(new EnderChestManager(), plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.enderchest");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        explosionChance = cfg.getDouble("explosion_chance", 0.001);
        explosionPower = cfg.getDouble("explosion_power", 10.0);
        damage = cfg.getDouble("damage", 8192);
        quickCloseEnabled = cfg.getBoolean("quick_close.enabled", true);
        quickCloseThresholdMs = cfg.getLong("quick_close.threshold_ms", 1000);
        quickCloseDamage = cfg.getDouble("quick_close.damage", 1);
        lookAwayEnabled = cfg.getBoolean("quick_close.look_away.enabled", true);
        lookAwayAngleThreshold = cfg.getDouble("quick_close.look_away.angle_threshold", 45.0);
        lookAwayExplosionDamage = cfg.getDouble("quick_close.look_away.explosion_damage", 20);
    }

    /**
     * Записывает время и позицию сундука при ПКМ по эндер-сундуку.
     * Также обрабатывает шанс взрыва (0.1% по умолчанию).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        Player player = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        // Всегда записываем данные для quick_close (даже если сундук взорвётся — close не сработает)
        if (quickCloseEnabled) {
            enderChestOpenData.put(player.getUniqueId(), new EnderChestOpenData(System.currentTimeMillis(), loc));
        }

        // Шанс взрыва (фиксируем значение ДО проверки, чтобы лог был корректен)
        double roll = RANDOM.nextDouble();
        if (roll >= explosionChance) return;

        // Удаляем сам блок сундука (взрыв может его не уничтожить из-за blast resistance)
        loc.getBlock().setType(Material.AIR);

        // Взрыв с разрушением блоков (сила 10)
        loc.getWorld().createExplosion(loc, (float) explosionPower, false, true);

        // Дополнительный урон игроку
        if (damage > 0) {
            player.damage(damage);
        }

        // Отменяем событие — сундук взорвался, не открываем GUI
        e.setCancelled(true);

        // Логирование
        Main.getInstance().getLogger().info("[EnderChest] " + player.getName()
                + " opened an ender chest at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " and it EXPLODED! (power=" + explosionPower + ", roll=" + String.format("%.4f", roll) + " < chance=" + explosionChance + ")");

        // 🏆 Достижение: blowed_by_echest
        try {
            var adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("minecraft", "datapack/blowed_by_echest"));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Резервное запоминание времени открытия (на случай, если PlayerInteractEvent
     * не сработал — например, при открытии через API).
     * Пытается определить позицию сундука через targetBlock.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent e) {
        if (!enabled || !quickCloseEnabled) return;
        if (e.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        // Если данные уже есть от PlayerInteractEvent — не перезаписываем (там точный Location)
        UUID uuid = player.getUniqueId();
        if (enderChestOpenData.containsKey(uuid)) return;

        // Пытаемся найти эндер-сундук, на который смотрит игрок
        var target = player.getTargetBlockExact(5);
        Location chestLoc = (target != null && target.getType() == Material.ENDER_CHEST)
                ? target.getLocation()
                : player.getLocation();
        enderChestOpenData.put(uuid, new EnderChestOpenData(System.currentTimeMillis(), chestLoc));
    }

    /**
     * При закрытии эндер-сундука проверяем:
     * <ul>
     *   <li>Если закрыл быстрее чем threshold_ms — проверяем куда смотрел</li>
     *   <li>Смотрит на сундук (≤ angle_threshold°) → {@code quickCloseDamage} урона</li>
     *   <li>Отвёл взгляд (> angle_threshold°) → взрыв (charged creeper)</li>
     * </ul>
     */
    @EventHandler
    public void onEnderChestClose(InventoryCloseEvent e) {
        if (!enabled || !quickCloseEnabled) return;
        if (e.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        EnderChestOpenData data = enderChestOpenData.remove(uuid);
        if (data == null) return;

        long elapsed = System.currentTimeMillis() - data.openTime;
        if (elapsed >= quickCloseThresholdMs) return;

        // Игрок закрыл сундук быстро — проверяем, куда он смотрит
        if (lookAwayEnabled && isLookingAway(player, data.chestLocation)) {
            // Отвёл взгляд → ВЗРЫВ
            triggerLookAwayExplosion(player, data.chestLocation);
        } else if (quickCloseDamage > 0) {
            // Смотрит на сундук → 1 урона
            player.damage(quickCloseDamage);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.2f);
        }
    }

    /**
     * Проверяет, смотрит ли игрок в сторону от блока дальше чем angle_threshold.
     *
     * @param player  игрок
     * @param targetLocation позиция блока (эпизод сундука)
     * @return true если игрок смотрит дальше angle_threshold градусов от блока
     */
    private static boolean isLookingAway(Player player, Location targetLocation) {
        // Вектор направления взгляда игрока (нормализован)
        Vector playerDir = player.getEyeLocation().getDirection().normalize();

        // Вектор от глаз игрока к центру блока сундука
        // Центр блока = location + 0.5 по X и Z для точности
        Location targetCenter = targetLocation.clone().add(0.5, 0.5, 0.5);
        Vector toTarget = targetCenter.toVector().subtract(player.getEyeLocation().toVector());

        // Проверяем, чтобы игрок не стоял прямо на сундуке (длина == 0)
        if (toTarget.lengthSquared() < 0.01) return false;

        toTarget.normalize();

        // Угол между направлением взгляда и направлением на сундук
        double dot = playerDir.dot(toTarget);
        // Ограничиваем от -1 до 1 (защита от floating point ошибок)
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angleDeg = Math.toDegrees(Math.acos(dot));

        return angleDeg > lookAwayAngleThreshold;
    }

    /**
     * Вызывает взрыв с разрушением блоков на позиции сундука.
     * Сам блок сундука гарантированно удаляется.
     */
    private static void triggerLookAwayExplosion(Player player, Location chestLocation) {
        // Удаляем сам блок сундука (взрыв может его не уничтожить из-за blast resistance)
        chestLocation.getBlock().setType(Material.AIR);

        // Взрыв с разрушением блоков (сила 10)
        chestLocation.getWorld().createExplosion(chestLocation, (float) explosionPower, false, true);

        // Урон игроку
        if (lookAwayExplosionDamage > 0) {
            player.damage(lookAwayExplosionDamage);
        }

        // Логирование
        Main.getInstance().getLogger().info("[EnderChest] " + player.getName()
                + " quickly closed ender chest while looking away at "
                + chestLocation.getBlockX() + " " + chestLocation.getBlockY() + " " + chestLocation.getBlockZ()
                + " — EXPLODED! (power=" + explosionPower + ")");

        // 🏆 Достижение: blowed_by_echest
        try {
            var adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("minecraft", "datapack/blowed_by_echest"));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception ignored) {}
    }
}
