package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.core.CommandRegistrar;
import com.mcplugin.infrastructure.core.TaskManager;
import com.mcplugin.infrastructure.listeners.BlockBreakListener;
import com.mcplugin.infrastructure.listeners.BlockPlaceListener;
import com.mcplugin.infrastructure.listeners.FishingListener;
import com.mcplugin.infrastructure.listeners.MultimeterListener;
import com.mcplugin.infrastructure.listeners.PluginHideListener;
import com.mcplugin.infrastructure.listeners.ServerBrandListener;
import com.mcplugin.infrastructure.listeners.ShulkerBulletListener;
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
