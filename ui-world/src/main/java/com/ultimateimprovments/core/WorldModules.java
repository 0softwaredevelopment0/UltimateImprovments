package com.ultimateimprovments.core;

import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseListener;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseManager;
import com.ultimateimprovments.mechanics.features.movement.BlockFrictionListener;
import com.ultimateimprovments.mechanics.features.world.BeaconManager;
import com.ultimateimprovments.mechanics.features.world.BedrockBreakListener;
import com.ultimateimprovments.mechanics.features.world.CmdBlockTracker;
import com.ultimateimprovments.mechanics.features.world.DeathBellManager;
import com.ultimateimprovments.mechanics.features.world.DragonEggManager;
import com.ultimateimprovments.mechanics.features.world.EarthCoreListener;
import com.ultimateimprovments.mechanics.features.world.EnderPearlChallenge;
import com.ultimateimprovments.mechanics.features.world.KaboomListener;
import com.ultimateimprovments.mechanics.features.world.MinecartSpeedManager;
import com.ultimateimprovments.mechanics.features.world.NetheriteKingListener;
import com.ultimateimprovments.mechanics.features.world.OutOfMemoryListener;
import com.ultimateimprovments.mechanics.features.world.ServerFreezeListener;
import com.ultimateimprovments.mechanics.features.world.ShutdownListener;
import com.ultimateimprovments.mechanics.features.world.WoodcutterChallenge;
import com.ultimateimprovments.mechanics.features.world.WirelessRedstoneManager;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.module.SimpleModule;
import com.ultimateimprovments.module.meteor.MeteorModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WorldModules — registration of the world-mechanics addon (blocks/collapse/friction,
 * meteor, wireless redstone, bedrock/sky challenges, death bell). Moved out of
 * ui-other's SimpleModules.
 */
public final class WorldModules {

    private WorldModules() {}

    public static void registerAll(ModuleManager mm) {
        // Beacon
        mm.register(new SimpleModule("Beacon", "mechanics/features/beacon", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BeaconManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                BeaconManager.reloadConfig();
            }
        });

        // BlockCollapse
        mm.register(new PluginModule("BlockCollapse", "mechanics/features/collapse", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                BlockCollapseManager.init(main);
                main.getServer().getPluginManager().registerEvents(new BlockCollapseListener(), main);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                BlockCollapseManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                BlockCollapseManager.reload();
            }
        });

        // DragonEgg
        mm.register(new SimpleModule("DragonEgg", "mechanics/features/dragon_egg", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                DragonEggManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                DragonEggManager.reloadConfig();
            }
        });

        // BlockFriction (block acceleration by material)
        mm.register(new SimpleModule("BlockFriction", "mechanics/features/movement", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                BlockFrictionListener.init();
                main.getServer().getPluginManager().registerEvents(new BlockFrictionListener(), main);
            }
        });

        // Beyond Space - bedrock break challenge
        mm.register(new SimpleModule("BedrockBreak", "mechanics/features/bedrock_break", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BedrockBreakListener.register((Main) plugin);
                CoreHooks.setBedrockBreakGranter(BedrockBreakListener::grant);
            }
        });

        // Kaboom! - mace damage challenge
        mm.register(new SimpleModule("Kaboom", "mechanics/features/kaboom", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                KaboomListener.register((Main) plugin);
            }
        });

        // Where is the Earth's core here?
        mm.register(new SimpleModule("EarthCore", "mechanics/features/earth_core", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EarthCoreListener.register((Main) plugin);
            }
        });

        // The Woodcutter at Full Throttle - timed challenge
        mm.register(new SimpleModule("WoodcutterChallenge", "mechanics/features/woodcutter_challenge", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                WoodcutterChallenge.register((Main) plugin);
            }
        });

        // Let me teleport! - timed challenge
        mm.register(new SimpleModule("EnderPearlChallenge", "mechanics/features/ender_pearl_challenge", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EnderPearlChallenge.register((Main) plugin);
            }
        });

        // A Netherite King
        mm.register(new SimpleModule("NetheriteKing", "mechanics/features/netherite_king", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                NetheriteKingListener.register((Main) plugin);
            }
        });

        // java.lang.OutOfMemoryError
        mm.register(new SimpleModule("OutOfMemory", "mechanics/features/out_of_memory", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                OutOfMemoryListener.register((Main) plugin);
            }
        });

        // The server has not responding!
        mm.register(new SimpleModule("ServerFreeze", "mechanics/features/server_freeze", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ServerFreezeListener.register((Main) plugin);
            }
        });

        // Active command blocks tracking (for /ui cmdblocklist)
        mm.register(new SimpleModule("CmdBlockTracker", "mechanics/features/cmdblock_tracker", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                CmdBlockTracker.register((Main) plugin);
            }
        });

        // DeathBell
        mm.register(new SimpleModule("DeathBell", "mechanics/features/bell_lightning", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                DeathBellManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                DeathBellManager.reloadConfig();
            }
        });

        // MinecartSpeed
        mm.register(new PluginModule("MinecartSpeed", "mechanics/features/minecart_speed", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                MinecartSpeedManager.init((Main) plugin);
                CoreHooks.setMinecartSpeed(MinecartSpeedManager::isSpeedDisplayEnabled,
                        MinecartSpeedManager::toggleSpeedDisplay);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                MinecartSpeedManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                MinecartSpeedManager.reloadConfig();
            }
        });

        // WirelessRedstone
        mm.register(new PluginModule("WirelessRedstone", "mechanics/features/wireless_redstone", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                WirelessRedstoneManager.init((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                WirelessRedstoneManager.restoreAllPowerBlocks();
                WirelessRedstoneManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                WirelessRedstoneManager.reloadConfig();
            }
        });

        // Meteor
        mm.register(new MeteorModule());
        CoreHooks.setShutdownGranter(ShutdownListener::grantToAllOnline);
    }
}
