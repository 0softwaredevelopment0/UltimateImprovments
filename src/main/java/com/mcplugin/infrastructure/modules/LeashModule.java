package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.features.player.LeashManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль поводков — привязка любых сущностей, моб→моб,
 * отключение обрыва поводка, конфигурация.
 * <p>
 * Не essential — можно отключить без потери основной функциональности.
 */
public class LeashModule extends PluginModule {

    public LeashModule() {
        super("Leash", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        LeashManager.init((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        LeashManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        LeashManager.reloadConfig();
    }
}
