package com.ultimateimprovments.datapack;

import com.ultimateimprovments.datapack.module.DatapackModule;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UIDatapack — the UI-Datapack plugin part.
 * <p>
 * Owns everything related to the bundled UI-Datapack:
 * <ul>
 *   <li>reads the {@code datapack.*} config (master toggle and the
 *       {@code datapack.modules.*} parts);</li>
 *   <li>reports the state of the datapack, which is registered <b>before the worlds
 *       load</b> by {@link UIDatapackBootstrap} through Paper's
 *       {@code DATAPACK_DISCOVERY} (no restart/reload needed after install);</li>
 *   <li>installs the {@link ModuleManager.DatapackGate} so UI-Other skips plugin
 *       code modules bound to disabled datapack parts.</li>
 * </ul>
 * Loaded right after UI-Core so the gate is active before UI-Other registers its modules.
 */
public class UIDatapack extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIDatapack instance;

    public static UIDatapack getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Datapack] Disabling...");
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.clearDatapackGate();
            mm.shutdownAll();
        }
        ConsoleLogger.success("[UI-Datapack] Disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): re-reads the datapack config and
     * re-installs the gate + module. Never disables the plugin — on Paper that
     * would close the JAR and re-enabling does not reopen it ("zip file
     * closed" zombie). The bundled datapack itself is registered at BOOTSTRAP
     * (before worlds load) and is not touched by a reload.
     */
    @Override
    public void softReload() {
        ConsoleLogger.info("[UI-Datapack] Soft reload (in place)...");
        runStartup();
    }

    private void runStartup() {
        instance = this;

        // Single config lives in UI-Core (Main.getInstance().getConfig());
        // UI-Datapack does not ship its own config.yml.
        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Datapack v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-Datapack] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Read the datapack config BEFORE any module registration — UI-Other
        // consults the gate when it registers enchantment/achievement modules.
        DatapackModules.init(this);
        mm.setDatapackGate(DatapackModules.gate());

        registerModules(mm);
        mm.initAll();

        ConsoleLogger.success("[UI-Datapack] All features enabled!");
    }

    private void registerModules(ModuleManager mm) {
        mm.register(new DatapackModule());
    }
}
