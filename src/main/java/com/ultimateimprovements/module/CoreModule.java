package com.ultimateimprovements.module;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.core.CommandRegistrar;
import com.ultimateimprovements.core.TaskManager;
import com.ultimateimprovements.listener.BlockBreakListener;
import com.ultimateimprovements.listener.BlockPlaceListener;
import com.ultimateimprovements.listener.FishingListener;
import com.ultimateimprovements.listener.MultimeterListener;
import com.ultimateimprovements.listener.PluginHideListener;
import com.ultimateimprovements.listener.ServerBrandListener;
import com.ultimateimprovements.listener.ShulkerBulletListener;
import com.ultimateimprovements.mechanics.features.items.AutoCraftManager;
import com.ultimateimprovements.mechanics.features.movement.BlockFrictionListener;
import com.ultimateimprovements.mechanics.security.codepanel.CodePanelGUIListener;
import com.ultimateimprovements.combat.weapons.plasma.GunListener;
import com.ultimateimprovements.combat.weapons.shoker.ShokerListener;
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
