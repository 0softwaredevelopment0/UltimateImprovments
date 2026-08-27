package com.ultimateimprovments.hook;

import com.ultimateimprovments.util.PlaceholderResolver;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for UltimateImprovments.
 *
 * <p>Registers all our placeholders in PAPI under the {@code ui} identifier.
 * If PAPI is installed, external plugins (TAB, scoreboard plugins, Discord integrations)
 * can use {@code %ui_player_ping%}, {@code %ui_online%},
 * {@code %ui_tps_5m%} etc. — and get the same values our plugin sees.
 *
 * <p><b>Name list:</b> shared between the internal and external resolver, taken from
 * {@link PlaceholderResolver#getBuiltinNames()} + dynamic templates (tps_*, mspt_* etc.).
 *
 * <p><b>If PAPI is not installed:</b> this class is NOT registered (see {@link PluginStartup}),
 * and the internal {@link PlaceholderResolver#resolve(String, Player)} keeps working
 * for the plugin's own strings.
 *
 * <p>All requests are delegated to {@link PlaceholderResolver#resolveInternal(String, Player)} —
 * an internal resolve WITHOUT the PAPI step, to avoid recursion PAPI → resolve → PAPI.
 */
public class UIPlaceholderExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return PlaceholderResolver.getPapiIdentifier(); // "ui"
    }

    @Override
    public @NotNull String getAuthor() {
        return "UltimateImprovments";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // do not unload on /reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * PAPI calls this method for each {@code %ui_<params>%}.
     * <p>{@code params} = everything after {@code ui_}. For example,
     * {@code %ui_player_ping%} → onRequest(offline, "player_ping").
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (params == null || params.isEmpty()) return null;

        // PAPI always passes an OfflinePlayer. If the player is online — use the Player;
        // otherwise Player=null (static placeholders and server values still work).
        Player online = (offline != null && offline.isOnline()) ? offline.getPlayer() : null;

        // 1. Fast path: exact name match in BUILTIN
        String direct = PlaceholderResolver.resolveBuiltin(online, params);
        if (direct != null) return direct;

        // 2. Dynamic templates (%tps_5m%, %ping_5m_all%, %copy:"x"%, ...)
        //    Safe wrapper: do NOT call resolvePapi to avoid recursion.
        String wrapped = "%" + params + "%";
        String resolved = PlaceholderResolver.resolveInternal(wrapped, online);

        // If nothing changed — this placeholder is not ours. Let PAPI return null.
        if (resolved.equals(wrapped)) return null;
        if (resolved.isEmpty()) return null;
        return resolved;
    }
}
