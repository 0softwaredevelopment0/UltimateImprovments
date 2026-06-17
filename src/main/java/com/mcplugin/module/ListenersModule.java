package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.auth.AuthListener;
import com.mcplugin.cp.CodePanelGUIListener;
import com.mcplugin.core1.ReactorListener;
import com.mcplugin.crafting.*;
import com.mcplugin.energy.crafting.EnergyCraftingListener;
import com.mcplugin.features.integrity.IntegrityCombineListener;
import com.mcplugin.features.integrity.IntegrityListener;
import com.mcplugin.features.magnet.MagnetEventListener;
import com.mcplugin.guns.plasmacannon.GunListener;
import com.mcplugin.guns.shoker.ShokerListener;
import com.mcplugin.listeners.*;
import com.mcplugin.server.RedstoneGuardListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль регистрации слушателей событий.
 * Неessential — если регистрация не удалась, плагин просто не будет
 * реагировать на некоторые события, но останется работоспособным.
 */
public class ListenersModule extends PluginModule {

    public ListenersModule() {
        super("Listeners", true);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        var pm = main.getServer().getPluginManager();

        pm.registerEvents(new BlockPlaceListener(), main);
        pm.registerEvents(new BlockBreakListener(), main);
        pm.registerEvents(new MultimeterListener(), main);

        // Craft listeners
        pm.registerEvents(new MultimeterCraftListener(), main);
        pm.registerEvents(new PlasmaCannonCraftListener(), main);
        pm.registerEvents(new ShokerCraftListener(), main);
        pm.registerEvents(new AntimatterCraftListener(), main);
        pm.registerEvents(new HealthMeterCraftListener(), main);
        pm.registerEvents(new EntityLocatorCraftListener(), main);
        pm.registerEvents(new DosimeterCraftListener(), main);
        pm.registerEvents(new LeadShieldCraftListener(), main);

        pm.registerEvents(new EnergyCraftingListener(), main);
        pm.registerEvents(new PluginHideListener(), main);
        pm.registerEvents(new PowerInterceptListener(), main);
        pm.registerEvents(new ChatFilterManager(), main);
        pm.registerEvents(new ServerBrandListener(), main);
        pm.registerEvents(new ShokerListener(), main);
        pm.registerEvents(new GunListener(), main);
        pm.registerEvents(new ReactorListener(), main);
        pm.registerEvents(new ShulkerBulletListener(), main);
        pm.registerEvents(FishingListener.getInstance(), main);
        pm.registerEvents(new RedstoneGuardListener(), main);
        pm.registerEvents(new AuthListener(), main);
        pm.registerEvents(new MagnetEventListener(), main);
        pm.registerEvents(new IntegrityListener(), main);
        pm.registerEvents(new IntegrityCombineListener(), main);
        pm.registerEvents(new CodePanelGUIListener(), main);
        pm.registerEvents(new VoidProtectionListener(), main);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Listeners are automatically unregistered when the plugin disables
    }
}
