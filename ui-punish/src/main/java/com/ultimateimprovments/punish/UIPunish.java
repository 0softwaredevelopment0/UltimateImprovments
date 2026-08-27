package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.server.AccessListCheckTask;
import com.ultimateimprovments.whitelist.BlacklistManager;
import com.ultimateimprovments.whitelist.OpWhitelistManager;
import com.ultimateimprovments.whitelist.WhitelistManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class UIPunish extends JavaPlugin {

    private static UIPunish instance;
    private BukkitTask accessCheckTask;

    @Override
    public void onEnable() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Punish cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var pm = getServer().getPluginManager();

        // Register punishment listener
        pm.registerEvents(new PunishJoinListener(), main);

        // Initialize whitelist/blacklist
        WhitelistManager.init(main);
        BlacklistManager.init(main);
        OpWhitelistManager.init(main);

        // Start periodic access list check
        accessCheckTask = new AccessListCheckTask().runTaskTimer(main, 20L, 20L);

        // Clean old kicks async
        Bukkit.getScheduler().runTaskAsynchronously(main, PunishmentManager::deleteOldKicks);

        getLogger().info("UI-Punish enabled!");
    }

    @Override
    public void onDisable() {
        if (accessCheckTask != null) {
            accessCheckTask.cancel();
            accessCheckTask = null;
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        getLogger().info("UI-Punish disabled!");
        instance = null;
    }

    public static UIPunish getInstance() {
        return instance;
    }
}
