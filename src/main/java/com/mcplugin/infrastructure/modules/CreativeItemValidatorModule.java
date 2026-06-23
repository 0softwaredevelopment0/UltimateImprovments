package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.savedhotbar.CreativeItemValidator;
import org.bukkit.plugin.java.JavaPlugin;

public class CreativeItemValidatorModule extends PluginModule {

    public CreativeItemValidatorModule() { super("CreativeItemValidator", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        CreativeItemValidator.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        CreativeItemValidator.reloadConfig();
    }
}
