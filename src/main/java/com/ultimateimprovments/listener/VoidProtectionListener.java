package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;

/**
 * Protection from falling into the void.
 * If a player takes void damage (VOID) in one of the protected worlds,
 * they are teleported to the specified world at the given coordinates.
 *
 * Settings in config.yml → void_protection
 */
public class VoidProtectionListener implements Listener {

    @EventHandler
    public void onVoidDamage(EntityDamageEvent e) {

        // =========================
        // Only players
        // =========================
        if (!(e.getEntity() instanceof Player player)) return;

        // =========================
        // Only void damage
        // =========================
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        // =========================
        // Check: is the protection enabled
        // =========================
        if (!Main.getInstance().getConfig().getBoolean("void_protection.enabled", false)) return;

        // =========================
        // Check: is the player's world in the protected list
        // =========================
        List<String> protectedWorlds = Main.getInstance().getConfig()
                .getStringList("void_protection.worlds");

        String playerWorldName = player.getWorld().getName();

        boolean isProtected = false;
        for (String worldName : protectedWorlds) {
            if (worldName.equalsIgnoreCase(playerWorldName)) {
                isProtected = true;
                break;
            }
        }

        if (!isProtected) return;

        // =========================
        // Cancel the void damage
        // =========================
        e.setCancelled(true);

        // =========================
        // Get the target point
        // =========================
        ConfigurationSection targetSection = Main.getInstance().getConfig()
                .getConfigurationSection("void_protection.target");

        if (targetSection == null) {
            ConsoleLogger.warn("[VOID] void_protection.target не настроен в config.yml!");
            return;
        }

        String targetWorldName = targetSection.getString("world", "world");
        World targetWorld = Main.getInstance().getServer().getWorld(targetWorldName);

        if (targetWorld == null) {
            ConsoleLogger.warn("[VOID] Мир " + targetWorldName + " не найден!");
            return;
        }

        double x = targetSection.getDouble("x", 0);
        double y = targetSection.getDouble("y", 64);
        double z = targetSection.getDouble("z", 0);
        float yaw = (float) targetSection.getDouble("yaw", 0.0);
        float pitch = (float) targetSection.getDouble("pitch", 0.0);

        Location targetLocation = new Location(targetWorld, x, y, z, yaw, pitch);

        // =========================
        // Teleport + effects after arrival
        // =========================
        player.teleportAsync(targetLocation).thenAccept(success -> {
            if (!success) return;

            // Particles
            targetWorld.spawnParticle(
                    Particle.END_ROD,
                    targetLocation,
                    80, 0.5, 1.0, 0.5, 0.15
            );
            targetWorld.spawnParticle(
                    Particle.PORTAL,
                    targetLocation,
                    60, 0.5, 1.0, 0.5, 0.3
            );
            targetWorld.spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    targetLocation,
                    40, 0.5, 1.0, 0.5, 0.5
            );

            // Sound
            targetWorld.playSound(
                    targetLocation,
                    Sound.ENTITY_ENDERMAN_TELEPORT,
                    1.0f, 1.0f
            );
            player.playSound(
                    targetLocation,
                    Sound.ENTITY_PLAYER_LEVELUP,
                    0.5f, 1.5f
            );

            // Message to the player
            String message = MessagesManager.getString("void_protection.message", "<green>✔</green> <white>Вы были спасены из пустоты!</white>");
            player.sendMessage(MessageUtil.parse(message));
        });
    }
}
