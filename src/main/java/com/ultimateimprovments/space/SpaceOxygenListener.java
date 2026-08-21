package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Space oxygen system:
 * <ul>
 *   <li>All living entities without a helmet take 1 damage/second</li>
 *   <li>Players without a helmet see an action bar warning</li>
 *   <li>Death from oxygen → custom death message + advancement</li>
 * </ul>
 */
public class SpaceOxygenListener implements Listener {

    private static final String HELMET_WARNING = "<red>⚠ Equip a helmet to stop suffocating!</red>";

    /** Players who died from oxygen deprivation (for death message override) */
    private static final Set<UUID> oxygenDeaths = ConcurrentHashMap.newKeySet();

    private static boolean running = false;

    public static void start(Main plugin) {
        if (running) return;
        running = true;

        // Check every second (20 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!SpaceManager.isEnabled() || SpaceManager.getSpaceWorld() == null) return;
                for (LivingEntity entity : SpaceManager.getSpaceWorld().getLivingEntities()) {
                    if (!hasHelmet(entity)) {
                        // Track oxygen death for players
                        if (entity instanceof Player player) {
                            oxygenDeaths.add(player.getUniqueId());
                        }
                        // Deal 1 damage (simulates suffocation)
                        entity.damage(1.0);
                        // Show action bar warning to players
                        if (entity instanceof Player player) {
                            player.sendActionBar(MessageUtil.parse(HELMET_WARNING));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public static void stop() {
        running = false;
        oxygenDeaths.clear();
    }

    /**
     * Override death message for oxygen deaths and grant advancement.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!SpaceManager.isInSpace(player)) return;

        if (oxygenDeaths.remove(uuid)) {
            // Custom death message
            event.setDeathMessage(MessageUtil.legacy(
                "<white>" + player.getName() + " ran out of oxyden</white>"));

            // Grant advancement
            SpaceManager.grantOxygenDeathAdvancement(player);
        }
    }

    private static boolean hasHelmet(LivingEntity entity) {
        ItemStack helmet = entity.getEquipment() != null ? entity.getEquipment().getHelmet() : null;
        return helmet != null && helmet.getType() != Material.AIR;
    }
}