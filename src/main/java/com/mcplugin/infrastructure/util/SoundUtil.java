package com.mcplugin.infrastructure.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/**
 * Utility class for looking up sounds by name.
 * <p>
 * Modern Paper 1.21+ uses the sound registry ({@link Registry#SOUNDS}) instead of
 * the deprecated {@link Sound#valueOf(String)} enum method.
 * This class provides a bridge that tries the registry first, then falls back
 * to the enum method for backward compatibility with old config values.
 */
public final class SoundUtil {

    private SoundUtil() {
        // Utility class — no instances
    }

    /**
     * Looks up a {@link Sound} by its name. Supports both:
     * <ul>
     *   <li>Modern registry keys: {@code "block.note_block.pling"}</li>
     *   <li>Old enum-style names: {@code "BLOCK_NOTE_BLOCK_PLING"}</li>
     * </ul>
     *
     * @param name the sound name (may be null)
     * @return the matching {@link Sound}, or {@code null} if not found
     */
    public static Sound getSound(String name) {
        return getSound(name, null);
    }

    /**
     * Looks up a {@link Sound} by its name, returning a fallback on failure.
     *
     * @param name     the sound name (may be null)
     * @param fallback the default sound to return if the name is not recognised
     * @return the matching {@link Sound}, or {@code fallback} if not found
     */
    public static Sound getSound(String name, Sound fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }

        // 1. Try modern registry lookup (lowercase, dots instead of underscores)
        String registryKey = name.toLowerCase().replace('_', '.');
        Sound fromRegistry = Registry.SOUNDS.get(NamespacedKey.minecraft(registryKey));
        if (fromRegistry != null) {
            return fromRegistry;
        }

        // 2. Fallback to old enum-style (deprecated but still functional)
        try {
            @SuppressWarnings("deprecation")
            Sound fromEnum = Sound.valueOf(name);
            return fromEnum;
        } catch (IllegalArgumentException ignored) {
            // Not found — return fallback
        }

        return fallback;
    }

    /**
     * Plays a sound to a player safely, ignoring failures gracefully.
     *
     * @param player the player to play the sound for
     * @param sound  the sound to play (null-safe)
     * @param volume the volume
     * @param pitch  the pitch
     */
    public static void playSound(org.bukkit.entity.Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
