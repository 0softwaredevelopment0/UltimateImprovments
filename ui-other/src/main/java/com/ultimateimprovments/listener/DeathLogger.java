package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeathLogger — records every player death to {@code deaths.log} in the plugin folder
 * and optionally to the console. Controlled by config.yml:
 * <pre>
 * death_logger:
 *   enabled: false        # master switch (off by default — enable for debugging)
 *   log_to_console: true  # also print each death to the console
 * </pre>
 * <p>
 * The log line contains everything needed to diagnose a death:
 * timestamp, death message, damage cause, final damage, the player's fall distance
 * at the moment of the fatal hit, world, coordinates and the attacker (if any).
 * <p>
 * Fall distance is captured BEFORE the damage is applied (on
 * {@link EntityDamageEvent}, LOWEST priority) — by the time
 * {@link PlayerDeathEvent} fires, the game has already reset it to 0.
 */
public class DeathLogger implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Main plugin;
    private static File logFile;
    private static boolean enabled = false;
    private static boolean logToConsole = true;
    private static boolean registered = false;

    /** The single listener instance (used to unregister it on disable). */
    private static DeathLogger instance;

    /** Player → fall distance at the moment of the last damage event. */
    private static final Map<UUID, Float> lastFallDistance = new ConcurrentHashMap<>();

    private DeathLogger() {}

    /**
     * Reads the config and registers the listener if {@code death_logger.enabled} is true.
     */
    public static void init(Main plugin) {
        DeathLogger.plugin = plugin;
        reloadConfig();
    }

    /**
     * Re-reads the config (also called on /ui reload). Toggles the listener and
     * the console output accordingly.
     */
    public static void reloadConfig() {
        if (plugin == null) return;

        var cfg = plugin.getConfig().getConfigurationSection("death_logger");
        enabled = cfg != null && cfg.getBoolean("enabled", false);
        logToConsole = cfg == null || cfg.getBoolean("log_to_console", true);

        if (enabled) {
            if (logFile == null) {
                logFile = new File(plugin.getDataFolder(), "deaths.log");
                try {
                    if (logFile.getParentFile() != null) {
                        logFile.getParentFile().mkdirs();
                    }
                    if (!logFile.exists()) {
                        logFile.createNewFile();
                    }
                } catch (IOException e) {
                    ConsoleLogger.warn("[DeathLogger] Cannot create deaths.log: " + e.getMessage());
                    logFile = null;
                }
            }
            if (!registered) {
                instance = new DeathLogger();
                plugin.getServer().getPluginManager().registerEvents(instance, plugin);
                registered = true;
                ConsoleLogger.info("[DeathLogger] Enabled — deaths are written to deaths.log"
                        + (logToConsole ? " and the console." : "."));
            }
        } else if (registered && instance != null) {
            PlayerDeathEvent.getHandlerList().unregister(instance);
            EntityDamageEvent.getHandlerList().unregister(instance);
            registered = false;
            lastFallDistance.clear();
            ConsoleLogger.info("[DeathLogger] Disabled.");
        }
    }

    /**
     * LOWEST priority: capture the fall distance while the damage event still
     * holds it, so we can report it in the death record.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        lastFallDistance.put(player.getUniqueId(), player.getFallDistance());
    }

    /**
     * MONITOR priority: run after everything else so the log reflects the final state.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled) return;
        Player player = event.getEntity();
        String name = player.getName();

        try {
            Location loc = player.getLocation();
            String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();

            // Death message (vanilla text, e.g. "fell from a high place")
            String msg = event.deathMessage() == null ? "?" : PLAIN.serialize(event.deathMessage());

            // Last damage info
            String cause = "?";
            double finalDamage = 0.0;
            String attacker = "-";
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            if (lastDamage != null) {
                cause = lastDamage.getCause().name();
                finalDamage = lastDamage.getFinalDamage();
                if (lastDamage instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() != null) {
                    var damager = byEntity.getDamager();
                    attacker = damager.getType().name() + " (\"" + damager.getName() + "\")";
                }
            }

            Float fallDist = lastFallDistance.remove(player.getUniqueId());
            String fallStr = fallDist == null ? "0.0" : String.format("%.2f", fallDist);

            String line = String.format(
                    "[%s] %s | msg=\"%s\" | cause=%s | dmg=%.2f | fallDist=%s | world=%s | x=%.1f y=%.1f z=%.1f | attacker=%s",
                    TS.format(new Date()), name, msg, cause, finalDamage, fallStr,
                    world, loc.getX(), loc.getY(), loc.getZ(), attacker);

            if (logToConsole) {
                ConsoleLogger.warn("[DeathLogger] " + line);
            }
            if (logFile != null) {
                append(line);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[DeathLogger] Failed to log death of " + name + ": " + e.getMessage());
        }
    }

    private static void append(String line) {
        if (logFile == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(line);
        } catch (IOException e) {
            ConsoleLogger.warn("[DeathLogger] Write failed: " + e.getMessage());
        }
    }
}
