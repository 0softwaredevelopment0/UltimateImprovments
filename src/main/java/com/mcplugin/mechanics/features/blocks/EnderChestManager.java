package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Random;

/**
 * Менеджер эндер-сундуков.
 * <p>
 * При ломании — блок ведёт себя как обычный эндер-сундук (дропает обсидиан).
 * При открытии (ПКМ) — есть шанс {@code explosion_chance} (0.001 = 0.1%),
 * что сундук взорвётся как заряженный крипер, нанеся урон игроку.
 */
public class EnderChestManager implements Listener {

    private static EnderChestManager instance;
    private static boolean enabled = true;
    private static double explosionChance = 0.001; // 0.1%
    private static int explosionRadius = 5;
    private static double damage = 8192;
    private static final Random RANDOM = new Random();

    public static void init(Main plugin) {
        instance = new EnderChestManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.enderchest");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        explosionChance = cfg.getDouble("explosion_chance", 0.001);
        explosionRadius = cfg.getInt("explosion_radius", 5);
        damage = cfg.getDouble("damage", 8192);
    }

    /**
     * При открытии эндер-сундука — шанс взрыва 0.1% (по умолчанию).
     * При ломании — поведение как у ванильного (не взрывается, просто дропается обсидиан).
     */
    @EventHandler
    public void onEnderChestInteract(PlayerInteractEvent e) {
        if (e.isCancelled()) return;
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        Player player = e.getPlayer();

        // Шанс взрыва (фиксируем значение ДО проверки, чтобы лог был корректен)
        double roll = RANDOM.nextDouble();
        if (roll >= explosionChance) return;

        Location loc = e.getClickedBlock().getLocation();

        // Взрыв
        loc.getWorld().spawn(loc, Creeper.class, c -> {
            c.setPowered(true);
            c.setExplosionRadius(explosionRadius);
            c.setMaxFuseTicks(0);
            c.setIgnited(true);
        });

        // Урон игроку
        if (damage > 0) {
            player.damage(damage);
        }

        // Отменяем событие — сундук взорвался, не открываем GUI
        e.setCancelled(true);

        // Звук
        loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.5f);

        // Логирование
        Main.getInstance().getLogger().info("[EnderChest] " + player.getName()
                + " opened an ender chest at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " and it EXPLODED! (roll=" + String.format("%.4f", roll) + " < chance=" + explosionChance + ")");

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
