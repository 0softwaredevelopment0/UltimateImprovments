package com.mcplugin.module;

import com.mcplugin.crafting.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль крафта — инициализация всех рецептов и слушателей крафта.
 * Essential — крафты — ключевая механика плагина.
 */
public class CraftingModule extends PluginModule {

    public CraftingModule() {
        super("Crafting", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        MultimeterCraftListener.init();
        PlasmaCannonCraftListener.init();
        ShokerCraftListener.init();
        AntimatterCraftListener.init();
        HealthMeterCraftListener.init();
        EntityLocatorCraftListener.init();
        DosimeterCraftListener.init();
        LeadShieldCraftListener.init();
        RecipeRegistry.init();

        plugin.getLogger().info("[CraftingModule] ✓ Recipes initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }
}
