package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Dimension-driven gravity: instead of hooking teleport/join/death events
 * (which miss entry/exit paths like rockets or plugins), a periodic task
 * checks every online player's dimension every 2 seconds.
 * <ul>
 *   <li>in the space dimension → the gravity attribute is set to the
 *       configured low value ({@code space.gravity}, default 0.01 vs the
 *       vanilla 0.08 — x8 lighter, still walkable)</li>
 *   <li>anywhere else → restored to the vanilla default</li>
 * </ul>
 * The applied state is tracked in the player PDC so the attribute is only
 * touched when it actually has to change — and is always cleaned up on
 * leaving the dimension, no matter how the player got out.
 */
public class SpaceGravityListener {

    private static double spaceGravity = 0.01;
    private static final NamespacedKey KEY_GRAVITY_APPLIED =
            new NamespacedKey(Main.getInstance(), "space_gravity_applied");

    private static boolean running = false;
    private static BukkitTask task;

    /** Reloads gravity value from config. */
    public static void reloadConfig() {
        spaceGravity = Main.getInstance().getConfig().getDouble("space.gravity", 0.01);
    }

    public static void start(Main plugin) {
        if (running) return;
        running = true;

        // Dimension check every 2 seconds (40 ticks)
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!SpaceManager.isEnabled()) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (SpaceManager.isInSpace(player)) {
                        applySpaceGravity(player);
                    } else {
                        resetGravity(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    public static void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
            task = null;
        }
        running = false;
    }

    private static void applySpaceGravity(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(KEY_GRAVITY_APPLIED, PersistentDataType.BYTE)) return; // already applied

        try {
            AttributeInstance attr = player.getAttribute(Attribute.GRAVITY);
            if (attr != null) {
                attr.setBaseValue(spaceGravity);
                pdc.set(KEY_GRAVITY_APPLIED, PersistentDataType.BYTE, (byte) 1);
                SpaceManager.debugLog("Gravity set to " + spaceGravity + " for " + player.getName()
                        + " (default=" + Attribute.GRAVITY.getDefaultValue() + ")");
            } else {
                com.ultimateimprovments.util.ConsoleLogger.warn(
                        "[Space] Attribute.GRAVITY is null for " + player.getName() + "!");
            }
        } catch (Exception e) {
            com.ultimateimprovments.util.ConsoleLogger.error(
                    "[Space] Failed to apply gravity for " + player.getName() + ": " + e.getMessage());
        }
    }

    private static void resetGravity(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(KEY_GRAVITY_APPLIED, PersistentDataType.BYTE)) return; // was not applied

        try {
            pdc.remove(KEY_GRAVITY_APPLIED);
            AttributeInstance attr = player.getAttribute(Attribute.GRAVITY);
            if (attr != null) {
                attr.setBaseValue(Attribute.GRAVITY.getDefaultValue());
                SpaceManager.debugLog("Gravity reset for " + player.getName());
            }
        } catch (Exception e) {
            com.ultimateimprovments.util.ConsoleLogger.error(
                    "[Space] Failed to reset gravity for " + player.getName() + ": " + e.getMessage());
        }
    }
}
