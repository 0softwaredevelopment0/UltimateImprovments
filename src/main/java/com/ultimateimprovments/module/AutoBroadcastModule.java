package com.ultimateimprovments.module;

import com.ultimateimprovments.broadcast.AutoBroadcastManager;
import com.ultimateimprovments.core.Main;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 📢 Модуль авто-броадкастов.
 * <p>
 * Обёртка над {@link AutoBroadcastManager} для модульной системы плагина:
 * при старте запускает тикер, при остановке/перезагрузке корректно гасит его.
 * <p>
 * Конфигурация: config.yml → секция {@code auto_broadcast}
 * (мастер-переключатель + список секций с условиями).
 */
public class AutoBroadcastModule extends PluginModule {

    public AutoBroadcastModule() {
        super("AutoBroadcast", "infrastructure/auto_broadcast", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        AutoBroadcastManager.getInstance().start((Main) plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        AutoBroadcastManager.getInstance().stop();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        AutoBroadcastManager.getInstance().reload();
    }
}
