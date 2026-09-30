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
public class UIMBS extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIMBS instance;

    public static UIMBS getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-MBS] Disabling...");
        runShutdown();
        ConsoleLogger.success("[UI-MBS] Disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): saves structure data, then re-runs
     * the startup path (templates + DB reload). Never disables the plugin —
     * on Paper that would close the JAR and re-enabling does not reopen it
     * ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        ConsoleLogger.info("[UI-MBS] Soft reload (in place)...");
        runShutdown();
        runStartup();
    }

    private void runStartup() {
        instance = this;

        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-MBS cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-MBS v" + getPluginMeta().getVersion());
        ConsoleLogger.info("  Multi-Block Structures");
        ConsoleLogger.info("===========================================");

        // Load NBT structure templates from this plugin's resources
        StructureTemplate.initAll();

        // Restore structure markers + tracked chunks from the DB
        StructureMarker.loadFromDatabase();
        StructureChunkTracker.load();
        StructureChunkTracker.loadTrackedChunks();

        ConsoleLogger.success("[UI-MBS] Multi-block structures enabled!");
    }

    private void runShutdown() {
        StructureMarker.saveAll();
        StructureChunkTracker.save();
    }
}
