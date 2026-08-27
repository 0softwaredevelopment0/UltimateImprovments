package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.listener.ChatFilterManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class UIChat extends JavaPlugin {

    private static UIChat instance;
    private ChatFilterManager chatFilterManager;

    public static UIChat getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        // Save own config.yml (plugins/UI-Chat/config.yml)
        saveDefaultConfig();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Chat v" + getDescription().getVersion());
        ConsoleLogger.info("===========================================");

        Main main = Main.getInstance();
        if (main == null) {
            ConsoleLogger.error("[UI-Chat] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ChatManager — channels, format, pings
        ChatManager.init();

        // ChatFilter
        chatFilterManager = new ChatFilterManager();
        getServer().getPluginManager().registerEvents(chatFilterManager, main);

        // Chat Pings (static utility — registered via ChatManager)
        ChatPingManager.reloadConfig();

        // CmdLogger
        CmdLogger.init(main);

        // OJM (Override Join/Leave Messages)
        OjmManager.init(main);

        ConsoleLogger.success("[UI-Chat] Enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Chat] Disabling...");

        // Unregister all listeners
        HandlerList.unregisterAll(this);

        // Shutdown features
        ChatManager.shutdown();

        chatFilterManager = null;

        ConsoleLogger.success("[UI-Chat] Disabled!");
    }
}
