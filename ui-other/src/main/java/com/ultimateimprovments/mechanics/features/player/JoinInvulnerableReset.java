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

import net.minecraft.server.level.ServerPlayer;

/**
 * 🛡 JoinInvulnerableReset — defensive fix for the "immortal player" bug.
 * <p>
 * A stale god-mode flag ({@code Invulnerable: 1b} in the player NBT, visible
 * via {@code /data get entity <nick> Invulnerable}) survives into player.dat
 * and makes a player permanently immune to ALL damage after relogs/restarts.
 * This feature forces the flag back to {@code 0b} for every player on join,
 * guaranteeing no one spawns immortal no matter what left the flag set.
 * <p>
 * Disabled by default — normal servers never need it (vanilla and this plugin
 * already clear the flag on the regular paths). Enable it only if you actually
 * hit the immortality bug or want the guarantee.
 * <p>
 * Config ({@code join_invulnerable_reset}, root level):
 * <ul>
 *   <li>{@code enabled} — master switch, default {@code false};</li>
 *   <li>{@code notify_console} — log every reset to the console, default {@code true}.</li>
 * </ul>
 * Write path: {@code setInvulnerable(false)} through the NMS handle (the
 * programmatic equivalent of {@code data merge entity <player> {Invulnerable:0b}}),
 * then {@code saveData()} so the cleared value reaches player.dat immediately.
 */
public class JoinInvulnerableReset implements Listener {

    private static JoinInvulnerableReset instance;

    private boolean enabled;
    private boolean notifyConsole;

    public static void init(Main plugin) {
        instance = new JoinInvulnerableReset();
        instance.loadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        ConsoleLogger.info("[JoinInvulnerableReset] " + (instance.enabled
                ? "ENABLED — Invulnerable is forced to 0b on every join."
                : "disabled."));
    }

    public static void reloadConfig() {
        if (instance != null) instance.loadConfig();
    }

    private void loadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("join_invulnerable_reset");
        if (cfg == null) {
            enabled = false;
            notifyConsole = true;
            return;
        }
        enabled = cfg.getBoolean("enabled", false);
        notifyConsole = cfg.getBoolean("notify_console", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();

        if (!player.isInvulnerable()) return; // nothing to fix

        // NMS write — the API equivalent of `data merge entity <player> {Invulnerable:0b}`
        if (player instanceof CraftPlayer craftPlayer) {
            try {
                ServerPlayer nmsPlayer = craftPlayer.getHandle();
                nmsPlayer.setInvulnerable(false);
                craftPlayer.saveData();
                if (notifyConsole) {
                    ConsoleLogger.warn("[JoinInvulnerableReset] Cleared a stale Invulnerable flag for "
                            + player.getName() + " on join.");
                }
            } catch (Throwable t) {
                ConsoleLogger.error("[JoinInvulnerableReset] Failed to reset Invulnerable for "
                        + player.getName() + ": " + t.getMessage());
            }
        }
    }
}
