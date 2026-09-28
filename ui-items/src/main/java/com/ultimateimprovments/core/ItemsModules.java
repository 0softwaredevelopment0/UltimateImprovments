package com.ultimateimprovments.core;

import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.mechanics.crafting.*;
import com.ultimateimprovments.mechanics.features.items.*;
import com.ultimateimprovments.mechanics.features.omniscanner.OmniscannerModule;
import com.ultimateimprovments.mechanics.features.scanner.MetalDetectorListener;
import com.ultimateimprovments.mechanics.features.scanner.ScannerItemListener;
import com.ultimateimprovments.mechanics.features.world.AntimatterManager;
import com.ultimateimprovments.mechanics.features.world.ChunkLoaderItemListener;
import com.ultimateimprovments.mechanics.features.world.ConcreteBucketManager;
import com.ultimateimprovments.mechanics.features.world.EntityLocatorManager;
import com.ultimateimprovments.mechanics.features.world.WaypointManager;
import com.ultimateimprovments.mechanics.particle.ParticleAcceleratorManager;
import com.ultimateimprovments.mechanics.particle.ParticleMovementTask;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.module.SimpleModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * ItemsModules — registration of the custom-items addon (crafting recipes,
 * item tools, particle accelerator, scanner items, omniscanner/admin menu).
 * Moved out of ui-other's SimpleModules.
 */
public final class ItemsModules {

    private ItemsModules() {}

    public static void registerAll(ModuleManager mm) {
        // Crafting (essential — crafting is a key plugin mechanic)
        mm.register(new SimpleModule("Crafting", "mechanics/crafting", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                MultimeterCraftListener.init();
                PlasmaCannonCraftListener.init();
                ShokerCraftListener.init();
                BlazingSwordCraftListener.init();
                GlassSwordCraftListener.init();
                ElectricTridentCraftListener.init();
                AntimatterCraftListener.init();
                EntityLocatorCraftListener.init();
                LeadIngotCraftListener.init();
                HazmatCraftListener.init();
                DosimeterCraftListener.init();
                HealthMeterCraftListener.init();
                OreFinderCraftListener.init();
                MobFinderCraftListener.init();
                PortableRadarCraftListener.init();
                MetalDetectorCraftListener.init();
                ConcreteBucketCraftListener.init();
                ChunkLoaderCraftListener.init();
                HeavyCoreCraftListener.init();
                RecipeRegistry.init();

                var pm = main.getServer().getPluginManager();
                pm.registerEvents(new MultimeterCraftListener(), main);
                pm.registerEvents(new PlasmaCannonCraftListener(), main);
                pm.registerEvents(new ShokerCraftListener(), main);
                pm.registerEvents(new BlazingSwordCraftListener(), main);
                pm.registerEvents(new GlassSwordCraftListener(), main);
                pm.registerEvents(new ElectricTridentCraftListener(), main);
                pm.registerEvents(new AntimatterCraftListener(), main);
                pm.registerEvents(new EntityLocatorCraftListener(), main);
                pm.registerEvents(new LeadIngotCraftListener(), main);
                pm.registerEvents(new DosimeterCraftListener(), main);
                pm.registerEvents(new HealthMeterCraftListener(), main);
                pm.registerEvents(new OreFinderCraftListener(), main);
                pm.registerEvents(new MobFinderCraftListener(), main);
                pm.registerEvents(new PortableRadarCraftListener(), main);
                pm.registerEvents(new MetalDetectorCraftListener(), main);
                pm.registerEvents(new ScannerItemListener(), main);
                pm.registerEvents(new MetalDetectorListener(), main);
                pm.registerEvents(new ConcreteBucketCraftListener(), main);
                pm.registerEvents(new ChunkLoaderCraftListener(), main);
                pm.registerEvents(new ChunkLoaderItemListener(), main);
                ConcreteBucketManager.init(main);

                ConsoleLogger.info("[CraftingModule] Recipes initialized.");
            }
        });

