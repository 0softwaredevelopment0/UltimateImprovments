package com.ultimateimprovments.mechanics.features.omniscanner;

import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 🎛 Omniscanner Module
 * <p>
 * Registers:
 * - {@link OmniscannerManager} — interaction listener, item creation, scanning
 * - {@link OmniscannerGUI} — configuration GUI
 * - {@link AdminMenuGUI} — /ui menu GUI (info, stats, items)
 */
public class OmniscannerModule extends PluginModule {

    public OmniscannerModule() {
        super("Omniscanner", "mechanics/features/omniscanner", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        var pm = plugin.getServer().getPluginManager();

        // Omniscanner item listener
        pm.registerEvents(new OmniscannerManager(), plugin);

        // Omniscanner config GUI (registered via the static register)
        OmniscannerGUI.register();

        // Admin menu GUI (/ui menu)
        AdminMenuGUI.register();

        ConsoleLogger.info("[OmniscannerModule] ✔ Omniscanner + AdminMenu registered.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Cleanup
    }
}
