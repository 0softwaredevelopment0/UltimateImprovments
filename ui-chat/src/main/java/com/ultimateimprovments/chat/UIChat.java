package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.listener.ChatFilterManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class UIChat extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIChat instance;
    private ChatFilterManager chatFilterManager;

    public static UIChat getInstance() { return instance; }

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-Chat] Disabling...");
        runShutdown();
        ConsoleLogger.success("[UI-Chat] Disabled!");
    }

    /**
     * In-place reload (soft /ui reload): full private shutdown + startup path.
     * Never disables the plugin — on Paper that would close the JAR and
     * re-enabling does not reopen it ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        ConsoleLogger.info("[UI-Chat] Soft reload (in place)...");
        runShutdown();
        runStartup();
    }

    private void runStartup() {
        instance = this;

        // Single config lives in UI-Core (Main.getInstance().getConfig());
        // UI-Chat does not ship its own config.yml.
        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Chat v" + getPluginMeta().getVersion());
        ConsoleLogger.info("===========================================");

        Main main = Main.getInstance();
        if (main == null) {
            ConsoleLogger.error("[UI-Chat] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ChatManager — channels, format, pings
        ChatManager.init();

        // ChatFilter — unregister the previous instance first: onEnable can run
        // again after /ui reload while the old listener is still registered,
        // which duplicated every filter check and sent the warning twice.
        if (chatFilterManager != null) {
            HandlerList.unregisterAll(chatFilterManager);
        }
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

    private void runShutdown() {
        // Unregister all listeners
        HandlerList.unregisterAll(this);

        // Shutdown features
        ChatManager.shutdown();
        // CmdLogger listeners live under UI-Core's handle and it also holds the
        // /ui outcome listener — release both on disable.
        CmdLogger.shutdown();
        // OJM listener also lives under UI-Core's handle.
        OjmManager.shutdown();

        chatFilterManager = null;
    }
}
