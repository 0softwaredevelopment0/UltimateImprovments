package com.ultimateimprovments.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.JukeboxSong;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.PatternType;
import org.bukkit.craftbukkit.enchantments.CraftEnchantment;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.trim.TrimMaterial;
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

    /**
     * Looks up an enchantment by key with a DATA-PACK fallback.
     * <p>
     * The Bukkit registry view does not reliably resolve data-driven enchantments
     * loaded from datapacks (all {@code ui:*} custom enchantments are of this
     * kind): a miss there returns a stale/dummy wrapper whose holder never
     * matches the real one stored on items. This method first tries the API
     * registry and then resolves the key through the SERVER registry
     * ({@code CraftRegistry.getMinecraftRegistry()}) — the same access
     * {@code CraftMetaItem} uses to unpack item enchantments, so the returned
     * wrapper is holder-identical and {@code item.getEnchantmentLevel()} works.
     */
    public static Enchantment enchantmentByKey(NamespacedKey key) {
        try {
            Enchantment viaApi = enchantment().get(key);
            if (viaApi != null && itemLevelResolves(viaApi)) return viaApi;
        } catch (Exception ignored) {
            // Fall through to the server-registry lookup.
        }
        try {
            var registry = org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var resourceKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.parse(key.toString()));
            var holder = registry.get(resourceKey);
            return holder.map(CraftEnchantment::minecraftHolderToBukkit).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sanity probe for the API-registry result: a stale/dummy wrapper (see
     * {@code CraftRegistry#loadBukkit}) reports a non-positive max level and
     * would never match real item holders. Server-registered enchantments
     * always have maxLevel &gt;= 1.
     */
    private static boolean itemLevelResolves(Enchantment enchantment) {
        try {
            return enchantment.getMaxLevel() >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Cached holder-identical Unbreaking enchantment. */
    private static Enchantment unbreaking;

    /**
     * Holder-identical {@code minecraft:unbreaking}. The legacy Bukkit
     * constant ({@code Enchantment.UNBREAKING}) comes from the stale API view
     * described in {@link #enchantmentByKey} — its holder never matches the
     * holders stored on items, so {@code item.getEnchantmentLevel(Enchantment.UNBREAKING)}
     * always reports 0 and every Unbreaking readout must go through this
     * resolver. Falls back to the constant when the server registry lookup
     * fails (better than nothing on a version where the API view is fine).
     */
    public static Enchantment unbreaking() {
        if (unbreaking != null) return unbreaking;
        Enchantment resolved = enchantmentByKey(NamespacedKey.minecraft("unbreaking"));
        if (resolved == null) resolved = Enchantment.UNBREAKING;
        unbreaking = resolved;
        return unbreaking;
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
