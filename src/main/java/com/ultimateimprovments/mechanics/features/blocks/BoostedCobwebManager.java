package com.ultimateimprovments.mechanics.features.blocks;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Full movement stop in cobweb.
 * <p>
 * Uses PlayerMoveEvent instead of a BukkitRunnable — catches EVERY movement
 * and cancels it if the player is in a cobweb or tries to enter one.
 * Checks both the block at the feet and the block at eye level (Y+1).
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

        // Skip pure rotation (yaw/pitch change without movement)
        if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) {
            return;
        }

        boolean inCobwebFrom = isInCobweb(from);
        boolean inCobwebTo = isInCobweb(to);

        if (inCobwebFrom || inCobwebTo) {
            // ── Allow falling down (gravity):
            //    - falling into a cobweb from above
            //    - falling inside/through a cobweb
            if (to.getY() < from.getY()) {
                return;
            }

            // Block everything else (walking, jumping, horizontal movement)
            event.setCancelled(true);
            player.teleport(from);
            player.setVelocity(new Vector(0, 0, 0));
            player.sendActionBar(com.ultimateimprovments.util.MessageUtil.parse("<red>❌ You can't move in the cobweb!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (!(event.getDamager() instanceof Player player)) return;

        if (isInCobweb(player)) {
            event.setCancelled(true);
            player.sendActionBar(MessageUtil.parse("<red>❌ You can't attack in the cobweb!</red>"));
        }
    }

    /**
     * Checks whether the player is inside a cobweb — the block at their feet
     * or at eye level (Y+1).
     */
    private boolean isInCobweb(Player player) {
        Location loc = player.getLocation();
        return isInCobweb(loc) || isInCobweb(loc.clone().add(0, 1, 0));
    }

    /**
     * Checks whether the player is in a cobweb exactly at ~ ~ ~ (the block at their feet).
     */
    private boolean isInCobweb(Location loc) {
        return loc.getBlock().getType() == Material.COBWEB;
    }
}
