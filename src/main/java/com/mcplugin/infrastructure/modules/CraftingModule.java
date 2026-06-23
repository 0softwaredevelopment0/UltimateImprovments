package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.mechanics.crafting.*;
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
        HealthMeterCraftListener.init();
        EntityLocatorCraftListener.init();
        DosimeterCraftListener.init();
        LeadShieldCraftListener.init();
        RecipeRegistry.init();

        // Register craft event listeners
        var pm = main.getServer().getPluginManager();
        pm.registerEvents(new MultimeterCraftListener(), main);
        pm.registerEvents(new PlasmaCannonCraftListener(), main);
        pm.registerEvents(new ShokerCraftListener(), main);
        pm.registerEvents(new AntimatterCraftListener(), main);
        pm.registerEvents(new HealthMeterCraftListener(), main);
        pm.registerEvents(new EntityLocatorCraftListener(), main);
        pm.registerEvents(new DosimeterCraftListener(), main);
        pm.registerEvents(new LeadShieldCraftListener(), main);

        plugin.getLogger().info("[CraftingModule] ✓ Recipes initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
