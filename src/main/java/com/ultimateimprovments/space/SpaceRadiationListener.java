package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive radiation in the space dimension.
 * <p>
 * Every {@code radiation_interval_ticks} ticks, every player in space
 * gains {@code radiation_points} radiation points (configurable).
 * <p>
 * Effects based on accumulated radiation:
 * <ul>
 *   <li>  1+ points → Slowness I</li>
 *   <li> 100+ points → Wither I</li>
 *   <li> 200+ points → Nausea</li>
 *   <li> 300+ points → Instant damage (1 heart per tick above 300)</li>
 * </ul>
 * Radiation resets when the player dies or leaves the space dimension.
 */
public class SpaceRadiationListener implements Listener {

    private static final NamespacedKey KEY_RADIATION = new NamespacedKey(Main.getInstance(), "space_radiation");

    private static int radiationPoints = 199;
    private static long radiationIntervalTicks = 200;

    private static boolean running = false;
    private static final Map<UUID, Boolean> taskRunning = new ConcurrentHashMap<>();

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig();
        radiationPoints = cfg.getInt("space.radiation_points", 199);
        radiationIntervalTicks = cfg.getLong("space.radiation_interval_ticks", 200);
    }

    public static void start(Main plugin) {
        if (running) return;
        running = true;

        reloadConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!SpaceManager.isEnabled() || SpaceManager.getSpaceWorld() == null) {
                    return;
                }
                for (Player player : SpaceManager.getSpaceWorld().getPlayers()) {
                    if (player.isDead()) continue;

                    // Add radiation points
                    int current = getRadiation(player);
                    int newAmount = current + radiationPoints;
                    setRadiation(player, newAmount);

                    // Apply effects based on accumulated radiation
                    applyRadiationEffects(player, newAmount);

                    // Warn at thresholds
                    if (current < 100 && newAmount >= 100) {
                        player.sendMessage(MessageUtil.parse(
                                "<dark_gray>[<green>UI<white>] <red>Radiation rising... Wither effects detected.</red>"));
                    }
                    if (current < 200 && newAmount >= 200) {
                        player.sendMessage(MessageUtil.parse(
                                "<dark_gray>[<green>UI<white>] <red>Radiation critical! Nausea and damage incoming.</red>"));
                    }
                }
            }
        }.runTaskTimer(plugin, radiationIntervalTicks, radiationIntervalTicks);
    }

    // ===== RADIATION POINTS =====

    public static int getRadiation(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.getOrDefault(KEY_RADIATION, PersistentDataType.INTEGER, 0);
    }

    public static void setRadiation(Player player, int amount) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(KEY_RADIATION, PersistentDataType.INTEGER, Math.max(0, amount));
    }

    public static void resetRadiation(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(KEY_RADIATION);
    }

    // ===== EFFECTS =====

    private static void applyRadiationEffects(Player player, int points) {
        if (points <= 0) return;

        // Slowness at 1+ points
        if (points >= 1) {
            int amplifier = Math.min(points / 100, 2); // max Slowness III
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    (int) (radiationIntervalTicks + 20), amplifier, false, false, true));
        }

        // Wither at 100+ points
        if (points >= 100) {
            int amplifier = Math.min((points - 100) / 100, 2); // max Wither III
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    (int) (radiationIntervalTicks + 20), amplifier, false, false, true));
        }

        // Nausea at 200+ points
        if (points >= 200) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    (int) (radiationIntervalTicks + 20), 0, false, false, true));
        }

        // Damage at 300+ points (1 heart per 100 above 300)
        if (points >= 300) {
            double damage = Math.min((points - 300) / 100.0 + 1.0, 10.0); // max 10 damage
            player.damage(damage);
        }
    }

    public static void stop() {
        running = false;
    }

    // ===== EVENT LISTENERS =====

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        resetRadiation(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        resetRadiation(event.getPlayer());
    }
}
