package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.main.CommandRegistrar;
import com.mcplugin.main.TaskManager;
import com.mcplugin.listeners.BlockBreakListener;
import com.mcplugin.listeners.BlockPlaceListener;
import com.mcplugin.listeners.FishingListener;
import com.mcplugin.listeners.MultimeterListener;
import com.mcplugin.listeners.PluginHideListener;
import com.mcplugin.listeners.ServerBrandListener;
import com.mcplugin.listeners.ShulkerBulletListener;
import com.mcplugin.cp.CodePanelGUIListener;
import com.mcplugin.guns.plasmacannon.GunListener;
import com.mcplugin.guns.shoker.ShokerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Core — базовые системы: управление задачами, командами, общие слушатели.
 * Essential — без них плагин бесполезен.
 */
public class CoreModule extends PluginModule {

    public CoreModule() {
        super("Core", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // =========================
        // TASK MANAGER & COMMANDS
        // =========================
        TaskManager.init(main);
        CommandRegistrar.init(main);

        // =========================
        // GENERAL LISTENERS
        // =========================
        var pm = main.getServer().getPluginManager();
        pm.registerEvents(new BlockPlaceListener(), main);
        pm.registerEvents(new BlockBreakListener(), main);
        pm.registerEvents(new MultimeterListener(), main);
        pm.registerEvents(new PluginHideListener(), main);
        pm.registerEvents(new ServerBrandListener(), main);
        pm.registerEvents(new ShokerListener(), main);
        pm.registerEvents(new GunListener(), main);
        pm.registerEvents(new ShulkerBulletListener(), main);
        pm.registerEvents(FishingListener.getInstance(), main);
        pm.registerEvents(new CodePanelGUIListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
