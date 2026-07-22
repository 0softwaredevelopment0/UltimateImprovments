package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.CommandRegistrar;
import com.ultimateimprovments.core.TaskManager;
import com.ultimateimprovments.listener.BlockBreakListener;
import com.ultimateimprovments.listener.BlockPlaceListener;
import com.ultimateimprovments.listener.FishingListener;
import com.ultimateimprovments.listener.MultimeterListener;
import com.ultimateimprovments.listener.PluginHideListener;
import com.ultimateimprovments.listener.ServerBrandListener;
import com.ultimateimprovments.listener.ShulkerBulletListener;
import com.ultimateimprovments.mechanics.features.items.AutoCraftManager;
import com.ultimateimprovments.mechanics.features.movement.BlockFrictionListener;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelGUIListener;
import com.ultimateimprovments.combat.weapons.plasma.GunListener;
import com.ultimateimprovments.combat.weapons.shoker.ShokerListener;
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
