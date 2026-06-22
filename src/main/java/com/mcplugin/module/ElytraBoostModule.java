package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.features.elytraboost.ElytraBoostManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль ElytraBoost — нажатие пробела во время полёта на элитрах
 * запускает фейерверк из инвентаря для ускорения.
 * <p>
 * Не essential — можно отключить без потери основной функциональности.
 */
public class ElytraBoostModule extends PluginModule {

    public ElytraBoostModule() {
        super("ElytraBoost", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ElytraBoostManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
