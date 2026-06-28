package com.mcplugin.mechanics.features.blocks;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Полная остановка движения в паутине (cobweb).
 * <p>
 * Использует PlayerMoveEvent вместо BukkitRunnable — ловит КАЖДОЕ движение
 * и отменяет его, если игрок находится в cobweb или пытается в него зайти.
 * Проверяет как блок у ног, так и блок на уровне глаз (Y+1).
 */
public class BoostedCobwebManager implements Listener {

    private static BoostedCobwebManager instance;
    private static boolean enabled = true;

    public static void init(Main plugin) {
        instance = new BoostedCobwebManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.boostedcobweb");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Пропускаем только поворот (изменение yaw/pitch без движения)
        if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) {
            return;
        }

        // Проверяем блоки: у ног игрока и на уровне тела (Y+1)
        if (isInCobweb(from) || isInCobweb(to)) {
            event.setCancelled(true);
            player.setVelocity(player.getVelocity().setX(0).setZ(0).setY(Math.min(player.getVelocity().getY(), 0)));
            player.sendActionBar("§c🕸 Вы не можете двигаться!");
        }
    }

    /**
     * Проверяет, находится ли локация внутри cobweb (проверка 2 слоёв: ноги + тело).
     */
    private boolean isInCobweb(Location loc) {
        return loc.getBlock().getType() == Material.COBWEB
                || loc.clone().add(0, 1, 0).getBlock().getType() == Material.COBWEB;
    }
}
