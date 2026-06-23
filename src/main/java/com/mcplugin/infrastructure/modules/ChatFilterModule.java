package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.listeners.ChatFilterManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatFilterModule extends PluginModule {

    public ChatFilterModule() { super("ChatFilter", "infrastructure/listeners", false); }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        main.getServer().getPluginManager().registerEvents(new ChatFilterManager(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ChatFilterManager.reloadConfigStatic();
    }
}
