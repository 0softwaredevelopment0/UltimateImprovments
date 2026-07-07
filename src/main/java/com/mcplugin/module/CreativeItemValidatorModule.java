package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.mechanics.features.creativeitem.CreativeItemValidator;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class CreativeItemValidatorModule extends PluginModule {

    private CreativeItemValidator listener;

    public CreativeItemValidatorModule() { super("CreativeItemValidator", "mechanics/features/creativeitem", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        CreativeItemValidator.init((Main) plugin);
        listener = CreativeItemValidator.getInstance();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        CreativeItemValidator.reloadConfig();
    }
}
