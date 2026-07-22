package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.mechanics.crafting.StructureIntegrityCraftListener;
import com.ultimateimprovements.mechanics.features.structure.StructureIntegrityListener;
import com.ultimateimprovements.mechanics.features.structure.StructureIntegrityManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module for the Structure Integrity Indicator system.
 * Tracks stress, degradation, and integrity of structures (currently ender chests).
 */
public class StructureIntegrityModule extends PluginModule {

    public StructureIntegrityModule() {
        super("StructureIntegrity", "mechanics/features/structure_integrity", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // Initialize manager (starts ticker)
        StructureIntegrityManager.init(main);

        // Register craft listener
        StructureIntegrityCraftListener.init();
        main.getServer().getPluginManager().registerEvents(new StructureIntegrityCraftListener(), main);

        // Register interaction listener
        main.getServer().getPluginManager().registerEvents(new StructureIntegrityListener(), main);

        ConsoleLogger.info("[StructureIntegrityModule] ✔ Initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        StructureIntegrityManager mgr = StructureIntegrityManager.getInstance();
        if (mgr != null) {
            mgr.shutdown();
        }
    }
}
