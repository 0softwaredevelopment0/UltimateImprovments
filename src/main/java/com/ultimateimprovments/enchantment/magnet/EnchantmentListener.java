package com.ultimateimprovments.enchantment.magnet;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener: Magnet enchantment — attracts dropped items to the player.
 * <p>
 * While a player holds a Magnet tool, every FRESHLY dropped item (pickup delay still
 * active, i.e. items that just came out of a broken block) within {@value #PULL_RADIUS}
 * blocks is pulled toward him at {@value #PULL_SPEED} blocks/second.
 * <p>
 * <b>Why pickup-delay-based (not drop events):</b> drops produced by AoE / VeinMiner /
 * TreeCapitator / AutoSmelt are created via {@code breakNaturally()} inside the same
 * {@code BlockBreakEvent} and never fire a per-item Bukkit event. Freshly-spawned
 * {@link Item} entities always carry a positive pickup delay, so scanning for them
 * catches ALL drops from every source — including those from AoE — with zero coupling
 * to the other enchantments.
 * <p>
 * Items already being pulled are tracked per player, so a pull is never interrupted
 * mid-flight when the pickup delay expires. Items that were lying on the ground before
 * (pickup delay 0, not tracked) are left alone — only what THIS player's tool dropped
 * gets attracted.
 */
public class EnchantmentListener implements Listener {

    /** Pull speed in blocks/second. */
    static final double PULL_SPEED = 0.5;

    /** Pull speed per tick (20 ticks/second): 0.5 / 20 = 0.025 blocks/tick. */
    private static final double PULL_PER_TICK = PULL_SPEED / 20.0;

    /** Radius (blocks) around the player in which drops are attracted. */
    private static final int PULL_RADIUS = 8;

    /** Scan radius covering pull + cleanup in a single getNearbyEntities call. */
    private static final int CLEANUP_RADIUS = 16;

    /** Once an item is closer than this, vanilla pickup takes over. */
    private static final double STOP_DISTANCE = 1.5;

    /** Small upward bias so items don't get stuck on uneven ground. */
    private static final double Y_BOOST = 0.03;

    /** Periodic sweep interval (ticks). */
    static final long SWEEP_INTERVAL_TICKS = 1L;

    /** Per-player set of item UUIDs currently being pulled. */
    private static final Map<UUID, Set<UUID>> TRACKED = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    //  EVENTS
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        TRACKED.remove(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: pull tracked items for every Magnet holder. */
    private static void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                updatePlayer(player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Magnet] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Updates attraction for one player: drops their Magnet tool may pull.
     */
    private static void updatePlayer(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasCharm = tool != null && tool.getType() != Material.AIR
                && com.ultimateimprovments.enchantment.magnet.Enchantment.getLevel(tool) > 0;

        if (!hasCharm) {
            // No Magnet in hand → stop tracking this player's items.
            TRACKED.remove(player.getUniqueId());
            return;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        Set<UUID> tracked = TRACKED.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        // One scan covering both the pull radius and the cleanup radius.
        Collection<Entity> nearby = world.getNearbyEntities(
                playerLoc, CLEANUP_RADIUS, CLEANUP_RADIUS, CLEANUP_RADIUS);

        Set<UUID> alive = new HashSet<>();
        for (Entity entity : nearby) {
            if (!(entity instanceof Item item)) continue;
            if (item.isDead() || !item.isValid()) continue;
            if (item.getLocation().getWorld() == null) continue;

            UUID itemUuid = item.getUniqueId();
            alive.add(itemUuid);

            double dist = item.getLocation().distance(playerLoc);
            boolean inPullRange = dist <= PULL_RADIUS;
            boolean fresh = item.getPickupDelay() > 0;
            boolean alreadyTracked = tracked.contains(itemUuid);

            if (!inPullRange) {
                // Beyond the pull radius — nothing to do this tick.
                continue;
            }
            if (dist < STOP_DISTANCE) {
                // Close enough — vanilla pickup handles it; stop pulling.
                tracked.remove(itemUuid);
                continue;
            }
            if (!fresh && !alreadyTracked) {
                // Old item that was already lying around — not this player's drop.
                continue;
            }

            // Freshly dropped item (any source, incl. AoE/VeinMiner/TreeCapitator/
            // AutoSmelt drops) or one we're already pulling → attract it.
            tracked.add(itemUuid);
            pull(item, playerLoc);
        }

        // Cleanup: forget tracked items that were picked up / died / despawned.
        tracked.retainAll(alive);
    }

    /**
     * Pulls an item toward the player at a constant speed (blocks/tick).
     * Absolute velocity set each tick keeps the speed steady regardless of gravity.
     */
    private static void pull(Item item, Location playerLoc) {
        Vector toPlayer = playerLoc.toVector().subtract(item.getLocation().toVector());
        double dist = toPlayer.length();
        if (dist <= 0.01) return;

        Vector velocity = toPlayer.normalize().multiply(PULL_PER_TICK);
        velocity.setY(velocity.getY() + Y_BOOST);
        item.setVelocity(velocity);
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers the listener and starts the periodic pull sweep.
     */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentListener(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweep,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[Magnet] Listener registered (pull " + PULL_SPEED
                + " blk/s, radius " + PULL_RADIUS + ").");
    }
}
