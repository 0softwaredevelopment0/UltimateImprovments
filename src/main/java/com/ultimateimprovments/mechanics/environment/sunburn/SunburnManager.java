package com.ultimateimprovments.mechanics.environment.sunburn;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * ☀️ SunburnManager — players take damage from sunlight in the Overworld.
 * <p>
 * Rules:
 * <ul>
 *   <li>Active during daytime (time 0–12000) in the Overworld only</li>
 *   <li>Player must have open sky above (no blocks blocking sunlight)</li>
 *   <li>If the player wears a helmet — helmet loses 1 integrity use instead</li>
 *   <li>If helmet integrity reaches 0 — it breaks and sunburn resumes</li>
 * </ul>
 */
public class SunburnManager implements Listener {

    private static SunburnManager instance;

    // =========================
    // CONFIG
    // =========================
    private boolean enabled;
    private double damageAmount;
    private int damageIntervalTicks;
    private int helmetIntegrityLoss;

    // =========================
    // TICK COUNTER
    // =========================
    private int tickCounter = 0;

    public static SunburnManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new SunburnManager();
        instance.loadConfig();

        Main plugin = Main.getInstance();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);

        ConsoleLogger.info("[SunburnModule] ✔ Sunburn system initialized.");
    }

    // =========================
    // CONFIG
    // =========================

    private void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("sunburn.enabled", true);
        damageAmount = cfg.getDouble("sunburn.damage_amount", 1.0);
        damageIntervalTicks = cfg.getInt("sunburn.damage_interval_ticks", 80);
        helmetIntegrityLoss = cfg.getInt("sunburn.helmet_integrity_loss", 1);

        ConsoleLogger.info("[SunburnModule] Config: enabled=" + enabled
                + ", damage=" + damageAmount
                + ", interval=" + damageIntervalTicks + " ticks"
                + ", helmet_loss=" + helmetIntegrityLoss + " use(s)");
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

        tickCounter++;
        if (tickCounter < damageIntervalTicks) return;
        tickCounter = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only Overworld
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;

            // Only survival / adventure
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;

            // Skip dead players
            if (player.isDead() || player.getHealth() <= 0) continue;

            // Check if it's daytime
            if (!isDaytime(player.getWorld())) continue;

            // Check if player has open sky above
            if (!hasOpenSkyAbove(player)) continue;

            // Player is exposed to sunlight — apply sunburn
            applySunburn(player);
        }
    }

    // =========================
    // SUNBURN LOGIC
    // =========================

    private void applySunburn(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack helmet = inv.getHelmet();

        if (helmet != null && helmet.getType() != Material.AIR) {
            // Helmet is equipped — try to absorb sunburn with integrity
            absorbWithHelmet(player, helmet);
        } else {
            // No helmet — deal direct damage
            dealDirectDamage(player);
        }
    }

    private void absorbWithHelmet(Player player, ItemStack helmet) {
        // Check if the helmet has integrity in the system
        if (IntegrityManager.isEnabled() && ItemIntegrityAPI.hasItemIntegrity(helmet)) {
            // Use integrity system: decrease by 1 use
            double remaining = ItemIntegrityAPI.decreaseItemIntegrity(helmet, helmetIntegrityLoss, player);

            if (remaining <= 0) {
                // Helmet broke — deal damage to player now
                dealDirectDamage(player);
            }
            // else: helmet absorbed the hit — no damage to player
        } else {
            // No integrity system or helmet not tracked — use vanilla durability
            absorbWithVanillaDurability(player, helmet);
        }
    }

    private void absorbWithVanillaDurability(Player player, ItemStack helmet) {
        int maxDur = helmet.getType().getMaxDurability();
        if (maxDur <= 0) {
            // Helmet has no durability (leather, etc.) — check vanilla damage
            int currentDmg = helmet.getDurability();
            if (currentDmg >= maxDur) {
                // Already broken
                player.getInventory().setHelmet(null);
                dealDirectDamage(player);
            } else {
                // Damage the helmet by 1 vanilla durability
                helmet.setDurability((short) (currentDmg + 1));
            }
        } else {
            // Helmet has durability — damage it
            short currentDmg = helmet.getDurability();
            if (currentDmg >= maxDur) {
                // Already broken — remove and deal damage
                player.getInventory().setHelmet(null);
                dealDirectDamage(player);
            } else {
                helmet.setDurability((short) (currentDmg + 1));
            }
        }
    }

    private void dealDirectDamage(Player player) {
        // Use player.damage(double) — applies even if wearing armor
        // The helmet check already happened in applySunburn() before reaching here
        player.damage(damageAmount);
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
        // canSeeSky() returns true if the block position receives sky light
        // This is exactly what we need — if there are no blocks above blocking the sun
        return player.getLocation().getBlock().getLightFromSky() >= 15;
    }

    // =========================
    // EVENTS
    // =========================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // No persistence needed — sunburn is real-time, not accumulated
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // No cleanup needed
    }
}
