package com.ultimateimprovements.mechanics.features.omniscanner;

import com.ultimateimprovements.module.PluginModule;
import com.ultimateimprovements.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 🎛 Omniscanner Module
 * <p>
 * Регистрирует:
 * - {@link OmniscannerManager} — слушатель взаимодействия, создание предмета, сканирование
 * - {@link OmniscannerGUI} — конфигурационное GUI
 * - {@link AdminMenuGUI} — /mp menu GUI (информация, статистика, предметы)
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

        // Omniscanner config GUI (регистрируется через статический register)
        OmniscannerGUI.register();

        // Admin menu GUI (/mp menu)
        AdminMenuGUI.register();

        ConsoleLogger.info("[OmniscannerModule] ✔ Omniscanner + AdminMenu registered.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Cleanup
    }
}
