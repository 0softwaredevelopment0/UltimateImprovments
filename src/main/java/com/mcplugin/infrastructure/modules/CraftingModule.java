package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.crafting.*;
import com.mcplugin.energy.machines.assembler.AssemblerListener;
import com.mcplugin.mechanics.features.scanner.ScannerItemListener;
import com.mcplugin.mechanics.features.scanner.MetalDetectorListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль крафта — инициализация всех рецептов и слушателей крафта.
 * Essential — крафты — ключевая механика плагина.
 */
public class CraftingModule extends PluginModule {

    public CraftingModule() {
        super("Crafting", "mechanics/crafting", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        MultimeterCraftListener.init();
        PlasmaCannonCraftListener.init();
        ShokerCraftListener.init();
        AntimatterCraftListener.init();
        EntityLocatorCraftListener.init();
        LeadIngotCraftListener.init();
        LeadShieldCraftListener.init();
        HealthMeterCraftListener.init();
        OreFinderCraftListener.init();
        MobFinderCraftListener.init();
        PortableRadarCraftListener.init();
        MetalDetectorCraftListener.init();
        RecipeRegistry.init();

        // Register craft event listeners
        var pm = main.getServer().getPluginManager();
        pm.registerEvents(new MultimeterCraftListener(), main);
        pm.registerEvents(new PlasmaCannonCraftListener(), main);
        pm.registerEvents(new ShokerCraftListener(), main);
        pm.registerEvents(new AntimatterCraftListener(), main);
        pm.registerEvents(new EntityLocatorCraftListener(), main);
        pm.registerEvents(new LeadIngotCraftListener(), main);
        pm.registerEvents(new LeadShieldCraftListener(), main);
        pm.registerEvents(new HealthMeterCraftListener(), main);
        pm.registerEvents(new OreFinderCraftListener(), main);
        pm.registerEvents(new MobFinderCraftListener(), main);
        pm.registerEvents(new PortableRadarCraftListener(), main);
        pm.registerEvents(new MetalDetectorCraftListener(), main);
        pm.registerEvents(new ScannerItemListener(), main);
        pm.registerEvents(new MetalDetectorListener(), main);
        pm.registerEvents(new AssemblerListener(), main);

        plugin.getLogger().info("[CraftingModule] ✔ Recipes initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
