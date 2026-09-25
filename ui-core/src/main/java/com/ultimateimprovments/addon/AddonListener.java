package com.ultimateimprovments.addon;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * Watches plugin enable/disable events and keeps {@link AddonRegistry}
 * up to date. Any plugin that declares {@code addon-for: UI-Core} in its
 * plugin.yml is registered automatically when it enables — including
 * third-party addons, no code needed on their side.
 * <p>
 * Paper fires {@code ServerExceptionEvent} (not a Bukkit event we can cheaply
 * intercept per-plugin here), so enable crashes are captured by the addon
 * reporting API ({@link AddonRegistry#reportModules}) and by the post-enable
 * verification in {@code /ui addon}.
 */
public final class AddonListener implements Listener {

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (AddonRegistry.isUiAddon(event.getPlugin())) {
            AddonRegistry.onAddonEnabled(event.getPlugin());
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (AddonRegistry.isUiAddon(event.getPlugin())) {
            AddonRegistry.onAddonDisabled(event.getPlugin().getName());
        }
    }
}
