package com.ultimateimprovments.mechanics.environment.radiation;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ☢ DosimeterTask — action bar readout for players holding a Dosimeter.
 * <p>
 * Every {@value #INTERVAL_TICKS} ticks, any player holding the dosimeter in
 * the main hand OR the off hand sees:
 * <pre>D: &lt;dose&gt; mSv   R: &lt;rate&gt; mSv/t</pre>
 * where:
 * <ul>
 *   <li><b>D</b> — the dose the player has absorbed so far (their current
 *       radiation value converted from the internal R/h units to mSv);</li>
 *   <li><b>R</b> — the current dose rate: how much radiation the player is
 *       picking up per tick right now (inventory debris, biomes, the End,
 *       space, reactors...), in mSv/tick.</li>
 * </ul>
 * Not holding the dosimeter → no action bar (the {@code /ui radview} toggle
 * is a separate admin-ish display and is unaffected).
 * <p>
 * The rate sample is fed by {@link RadiationManager#tick()} via
 * {@link #recordRate(UUID, double)} — the manager computes the per-tick delta
 * for every player anyway, so the dosimeter reuses it for free.
 */
public class DosimeterTask extends BukkitRunnable {

    /** Action bar refresh interval: 10 ticks = 0.5 s (responsive, no spam). */
    static final long INTERVAL_TICKS = 10L;

    /** Internal radiation units → mSv multiplier (config radiation.dosimeter_ms_per_unit). */
    private static double msPerUnit = 1.0;

    /** Last computed rate (internal units per tick) per player, sampled in RadiationManager.tick(). */
    private static final Map<UUID, Double> LAST_RATE = new ConcurrentHashMap<>();

    /** Registered flag so the manager feeds rates only when a dosimeter exists. */
    private static volatile boolean registered = false;

    /** The running task instance (kept to cancel on reload — init is idempotent). */
    private static DosimeterTask running;

    public static void init(Main plugin, double msPerUnitConfig) {
        // Idempotent init: cancel a previous instance first (module reload).
        shutdown();
        msPerUnit = msPerUnitConfig;
        registered = true;
        running = new DosimeterTask();
        running.runTaskTimer(plugin, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    public static void shutdown() {
        registered = false;
        if (running != null) {
            try { running.cancel(); } catch (Exception ignored) { }
            running = null;
        }
        LAST_RATE.clear();
    }

    /**
     * Called by {@link RadiationManager#tick()} with the net radiation delta
     * (before decay) the player gained this second, in internal units.
     * Stored as a per-tick rate for the R readout.
     */
    public static void recordRate(UUID playerId, double gainedUnits) {
        if (!registered) return;
        LAST_RATE.put(playerId, gainedUnits / 20.0); // units per second → units per tick
    }

    /** Drops the stored rate (player quit / death reset). */
    public static void clearRate(UUID playerId) {
        LAST_RATE.remove(playerId);
    }

    @Override
    public void run() {
        if (!registered) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (!holdsDosimeter(player)) continue;

                // D — absorbed dose: current radiation value in mSv.
                double doseMs = RadiationManager.getRadiation(player) * msPerUnit;

                // R — current rate in mSv/tick.
                double rateMs = LAST_RATE.getOrDefault(player.getUniqueId(), 0.0) * msPerUnit;

                player.sendActionBar(MessageUtil.parse(String.format(Locale.US,
                        "<white>D: <yellow>%.1f<white> mSv R: <yellow>%.2f<white> mSv/t",
                        doseMs, rateMs)));
            } catch (Exception ignored) {
                // Never let a readout error kill the task.
            }
        }
    }

    /** True if the player holds a dosimeter in the main hand or the off hand. */
    static boolean holdsDosimeter(Player player) {
        return isDosimeter(player.getInventory().getItemInMainHand())
                || isDosimeter(player.getInventory().getItemInOffHand());
    }

    private static boolean isDosimeter(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.DOSIMETER, PersistentDataType.BYTE);
    }
}
