package com.ultimateimprovments.util;

import com.ultimateimprovments.util.Registries;

import com.ultimateimprovments.util.Registries;
import io.papermc.paper.registry.RegistryAccess;
import com.ultimateimprovments.util.Registries;
import io.papermc.paper.registry.RegistryKey;
import com.ultimateimprovments.util.Registries;
import org.bukkit.JukeboxSong;
import com.ultimateimprovments.util.Registries;
import org.bukkit.MusicInstrument;
import com.ultimateimprovments.util.Registries;
import org.bukkit.Registry;
import com.ultimateimprovments.util.Registries;
import org.bukkit.block.banner.PatternType;
import com.ultimateimprovments.util.Registries;
import org.bukkit.enchantments.Enchantment;
import com.ultimateimprovments.util.Registries;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import com.ultimateimprovments.util.Registries;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Modern (Paper 26.3+) registry access points. The legacy static constants on
 * {@code org.bukkit.Registry} ({@code Registries.enchantment()},
 * {@code Registries.bannerPattern()}, ...) are deprecated for removal — every
 * lookup must go through {@link RegistryAccess#getRegistry(RegistryKey)}.
 */
public final class Registries {

    private Registries() {
    }

    /** Modern access to the enchantment registry. */
    public static Registry<Enchantment> enchantment() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    /** Modern access to the banner pattern registry. */
    public static Registry<PatternType> bannerPattern() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN);
    }

    /** Modern access to the trim material registry. */
    public static Registry<TrimMaterial> trimMaterial() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
    }

    /** Modern access to the trim pattern registry. */
    public static Registry<TrimPattern> trimPattern() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
    }

    /** Modern access to the jukebox song registry. */
    public static Registry<JukeboxSong> jukeboxSong() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.JUKEBOX_SONG);
    }

    /** Modern access to the music instrument registry. */
    public static Registry<MusicInstrument> instrument() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT);
    }

    /** Modern access to the sound event registry. */
    public static Registry<org.bukkit.Sound> soundEvent() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
    }
}
