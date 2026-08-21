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
 * Radiation decays over time at {@code radiation_decay_per_second} points/sec.
 * By default, decay rate matches the gain so that old radiation wears off
 * before the next batch arrives.
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
    private static double radiationDecayPerSecond = 19.9;

    private static boolean running = false;

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig();
        radiationPoints = cfg.getInt("space.radiation_points", 199);
        radiationIntervalTicks = cfg.getLong("space.radiation_interval_ticks", 200);
        radiationDecayPerSecond = cfg.getDouble("space.radiation_decay_per_second", 19.9);
    }

    public static void start(Main plugin) {
        if (running) return;
        running = true;

        reloadConfig();

        // Main radiation tick — add points every interval
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!SpaceManager.isEnabled() || SpaceManager.getSpaceWorld() == null) {
                    return;
                }
                for (Player player : SpaceManager.getSpaceWorld().getPlayers()) {
                    if (player.isDead()) continue;

                    int current = getRadiation(player);
                    int newAmount = current + radiationPoints;
                    setRadiation(player, newAmount);

                    applyRadiationEffects(player, newAmount);

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

        // Decay tick — remove radiation every second
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!SpaceManager.isEnabled() || SpaceManager.getSpaceWorld() == null) {
                    return;
                }
                int decayPerTick = (int) Math.ceil(radiationDecayPerSecond); // per 1 second = 20 ticks
                for (Player player : SpaceManager.getSpaceWorld().getPlayers()) {
                    if (player.isDead()) continue;
                    int current = getRadiation(player);
                    if (current <= 0) continue;

                    int newAmount = Math.max(0, current - decayPerTick);
                    setRadiation(player, newAmount);
                    applyRadiationEffects(player, newAmount);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // every 1 second
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
        if (points <= 0) {
            // Clear all radiation effects when radiation is gone
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.WITHER);
            player.removePotionEffect(PotionEffectType.NAUSEA);
            return;
        }

        // Slowness at 1+ points
        int slowAmp = Math.min(points / 100, 2);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                40, slowAmp, false, false, true));

        // Wither at 100+ points
        if (points >= 100) {
            int witherAmp = Math.min((points - 100) / 100, 2);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    40, witherAmp, false, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.WITHER);
        }

        // Nausea at 200+ points
        if (points >= 200) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    40, 0, false, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.NAUSEA);
        }

        // Damage at 300+ points
        if (points >= 300) {
            double damage = Math.min((points - 300) / 100.0 + 1.0, 10.0);
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
