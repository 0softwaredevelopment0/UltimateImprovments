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
 *   <li>reads the {@code datapack.*} config (master toggle, install mode,
 *       auto-enable/restart behavior and the {@code datapack.modules.*} parts);</li>
 *   <li>installs the datapack into the world's {@code datapacks/} folder and
 *       verifies it is enabled in the world (the {@link DatapackModule});</li>
 *   <li>installs the {@link ModuleManager.DatapackGate} so UI-Other skips plugin
 *       code modules bound to disabled datapack parts.</li>
 * </ul>
 * Loaded at STARTUP (right after UI-Core) so the gate is active before UI-Other
 * registers its modules.
 */
public class UIDatapack extends JavaPlugin {

    private static UIDatapack instance;

    public static UIDatapack getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Save own config.yml (plugins/UI-Datapack/config.yml)
        saveDefaultConfig();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Datapack v" + getDescription().getVersion());
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

    private void registerModules(ModuleManager mm) {
        mm.register(new DatapackModule());
    }
}
