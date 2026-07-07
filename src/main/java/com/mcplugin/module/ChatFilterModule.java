package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.listener.ChatFilterManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatFilterModule extends PluginModule {

    private ChatFilterManager filterManager;

    public ChatFilterModule() { super("ChatFilter", "infrastructure/listeners", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        filterManager = new ChatFilterManager();
        main.getServer().getPluginManager().registerEvents(filterManager, main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (filterManager != null) {
            HandlerList.unregisterAll(filterManager);
            filterManager = null;
        }
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ChatFilterManager.reloadConfigStatic();
    }
}
