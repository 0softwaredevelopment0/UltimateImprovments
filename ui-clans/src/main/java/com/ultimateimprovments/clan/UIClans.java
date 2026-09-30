package com.ultimateimprovments.clan;

import com.ultimateimprovments.command.clan.ClanFriendlyFireListener;
import com.ultimateimprovments.core.Main;
import org.bukkit.plugin.java.JavaPlugin;

public class UIClans extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIClans instance;

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        getLogger().info("UI-Clans disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): private cleanup + startup path.
     * Never disables the plugin — on Paper that would close the JAR and
     * re-enabling does not reopen it ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        getLogger().info("UI-Clans soft reload (in place)...");
        org.bukkit.event.HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Clans cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register friendly fire listener
        ClanFriendlyFireListener.init(main);

        getLogger().info("UI-Clans enabled!");
    }

    public static UIClans getInstance() {
        return instance;
    }
}
