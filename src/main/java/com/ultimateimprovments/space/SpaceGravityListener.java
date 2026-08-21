package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

/**
 * Applies the gravity attribute (0.01 by default) to players every time they
 * teleport into the space dimension. Resets to default on leave.
 * <p>
 * Default Minecraft gravity is 0.08; space uses 0.01 (x8 lighter)
 * for a low-gravity feel that still allows walking.
 */
public class SpaceGravityListener implements Listener {

    private static double spaceGravity = 0.01;
    private static final NamespacedKey KEY_GRAVITY_APPLIED = new NamespacedKey(Main.getInstance(), "space_gravity_applied");

    /** Reloads gravity value from config. */
    public static void reloadConfig() {
        spaceGravity = Main.getInstance().getConfig().getDouble("space.gravity", 0.01);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!SpaceManager.isEnabled()) return;

        // Check if arriving in space
        if (event.getTo() != null && SpaceManager.isInSpace(event.getTo().getWorld())) {
            applySpaceGravity(player);
        }
        // Check if leaving space
        if (event.getFrom().getWorld() != null && SpaceManager.isInSpace(event.getFrom().getWorld())) {
            resetGravity(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // If player somehow logs in while in space
        Player player = event.getPlayer();
        if (SpaceManager.isInSpace(player)) {
            applySpaceGravity(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // Reset gravity on death so respawning works correctly
        resetGravity(event.getEntity());
    }

    private void applySpaceGravity(Player player) {
        try {
            AttributeInstance attr = player.getAttribute(Attribute.GRAVITY);
            if (attr != null) {
                attr.setBaseValue(spaceGravity);
                PersistentDataContainer pdc = player.getPersistentDataContainer();
                pdc.set(KEY_GRAVITY_APPLIED, PersistentDataType.BYTE, (byte) 1);
                ConsoleLogger.info("[Space] Gravity set to " + spaceGravity + " for " + player.getName()
                        + " (default=" + Attribute.GRAVITY.getDefaultValue() + ")");
            } else {
                ConsoleLogger.warn("[Space] Attribute.GRAVITY is null for " + player.getName() + "!");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Space] Failed to apply gravity for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetGravity(Player player) {
        try {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            if (pdc.has(KEY_GRAVITY_APPLIED, PersistentDataType.BYTE)) {
                pdc.remove(KEY_GRAVITY_APPLIED);
                AttributeInstance attr = player.getAttribute(Attribute.GRAVITY);
                if (attr != null) {
                    attr.setBaseValue(Attribute.GRAVITY.getDefaultValue());
                    ConsoleLogger.info("[Space] Gravity reset for " + player.getName());
                }
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Space] Failed to reset gravity for " + player.getName() + ": " + e.getMessage());
        }
    }
}