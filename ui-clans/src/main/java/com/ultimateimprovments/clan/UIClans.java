package com.ultimateimprovments.clan;

import com.ultimateimprovments.command.clan.ClanFriendlyFireListener;
import com.ultimateimprovments.core.Main;
import org.bukkit.plugin.java.JavaPlugin;

public class UIClans extends JavaPlugin {

    private static UIClans instance;

    @Override
    public void onEnable() {
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

    @Override
    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        getLogger().info("UI-Clans disabled!");
        instance = null;
    }

    public static UIClans getInstance() {
        return instance;
    }
}
