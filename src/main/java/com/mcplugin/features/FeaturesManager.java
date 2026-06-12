package com.mcplugin.features;

import com.mcplugin.Main;
import com.mcplugin.features.antimatter.AntimatterManager;
import com.mcplugin.features.attributes.AttributesManager;
import com.mcplugin.features.beacon.BeaconManager;
import com.mcplugin.features.blockdmg.BlockDmgManager;
import com.mcplugin.features.boostedcobweb.BoostedCobwebManager;
import com.mcplugin.features.deathbell.DeathBellManager;
import com.mcplugin.features.dragonegg.DragonEggManager;
import com.mcplugin.features.enderchest.EnderChestManager;
import com.mcplugin.features.entitylocator.EntityLocatorManager;
import com.mcplugin.features.glassbreak.GlassBreakManager;
import com.mcplugin.features.healthmeter.HealthMeterManager;
import com.mcplugin.features.itemskill.ItemKillManager;
import com.mcplugin.features.magnet.MagnetManager;
import com.mcplugin.features.modeprotect.ModeProtectManager;
import com.mcplugin.features.shieldslowness.ShieldSlownessManager;
import com.mcplugin.features.terracotaspeed.TerracotaSpeedManager;
import com.mcplugin.features.savedhotbar.CreativeItemValidator;
import com.mcplugin.features.waypoint.WaypointManager;

public class FeaturesManager {

    private static FeaturesManager instance;

    public static void init(Main plugin) {
        instance = new FeaturesManager();

        // Init all feature managers
        AttributesManager.init(plugin);
        BeaconManager.init(plugin);
        BlockDmgManager.init(plugin);
        BoostedCobwebManager.init(plugin);
        DragonEggManager.init(plugin);
        EntityLocatorManager.init(plugin);
        HealthMeterManager.init(plugin);
        ItemKillManager.init(plugin);
        MagnetManager.init(plugin);
        ModeProtectManager.init(plugin);
        TerracotaSpeedManager.init(plugin);
        WaypointManager.init(plugin);

        // Init managers with listeners
        AntimatterManager.init(plugin);
        DeathBellManager.init(plugin);
        EnderChestManager.init(plugin);
        GlassBreakManager.init(plugin);
        ShieldSlownessManager.init(plugin);
        CreativeItemValidator.init(plugin);
    }

    public static void reloadConfig() {
        AntimatterManager.reloadConfig();
        AttributesManager.reloadConfig();
        BeaconManager.reloadConfig();
        BlockDmgManager.reloadConfig();
        BoostedCobwebManager.reloadConfig();
        DeathBellManager.reloadConfig();
        DragonEggManager.reloadConfig();
        EnderChestManager.reloadConfig();
        EntityLocatorManager.reloadConfig();
        GlassBreakManager.reloadConfig();
        HealthMeterManager.reloadConfig();
        ItemKillManager.reloadConfig();
        MagnetManager.reloadConfig();
        ModeProtectManager.reloadConfig();
        ShieldSlownessManager.reloadConfig();
        CreativeItemValidator.reloadConfig();
        TerracotaSpeedManager.reloadConfig();
        WaypointManager.reloadConfig();
    }

    public static FeaturesManager getInstance() {
        return instance;
    }
}
