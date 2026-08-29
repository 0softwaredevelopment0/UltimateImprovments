package com.ultimateimprovments.mbs;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.structure.StructureChunkTracker;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.StructureTemplate;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UI-MBS — Multi-Block Structures part.
 * <p>
 * Owns everything structure-related: the NBT templates ({@link StructureTemplate}),
 * the SQLite-backed structure marker registry ({@link StructureMarker}),
 * chunk tracking ({@link StructureChunkTracker}) and the structure mechanics
 * (lightning, magnet, reactor/generator validation).
 * <p>
 * Energy-dependent behaviour is exposed through the {@code MbsEnergy} API bridge,
 * which UI-Energy registers at startup — UI-MBS never depends on UI-Energy.
 */
public class UIMBS extends JavaPlugin {

    private static UIMBS instance;

    public static UIMBS getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-MBS cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-MBS v" + getDescription().getVersion());
        ConsoleLogger.info("  Multi-Block Structures");
        ConsoleLogger.info("===========================================");

        // Load NBT structure templates from this plugin's resources
        StructureTemplate.initAll();

        // Restore structure markers + tracked chunks from the DB
        StructureMarker.loadFromDatabase();
        StructureChunkTracker.load();
        StructureChunkTracker.loadTrackedChunks();
        StructureMarker.migrateLegacyMarkers();

        ConsoleLogger.success("[UI-MBS] Multi-block structures enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-MBS] Disabling...");
        StructureMarker.saveAll();
        StructureChunkTracker.save();
        instance = null;
        ConsoleLogger.success("[UI-MBS] Disabled!");
    }
}
