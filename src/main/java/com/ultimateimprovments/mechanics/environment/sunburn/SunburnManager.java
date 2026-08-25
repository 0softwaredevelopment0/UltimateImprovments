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
 * ☀️ SunburnManager — players catch fire from sunlight in the Overworld.
 * <p>
 * Rules:
 * <ul>
 *   <li>Active during daytime (time 0–12000) in the Overworld only</li>
 *   <li>Player must have open sky above (no blocks blocking sunlight)</li>
 *   <li>If it's raining / thunder — fire is suppressed</li>
 *   <li>If the player wears a helmet — helmet loses integrity instead of fire</li>
 *   <li>If helmet integrity reaches 0 — it breaks and fire resumes</li>
 *   <li>When conditions are no longer met — player is immediately extinguished</li>
 * </ul>
 */
public class SunburnManager implements Listener {

    private static SunburnManager instance;

    // =========================
    // CONFIG
    // =========================
    private boolean enabled;
    private int fireTicks;                 // How long fire lasts per refresh (20 = 1s)
    private int fireRefreshInterval;       // How often to re-apply fire (ticks)
    private int helmetDegradeInterval;     // How often to degrade helmet (ticks)
    private int helmetIntegrityLoss;       // Integrity uses per degrade tick
    private boolean extinguishImmediately; // Extinguish when not in sun
    private boolean checkRain;             // Rain suppresses fire
    private boolean checkThunder;         // Thunder suppresses fire
    private boolean checkWater;           // Being submerged suppresses fire
    private Set<String> excludedWorlds;    // Worlds where sunburn is disabled

    // =========================
    // TICK COUNTERS
    // =========================
    private int fireTickCounter = 0;
    private int helmetTickCounter = 0;

    // =========================
    // TRACKING — players currently on fire from sunburn
    // =========================
    private final Set<UUID> sunburnedPlayers = new HashSet<>();

    public static SunburnManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new SunburnManager();
        instance.loadConfig();

