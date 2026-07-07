package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.core.CommandRegistrar;
import com.mcplugin.core.TaskManager;
import com.mcplugin.listener.BlockBreakListener;
import com.mcplugin.listener.BlockPlaceListener;
import com.mcplugin.listener.FishingListener;
import com.mcplugin.listener.MultimeterListener;
import com.mcplugin.listener.PluginHideListener;
import com.mcplugin.listener.ServerBrandListener;
import com.mcplugin.listener.ShulkerBulletListener;
import com.mcplugin.mechanics.features.items.AutoCraftManager;
import com.mcplugin.mechanics.features.movement.BlockFrictionListener;
import com.mcplugin.mechanics.security.codepanel.CodePanelGUIListener;
import com.mcplugin.combat.weapons.plasma.GunListener;
import com.mcplugin.combat.weapons.shoker.ShokerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Core — базовые системы: управление задачами, командами, общие слушатели.
 * Essential — без них плагин бесполезен.
 */
public class CoreModule extends PluginModule {

    public CoreModule() {
        super("Core", "infrastructure/core", true);
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

        BlockFrictionListener.init();
        pm.registerEvents(new BlockFrictionListener(), main);

        AutoCraftManager.init(main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
