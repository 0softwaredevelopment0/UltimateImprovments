package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.minecraft.server.level.ServerPlayer;

/**
 * 🛡 JoinInvulnerableReset — defensive fix for the "immortal player" bug.
 * <p>
 * A stale god-mode flag ({@code Invulnerable: 1b} in the player NBT, visible
 * via {@code /data get entity <nick> Invulnerable}) survives into player.dat
 * and makes a player permanently immune to ALL damage after relogs/restarts.
 * This feature forces the flag back to {@code 0b} on a schedule, guaranteeing
 * no one stays immortal no matter what left the flag set.
 * <p>
 * Two modes (config {@code join_invulnerable_reset.mode}):
 * <ul>
 *   <li>{@code join} (default) — reset on every player JOIN;</li>
 *   <li>{@code timed} — reset every {@code interval_ticks} (default 2000 = 100s)
 *       for all online players; the join listener stays active in this mode too
 *       (a fresh join is also a fresh immortal flag risk).</li>
 * </ul>
 * In {@code join} mode the {@code interval_ticks} constant is ignored.
 * <p>
 * Disabled by default — normal servers never need it (vanilla and this plugin
 * already clear the flag on the regular paths). Enable it only if you actually
 * hit the immortality bug or want the guarantee.
 * <p>
 * Config ({@code join_invulnerable_reset}, root level):
 * <ul>
 *   <li>{@code enabled} — master switch, default {@code false};</li>
 *   <li>{@code mode} — {@code join} | {@code timed}, default {@code join};</li>
 *   <li>{@code interval_ticks} — timed-mode period, default {@code 2000};</li>
 *   <li>{@code notify_console} — log every reset, default {@code true}.</li>
 * </ul>
 * Write path: {@code setInvulnerable(false)} through the NMS handle (the
 * programmatic equivalent of {@code data merge entity <player> {Invulnerable:0b}}),
 * then {@code saveData()} so the cleared value reaches player.dat immediately.
 */
public class JoinInvulnerableReset implements Listener {

    private static JoinInvulnerableReset instance;

    private boolean enabled;
    private boolean timedMode;
    private int intervalTicks;
    private boolean notifyConsole;

    /** The repeating timed task (timed mode only). Cancelled on reload. */
    private BukkitRunnable timedTask;

    public static void init(Main plugin) {
        instance = new JoinInvulnerableReset();
        instance.loadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        ConsoleLogger.info("[JoinInvulnerableReset] " + (instance.enabled
                ? "ENABLED — mode=" + (instance.timedMode ? "timed (every " + instance.intervalTicks + " ticks)" : "join")
                : "disabled."));
    }

    public static void reloadConfig() {
        if (instance != null) instance.reload();
    }

    private void reload() {
        cancelTimedTask();
        loadConfig();
        if (enabled && timedMode) scheduleTimedTask();
    }

    private void loadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("join_invulnerable_reset");
        if (cfg == null) {
            enabled = false;
            timedMode = false;
            intervalTicks = 2000;
            notifyConsole = true;
            return;
        }
        enabled = cfg.getBoolean("enabled", false);
        String mode = cfg.getString("mode", "join").trim().toLowerCase(java.util.Locale.ROOT);
        timedMode = "timed".equals(mode);
        intervalTicks = Math.max(20, cfg.getInt("interval_ticks", 2000));
        notifyConsole = cfg.getBoolean("notify_console", true);
    }

    /** Schedules the timed sweep (timed mode only). */
    private void scheduleTimedTask() {
        timedTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || !timedMode) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    resetIfStale(player);
                }
            }
        };
        timedTask.runTaskTimer(Main.getInstance(), intervalTicks, intervalTicks);
    }

    private void cancelTimedTask() {
        if (timedTask != null) {
            try {
                timedTask.cancel();
            } catch (IllegalStateException ignored) {
                // already cancelled
            }
            timedTask = null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  JOIN MODE
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        resetIfStale(event.getPlayer());
    }

    // ─────────────────────────────────────────────────────────────
    //  RESET
    // ─────────────────────────────────────────────────────────────

    /**
     * Forces {@code Invulnerable} to 0b if it is currently set (NMS write +
     * immediate saveData). No-op when the flag is already clear.
     */
    private void resetIfStale(Player player) {
        if (!player.isInvulnerable()) return; // nothing to fix

        // NMS write — the API equivalent of `data merge entity <player> {Invulnerable:0b}`
        if (player instanceof CraftPlayer craftPlayer) {
            try {
                ServerPlayer nmsPlayer = craftPlayer.getHandle();
                nmsPlayer.setInvulnerable(false);
                craftPlayer.saveData();
                if (notifyConsole) {
                    ConsoleLogger.warn("[JoinInvulnerableReset] Cleared a stale Invulnerable flag for "
                            + player.getName() + (timedMode ? " (timed sweep)." : " on join."));
                }
            } catch (Throwable t) {
                ConsoleLogger.error("[JoinInvulnerableReset] Failed to reset Invulnerable for "
                        + player.getName() + ": " + t.getMessage());
            }
        }
    }
}