        Main plugin = Main.getInstance();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        ConsoleLogger.info("[SunburnModule] ✔ Sunburn system initialized (fire mode).");
    }

    // =========================
    // CONFIG
    // =========================

    private void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("sunburn.enabled", true);
        fireTicks = cfg.getInt("sunburn.fire_ticks", 40);
        fireRefreshInterval = cfg.getInt("sunburn.fire_refresh_interval", 20);
        helmetDegradeInterval = cfg.getInt("sunburn.helmet_degrade_interval", 80);
        helmetIntegrityLoss = cfg.getInt("sunburn.helmet_integrity_loss", 1);
        extinguishImmediately = cfg.getBoolean("sunburn.extinguish_immediately", true);
        checkRain = cfg.getBoolean("sunburn.check_rain", true);
        checkThunder = cfg.getBoolean("sunburn.check_thunder", true);
        checkWater = cfg.getBoolean("sunburn.check_water", true);

        excludedWorlds = new HashSet<>();
        if (cfg.isList("sunburn.excluded_worlds")) {
            excludedWorlds.addAll(cfg.getStringList("sunburn.excluded_worlds"));
        }

        ConsoleLogger.info("[SunburnModule] Config loaded:"
                + " enabled=" + enabled
                + ", fireTicks=" + fireTicks
                + ", fireRefresh=" + fireRefreshInterval
                + ", helmetDegrade=" + helmetDegradeInterval
                + ", helmetLoss=" + helmetIntegrityLoss
                + ", extinguish=" + extinguishImmediately
                + ", checkRain=" + checkRain
                + ", checkThunder=" + checkThunder
                + ", checkWater=" + checkWater
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

        boolean doFire = false;
        boolean doHelmet = false;

        fireTickCounter++;
        if (fireTickCounter >= fireRefreshInterval) {
            fireTickCounter = 0;
            doFire = true;
        }

        helmetTickCounter++;
        if (helmetTickCounter >= helmetDegradeInterval) {
            helmetTickCounter = 0;
            doHelmet = true;
        }

        // Track which players are currently exposed (for extinguishing)
        Set<UUID> currentlyExposed = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only Overworld
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;

            // Excluded worlds
            if (excludedWorlds.contains(player.getWorld().getName())) continue;

            // Only survival / adventure
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;

            // Skip dead players
            if (player.isDead() || player.getHealth() <= 0) continue;

            // Check if player is exposed to sun
            boolean exposed = isExposedToSun(player);

            if (exposed) {
                currentlyExposed.add(player.getUniqueId());

                // Apply fire or degrade helmet
                if (doFire || doHelmet) {
                    applySunburn(player, doHelmet);
                }
            } else if (extinguishImmediately) {
                // Player is NOT exposed — extinguish if we set them on fire before
                if (sunburnedPlayers.contains(player.getUniqueId())) {
                    player.setFireTicks(0);
                    sunburnedPlayers.remove(player.getUniqueId());
                }
            }
        }

        // Also extinguish players who logged off sunburn list
        sunburnedPlayers.retainAll(currentlyExposed);
    }

    // =========================
    // EXPOSURE CHECK
    // =========================

    /**
     * Returns true if the player is exposed to sunlight and should burn.
     */
    private boolean isExposedToSun(Player player) {
        World world = player.getWorld();

        // Must be daytime
        if (!isDaytime(world)) return false;

        // Must have open sky above
        if (!hasOpenSkyAbove(player)) return false;

        // Rain check
        if (checkRain && world.hasStorm()) return false;

        // Thunder check
        if (checkThunder && world.isThundering()) return false;

        // Water check — player submerged in water
        if (checkWater && player.isInWater()) return false;

        // Also check if player is in rain (exposed to sky + storm = in rain)
        // Minecraft handles rain extinguishing natively, but we also check here
        if (checkRain && world.hasStorm() && hasOpenSkyAbove(player)) return false;

        return true;
    }

    // =========================
    // SUNBURN LOGIC
    // =========================

    private void applySunburn(Player player, boolean doHelmetDegrade) {
        PlayerInventory inv = player.getInventory();
        ItemStack helmet = inv.getHelmet();

        if (helmet != null && helmet.getType() != Material.AIR) {
            // Helmet is equipped — try to absorb sunburn
            if (doHelmetDegrade) {
                absorbWithHelmet(player, helmet);
            }
            // If not degrade tick — helmet absorbs without damage
            // Player does NOT catch fire while helmet is protecting
        } else {
            // No helmet — set on fire
            setSunburnFire(player);
        }
    }

    /**
     * Sets the player on fire for the configured duration.
     * This is called on each fire refresh interval.
     */
    private void setSunburnFire(Player player) {
        player.setFireTicks(fireTicks);
        sunburnedPlayers.add(player.getUniqueId());
    }

    private void absorbWithHelmet(Player player, ItemStack helmet) {
        // Check if the helmet has integrity in the system
        if (IntegrityManager.isEnabled() && ItemIntegrityAPI.hasItemIntegrity(helmet)) {
            // Use integrity system: decrease by configured uses
            double remaining = ItemIntegrityAPI.decreaseItemIntegrity(helmet, helmetIntegrityLoss, player);

            if (remaining <= 0) {
                // Helmet broke — set on fire now
                setSunburnFire(player);
            }
            // else: helmet absorbed the hit — no fire
        } else {
            // No integrity system or helmet not tracked — use vanilla durability
            absorbWithVanillaDurability(player, helmet);
        }
    }

    private void absorbWithVanillaDurability(Player player, ItemStack helmet) {
        short currentDmg = helmet.getDurability();
        short maxDur = helmet.getType().getMaxDurability();

        if (maxDur <= 0) {
            // Helmet has no durability (leather cap etc.) — it just protects passively
            return;
        }

        if (currentDmg >= maxDur) {
            // Already broken — remove and set fire
            player.getInventory().setHelmet(null);
            setSunburnFire(player);
        } else {
            // Damage the helmet by 1 vanilla durability
            helmet.setDurability((short) (currentDmg + 1));
        }
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Checks if the given world is in daytime (time 0–12000).
     */
    private boolean isDaytime(World world) {
        long time = world.getTime();
        return time >= 0 && time < 12000;
    }

    /**
     * Checks if the player has open sky above (no opaque blocks blocking sunlight).
     * Uses Paper's canSeeSky() which checks light level from sky.
     */
    private boolean hasOpenSkyAbove(Player player) {
        return player.getLocation().getBlock().getLightFromSky() >= 15;
    }

    // =========================
    // EVENTS
    // =========================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // No persistence needed — sunburn is real-time
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sunburnedPlayers.remove(event.getPlayer().getUniqueId());
    }
}
