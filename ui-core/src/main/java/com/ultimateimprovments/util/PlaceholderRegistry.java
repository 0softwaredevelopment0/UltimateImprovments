package com.ultimateimprovments.util;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * Thin facade over {@link PlaceholderResolver} — a single entry point for code
 * that needs the shared registry of UltimateImprovments built-in placeholders.
 *
 * <p>All names and values are defined in {@link PlaceholderResolver}.
 * This class just proxies the calls, so code using the registry
 * stays consistent both inside the plugin and externally via PAPI.
 *
 * <p><b>Format:</b> {@code %<name>%} (e.g. {@code %player_ping%}, {@code %online%}).
 */
public final class PlaceholderRegistry {

    private PlaceholderRegistry() {}

    /** Resolver for a placeholder name without the surrounding {@code %}. */
    public static BiFunction<Player, String, String> get(String name) {
        return PlaceholderResolver.getBuiltin(name);
    }

    /** true if the name is known to the registry. */
    public static boolean contains(String name) {
        return PlaceholderResolver.getBuiltin(name) != null;
    }

    /** All supported static placeholder names. */
    public static Set<String> names() {
        return PlaceholderResolver.getBuiltinNames();
    }

    /** The identifier PAPI Expansion attaches to: {@code ui}. */
    public static String getIdentifier() {
        return PlaceholderResolver.getPapiIdentifier();
    }
}
