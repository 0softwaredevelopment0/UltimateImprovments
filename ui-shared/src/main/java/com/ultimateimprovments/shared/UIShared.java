package com.ultimateimprovments.shared;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.crafting.RecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class UIShared extends JavaPlugin implements com.ultimateimprovments.core.SoftReloadable {

    private static UIShared instance;

    @Override
    public void onEnable() {
        runStartup();
    }

    @Override
    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        getLogger().info("UI-Shared disabled!");
        instance = null;
    }

    /**
     * In-place reload (soft /ui reload): private cleanup + startup path.
     * Never disables the plugin — on Paper that would close the JAR and
     * re-enabling does not reopen it ("zip file closed" zombie).
     */
    @Override
    public void softReload() {
        getLogger().info("UI-Shared soft reload (in place)...");
        org.bukkit.event.HandlerList.unregisterAll(this);
        runStartup();
    }

    private void runStartup() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Shared cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Structure data is owned by UI-MBS — no longer loaded here.
        // Space system is owned by UI-Other (it registers the same listeners and
        // tasks; initializing it here too duplicated every listener and the
        // oxygen damage task ran twice per second).

        // Recipe reload listener
        Bukkit.getPluginManager().registerEvents(new RecipeRegistry(), main);

        getLogger().info("UI-Shared enabled!");
    }

    public static UIShared getInstance() {
        return instance;
    }
}
