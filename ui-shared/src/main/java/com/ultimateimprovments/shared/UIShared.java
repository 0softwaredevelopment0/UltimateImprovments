package com.ultimateimprovments.shared;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.crafting.RecipeRegistry;
import com.ultimateimprovments.space.SpaceGravityListener;
import com.ultimateimprovments.space.SpaceManager;
import com.ultimateimprovments.space.SpaceOxygenListener;
import com.ultimateimprovments.space.SpaceRadiationListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class UIShared extends JavaPlugin {

    private static UIShared instance;

    @Override
    public void onEnable() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Shared cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Structure data is owned by UI-MBS — no longer loaded here.

        // Recipe reload listener
        Bukkit.getPluginManager().registerEvents(new RecipeRegistry(), main);

        // Space system
        SpaceManager.createTable();
        SpaceManager.init(main);
        Bukkit.getPluginManager().registerEvents(new SpaceGravityListener(), main);
        Bukkit.getPluginManager().registerEvents(new SpaceOxygenListener(), main);
        Bukkit.getPluginManager().registerEvents(new SpaceRadiationListener(), main);

        getLogger().info("UI-Shared enabled!");
    }

    @Override
    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        getLogger().info("UI-Shared disabled!");
        instance = null;
    }

    public static UIShared getInstance() {
        return instance;
    }
}
