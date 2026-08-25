package com.ultimateimprovments.mechanics.environment.sunburn;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SunburnManager — players catch fire from sunlight in the Overworld.
 *
 * Every check_interval ticks (default 80 = 4s), all online players in
 * Overworld are checked. If daytime + open sky above:
 *   - No helmet => set on fire (fire_ticks duration)
 *   - Helmet => degrade helmet instead (no fire while protected)
 *
 * Minecraft naturally extinguishes fire in water, rain, etc.
 * When player leaves sun (night, underground) => extinguished on next check.
 */
public class SunburnManager implements Listener {

    private static SunburnManager instance;

    // =========================
    // CONFIG
    // =========================
    private boolean enabled;
    private int checkInterval;
    private int fireTicks;
    private int helmetDegradeInterval;
    private int helmetIntegrityLoss;
    private boolean extinguishWhenSafe;
    private Set<String> excludedWorlds;

    // =========================
    // TICK COUNTERS
    // =========================
    private int mainTickCounter = 0;
    private int helmetTickCounter = 0;

    // =========================
    // TRACKING
    // =========================
    private final Set<UUID> exposedPlayers = new HashSet<>();

    public static SunburnManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new SunburnManager();
        instance.loadConfig();

        Main plugin = Main.getInstance();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        ConsoleLogger.info("[SunburnModule] Initialized — check every " + instance.checkInterval
                + " ticks, fire " + instance.fireTicks + " ticks, helmet degrade every "
                + instance.helmetDegradeInterval + " ticks.");
    }

    // =========================
    // CONFIG
    // =========================

    private void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("sunburn.enabled", true);
        checkInterval = cfg.getInt("sunburn.check_interval", 80);
        fireTicks = cfg.getInt("sunburn.fire_ticks", 80);
        helmetDegradeInterval = cfg.getInt("sunburn.helmet_degrade_interval", 80);
        helmetIntegrityLoss = cfg.getInt("sunburn.helmet_integrity_loss", 1);
        extinguishWhenSafe = cfg.getBoolean("sunburn.extinguish_when_safe", true);

        excludedWorlds = new HashSet<>();
        if (cfg.isList("sunburn.excluded_worlds")) {
            excludedWorlds.addAll(cfg.getStringList("sunburn.excluded_worlds"));
        }

        ConsoleLogger.info("[SunburnModule] Config: enabled=" + enabled
                + ", checkInterval=" + checkInterval
                + ", fireTicks=" + fireTicks
                + ", helmetDegrade=" + helmetDegradeInterval
                + ", helmetLoss=" + helmetIntegrityLoss
                + ", extinguishWhenSafe=" + extinguishWhenSafe
                + ", excludedWorlds=" + excludedWorlds);
    }

    public void reloadConfig() {
        loadConfig();
    }

    // =========================
    // PUBLIC API
    // =========================

    public static boolean isEnabled() {
        return instance != null && instance.enabled;
    }

    // =========================
    // TICK (called from SunburnTask every 1 tick)
    // =========================

    public void tick() {
        if (!enabled) return;

        mainTickCounter++;
        if (mainTickCounter < checkInterval) return;
        mainTickCounter = 0;

        helmetTickCounter++;
        boolean doHelmetDegrade = false;
        if (helmetTickCounter >= helmetDegradeInterval) {
            helmetTickCounter = 0;
            doHelmetDegrade = true;
        }

        Set<UUID> nowExposed = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            if (excludedWorlds.contains(player.getWorld().getName())) continue;
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;
            if (player.isDead() || player.getHealth() <= 0) continue;

            boolean exposed = isExposedToSun(player);

            if (exposed) {
                nowExposed.add(player.getUniqueId());

                PlayerInventory inv = player.getInventory();
                ItemStack helmet = inv.getHelmet();
                boolean hasHelmet = helmet != null && helmet.getType() != Material.AIR;

                if (hasHelmet) {
                    if (doHelmetDegrade) {
                        degradeHelmet(player, helmet);
                    }
                } else {
                    // No helmet — set on fire
                    // Minecraft handles natural extinguishing (water, rain, etc.)
                    player.setFireTicks(fireTicks);
                }
            } else {
                // Not exposed — extinguish if previously exposed
                if (extinguishWhenSafe && exposedPlayers.contains(player.getUniqueId())) {
                    player.setFireTicks(0);
                }
            }
        }

        exposedPlayers.clear();
        exposedPlayers.addAll(nowExposed);
    }

    // =========================
    // EXPOSURE CHECK
    // =========================

    private boolean isExposedToSun(Player player) {
        World world = player.getWorld();

        // Daytime only (time 0-12000)
        long time = world.getTime();
        if (time < 0 || time >= 12000) return false;

        // Open sky above (light from sky >= 15)
        if (player.getLocation().getBlock().getLightFromSky() < 15) return false;

        return true;
    }

    // =========================
    // HELMET DEGRADATION
    // =========================

    private void degradeHelmet(Player player, ItemStack helmet) {
        if (IntegrityManager.isEnabled() && ItemIntegrityAPI.hasItemIntegrity(helmet)) {
            double remaining = ItemIntegrityAPI.decreaseItemIntegrity(helmet, helmetIntegrityLoss, player);

            if (remaining <= 0) {
                player.getInventory().setHelmet(null);
            }
        } else {
            short currentDmg = helmet.getDurability();
            short maxDur = helmet.getType().getMaxDurability();

            if (maxDur <= 0) return; // No durability — passive protection

            if (currentDmg >= maxDur) {
                player.getInventory().setHelmet(null);
            } else {
                helmet.setDurability((short) (currentDmg + 1));
            }
        }
    }

    // =========================
    // EVENTS
    // =========================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {}

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        exposedPlayers.remove(event.getPlayer().getUniqueId());
    }
}
