package com.ultimateimprovments.datapack.module;

import com.ultimateimprovments.datapack.DatapackInstaller;
import com.ultimateimprovments.module.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Datapack — installs the bundled UI-Datapack into the world's
 * {@code datapacks/} folder and verifies it is enabled in the world.
 * <p>
 * Installation mode and enabled parts are read from {@code datapack.*}
 * in the UI-Datapack config (see {@code DatapackModules}).
 */
public final class DatapackModule extends PluginModule {

    public DatapackModule() {
        super("Datapack", "infrastructure/datapack", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        DatapackInstaller.init(plugin);
        DatapackInstaller.getInstance().install(plugin);
        // Success is logged inside DatapackInstaller.install()
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
