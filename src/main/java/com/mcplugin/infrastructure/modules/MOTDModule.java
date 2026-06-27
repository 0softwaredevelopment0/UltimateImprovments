package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.listeners.MOTDListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль MOTD — управление отображением сервера в списке (MOTD + иконка).
 * <p>
 * Регистрирует {@link MOTDListener}, который обрабатывает ServerListPingEvent.
 * Конфигурация в config.yml → секция motd.
 */
public class MOTDModule extends PluginModule {

    private MOTDListener listener;

    public MOTDModule() {
        super("MOTD", "infrastructure/listeners/motd", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        this.listener = new MOTDListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (listener != null) {
            // ⛔ ОБЯЗАТЕЛЬНО отписываемся от Bukkit событий,
            // иначе при каждом /mp reload будет висеть дубликат listener'а
            HandlerList.unregisterAll(listener);
            this.listener = null;
        }
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        // При reload: disable() → onDisable() обнуляет listener.
        // Новый listener создаётся в onInit() после вызова onReloadConfig(),
        // поэтому здесь проверка на null — иконка загрузится в конструкторе нового listener.
        if (listener != null) {
            listener.loadIcon();
        }
    }
}