        // EntityLocator
        mm.register(new SimpleModule("EntityLocator", "mechanics/features/entity_locator", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EntityLocatorManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                EntityLocatorManager.reloadConfig();
            }
        });

        // Waypoint
        mm.register(new SimpleModule("Waypoint", "mechanics/features/waypoint", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                WaypointManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                WaypointManager.reloadConfig();
            }
        });

        // Antimatter
        mm.register(new SimpleModule("Antimatter", "mechanics/features/antimatter", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                AntimatterManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                AntimatterManager.reloadConfig();
            }
        });

        // ExpBottleUpgrade — charged experience bottles (anvil x1+x1→x2 etc.)
        mm.register(new SimpleModule("ExpBottleUpgrade", "mechanics/features/exp_bottle", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                ExpBottleUpgradeListener.loadConfig(main);
                main.getServer().getPluginManager().registerEvents(new ExpBottleUpgradeListener(), main);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ExpBottleUpgradeListener.loadConfig((Main) plugin);
            }
        });

        // Notes
        mm.register(new SimpleModule("Notes", "mechanics/features/notes", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                NotesManager.init();
                CoreHooks.setNotesOpener(NotesGUI::openMainGUI);
            }
        });

        // UnbreakableBreaker
        mm.register(new SimpleModule("UnbreakableBreaker", "mechanics/features/unbreakable_breaker", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                UnbreakableBreakerManager.init((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                UnbreakableBreakerManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                UnbreakableBreakerManager.reloadConfig();
            }
        });

        // Particle accelerator (item tools + machine)
        mm.register(new PluginModule("ParticleAccelerator", "mechanics/particle", false) {
            private BukkitTask movementTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                ParticleAcceleratorManager.init(main);

                // Movement task every tick
                movementTask = new ParticleMovementTask().runTaskTimer(main, 20L, 1L);

                // Register crafting recipes
                ParticleRingCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleRingCraftListener(), main);

                ParticleEngineCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleEngineCraftListener(), main);

                ParticleSensorCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleSensorCraftListener(), main);

                ParticleInjectorCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleInjectorCraftListener(), main);

                // Expose read-only access for ui-other tools (multimeter, structure chunks).
                CoreHooks.setParticleAccelerator(new CoreHooks.ParticleAccelerator() {
                    @Override
                    public org.bukkit.Material engineMaterial() {
                        return ParticleAcceleratorManager.ENGINE;
                    }

                    @Override
                    public org.bukkit.Material sensorMaterial() {
                        return ParticleAcceleratorManager.SENSOR;
                    }

                    @Override
                    public double maxSpeed() {
                        return ParticleAcceleratorManager.MAX_SPEED;
                    }

                    @Override
                    public int getEngineEnergy(org.bukkit.Location loc) {
                        return ParticleAcceleratorManager.getEngineEnergy(loc);
                    }

                    @Override
                    public boolean canEngineAccelerate(org.bukkit.Location loc) {
                        return ParticleAcceleratorManager.canEngineAccelerate(loc);
                    }

                    @Override
                    public double getSensorLastSpeed(org.bukkit.Location loc) {
                        return ParticleAcceleratorManager.getSensorLastSpeed(loc);
                    }

                    @Override
                    public void scanExistingAccelerators() {
                        ParticleAcceleratorManager.scanExistingAccelerators();
                    }
                });

                ConsoleLogger.info("[ParticleModule] Particle accelerator system initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (movementTask != null) {
                    movementTask.cancel();
                    movementTask = null;
                }
                ParticleAcceleratorManager.shutdown();
            }
        });

        // Item enchant listeners (ChestplateFlight / NetheriteUpgrade / TotemCharge)
        mm.register(new SimpleModule("ItemEnchantListeners", "mechanics/features/items", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                main.getServer().getPluginManager().registerEvents(new ChestplateFlightListener(), main);
                main.getServer().getPluginManager().registerEvents(new NetheriteUpgradeListener(), main);
                main.getServer().getPluginManager().registerEvents(new TotemChargeListener(), main);
                TotemChargeListener.startPeriodicLoreCheck();
            }
        });

        // Omniscanner + Admin menu (/ui menu)
        mm.register(new OmniscannerModule());
    }
}
