package com.ultimateimprovments.module;

import com.ultimateimprovments.broadcast.AutoBroadcastManager;
import com.ultimateimprovments.chat.ChatManager;
import com.ultimateimprovments.command.PowerManager;
import com.ultimateimprovments.command.vote.VoteManager;
import com.ultimateimprovments.combat.turret.TurretListener;
import com.ultimateimprovments.combat.turret.TurretManager;
import com.ultimateimprovments.combat.weapons.blazing.BlazingSwordListener;
import com.ultimateimprovments.combat.weapons.electrictrident.ElectricTridentListener;
import com.ultimateimprovments.combat.weapons.plasma.GunListener;
import com.ultimateimprovments.combat.weapons.shoker.ShokerListener;
import com.ultimateimprovments.core.CommandRegistrar;
import com.ultimateimprovments.core.DatapackInstaller;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.TaskManager;
import com.ultimateimprovments.database.AsyncAutoSaveManager;
import com.ultimateimprovments.database.DatabaseInit;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.display.BossBarManager;
import com.ultimateimprovments.display.ScoreboardManager;
import com.ultimateimprovments.display.TabManager;
import com.ultimateimprovments.economy.EconomyManager;
import com.ultimateimprovments.economy.EconomyPlaceholderExpansion;
import com.ultimateimprovments.economy.VaultIntegration;
import com.ultimateimprovments.economy.listeners.IncomeListener;
import com.ultimateimprovments.economy.listeners.PlayerJoinListener;
import com.ultimateimprovments.enchantment.aoe.EnchantmentListener;
import com.ultimateimprovments.enchantment.aoe.EnchantmentSyncListener;
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.generation.reactor.ReactorListener;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.energy.machines.furnace.ElectricFurnaceManager;
import com.ultimateimprovments.energy.machines.workbench.EnergyCraftingListener;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.hook.PluginHook;
import com.ultimateimprovments.listener.BlockBreakListener;
import com.ultimateimprovments.listener.BlockPlaceListener;
import com.ultimateimprovments.listener.ChatFilterManager;
import com.ultimateimprovments.listener.FishingListener;
import com.ultimateimprovments.listener.MOTDListener;
import com.ultimateimprovments.listener.MultimeterListener;
import com.ultimateimprovments.listener.PluginHideListener;
import com.ultimateimprovments.listener.PowerInterceptListener;
import com.ultimateimprovments.listener.ServerBrandListener;
import com.ultimateimprovments.listener.ShulkerBulletListener;
import com.ultimateimprovments.listener.VoidProtectionListener;
import com.ultimateimprovments.mechanics.crafting.AntimatterCraftListener;
import com.ultimateimprovments.mechanics.crafting.BlazingSwordCraftListener;
import com.ultimateimprovments.mechanics.crafting.ChunkLoaderCraftListener;
import com.ultimateimprovments.mechanics.crafting.ElectricTridentCraftListener;
import com.ultimateimprovments.mechanics.crafting.ConcreteBucketCraftListener;
import com.ultimateimprovments.mechanics.crafting.EnderChestCraftListener;
import com.ultimateimprovments.mechanics.crafting.EntityLocatorCraftListener;
import com.ultimateimprovments.mechanics.crafting.GlassSwordCraftListener;
import com.ultimateimprovments.mechanics.crafting.HealthMeterCraftListener;
import com.ultimateimprovments.mechanics.crafting.LeadIngotCraftListener;
import com.ultimateimprovments.mechanics.crafting.LeadShieldCraftListener;
import com.ultimateimprovments.mechanics.crafting.MetalDetectorCraftListener;
import com.ultimateimprovments.mechanics.crafting.MobFinderCraftListener;
import com.ultimateimprovments.mechanics.crafting.MultimeterCraftListener;
import com.ultimateimprovments.mechanics.crafting.OreFinderCraftListener;
import com.ultimateimprovments.mechanics.crafting.ParticleEngineCraftListener;
import com.ultimateimprovments.mechanics.crafting.ParticleInjectorCraftListener;
import com.ultimateimprovments.mechanics.crafting.ParticleRingCraftListener;
import com.ultimateimprovments.mechanics.crafting.ParticleSensorCraftListener;
import com.ultimateimprovments.mechanics.crafting.PlasmaCannonCraftListener;
import com.ultimateimprovments.mechanics.crafting.PortableRadarCraftListener;
import com.ultimateimprovments.mechanics.crafting.RecipeRegistry;
import com.ultimateimprovments.mechanics.crafting.ShokerCraftListener;
import com.ultimateimprovments.mechanics.crafting.StructureIntegrityCraftListener;
import com.ultimateimprovments.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetConfig;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetEventListener;
import com.ultimateimprovments.mechanics.environment.magnet.MagnetManager;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.mechanics.features.blocks.BlockDmgManager;
import com.ultimateimprovments.mechanics.features.blocks.BoostedCobwebManager;
import com.ultimateimprovments.mechanics.features.blocks.ContainerTriggerManager;
import com.ultimateimprovments.mechanics.features.blocks.EnderChestManager;
import com.ultimateimprovments.mechanics.features.blocks.GlassBreakManager;
import com.ultimateimprovments.mechanics.features.blocks.TerracotaSpeedManager;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseListener;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseManager;
import com.ultimateimprovments.mechanics.features.creativeitem.CreativeItemValidator;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityCombineListener;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityListener;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import com.ultimateimprovments.mechanics.features.items.AutoCraftManager;
import com.ultimateimprovments.mechanics.features.items.ChestplateFlightListener;
import com.ultimateimprovments.mechanics.features.items.ExpBottleUpgradeListener;
import com.ultimateimprovments.mechanics.features.items.NetheriteUpgradeListener;
import com.ultimateimprovments.mechanics.features.items.NotesManager;
import com.ultimateimprovments.mechanics.features.items.TotemChargeListener;
import com.ultimateimprovments.mechanics.features.items.UnbreakableBreakerManager;
import com.ultimateimprovments.mechanics.features.movement.BlockFrictionListener;
import com.ultimateimprovments.mechanics.features.player.AttributesManager;
import com.ultimateimprovments.mechanics.features.player.ElytraBoostManager;
import com.ultimateimprovments.mechanics.features.player.LeashManager;
import com.ultimateimprovments.mechanics.features.player.ModeProtectManager;
import com.ultimateimprovments.mechanics.features.player.ShieldSlownessManager;
import com.ultimateimprovments.mechanics.features.player.VanishManager;
import com.ultimateimprovments.mechanics.features.scanner.MetalDetectorListener;
import com.ultimateimprovments.mechanics.features.scanner.ScannerItemListener;
import com.ultimateimprovments.mechanics.features.structure.StructureIntegrityListener;
import com.ultimateimprovments.mechanics.features.structure.StructureIntegrityManager;
import com.ultimateimprovments.mechanics.features.updater.UpdateChecker;
import com.ultimateimprovments.mechanics.features.world.AntimatterManager;
import com.ultimateimprovments.mechanics.features.world.BedrockBreakListener;
import com.ultimateimprovments.mechanics.features.world.BeyondSpaceListener;
import com.ultimateimprovments.mechanics.features.world.CmdBlockTracker;
import com.ultimateimprovments.mechanics.features.world.EarthCoreListener;
import com.ultimateimprovments.mechanics.features.world.KaboomListener;
import com.ultimateimprovments.mechanics.features.world.ServerOverloadListener;
import com.ultimateimprovments.mechanics.features.world.WoodcutterChallenge;
import com.ultimateimprovments.mechanics.features.world.EnderPearlChallenge;
import com.ultimateimprovments.mechanics.features.world.NetheriteKingListener;
import com.ultimateimprovments.mechanics.features.world.OutOfMemoryListener;
import com.ultimateimprovments.mechanics.features.world.ServerFreezeListener;
import com.ultimateimprovments.mechanics.features.world.BeaconManager;
import com.ultimateimprovments.mechanics.features.world.ChunkLoaderItemListener;
import com.ultimateimprovments.mechanics.features.world.ConcreteBucketManager;
import com.ultimateimprovments.mechanics.features.world.DeathBellManager;
import com.ultimateimprovments.mechanics.features.world.DragonEggManager;
import com.ultimateimprovments.mechanics.features.world.EntityLocatorManager;
import com.ultimateimprovments.mechanics.features.world.MinecartSpeedManager;
import com.ultimateimprovments.mechanics.features.world.WaypointManager;
import com.ultimateimprovments.mechanics.features.world.WirelessRedstoneManager;
import com.ultimateimprovments.mechanics.particle.ParticleAcceleratorManager;
import com.ultimateimprovments.mechanics.particle.ParticleMovementTask;
import com.ultimateimprovments.mechanics.security.auth.AuthListener;
import com.ultimateimprovments.mechanics.security.auth.AuthManager;
import com.ultimateimprovments.mechanics.security.botprotect.BotProtectionListener;
import com.ultimateimprovments.mechanics.security.sudo.SudoCommandInterceptor;
import com.ultimateimprovments.mechanics.security.sudo.SudoManager;
import com.ultimateimprovments.punish.PunishJoinListener;
import com.ultimateimprovments.punish.PunishmentManager;
import com.ultimateimprovments.server.EmergencyEntitiesKill;
import com.ultimateimprovments.server.PacketGuard;
import com.ultimateimprovments.server.ProxyServerListener;
import com.ultimateimprovments.server.RedstoneGuard;
import com.ultimateimprovments.server.RedstoneGuardListener;
import com.ultimateimprovments.server.ServerOverloadWarning;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * SimpleModules — registry of all simple (thin) wrapper modules.
 * <p>
 * Previously each such module lived in its own file (~60 files of 17-77 lines):
 * constructor + calling {@code XxxManager.init()} in {@code onInit} and an empty
 * {@code onDisable}. Now they're all collected in this single file as anonymous
 * classes grouped by domain — exactly in the order {@code PluginStartup} registered them.
 * <p>
 * Modules with non-trivial logic (Core, Database, Economy, AntiCheat, ...)
 * remain separate classes.
 * <p>
 * IMPORTANT: these modules are registered manually via {@code PluginStartup} —
 * auto-scanning (ModuleScanner) won't find them because they're anonymous classes.
 */
public final class SimpleModules {

    private SimpleModules() {}

    /**
     * Base for most modules: onDisable — no-op.
     * Modules with task cleanup/shutdown override onDisable
     * and inherit PluginModule directly.
     */
    private abstract static class SimpleModule extends PluginModule {
        SimpleModule(String name, String path, boolean essential) {
            super(name, path, essential);
        }

        @Override
        protected void onDisable(JavaPlugin plugin) {}
    }

    // ==========================================================================
    // 🧩 REGISTRATION GROUPS (order = PluginStartup order)
    // ==========================================================================

    // --------------------------------------------------------------------------
    // CORE (Database + Core — registered right after VersionCheckModule)
    // --------------------------------------------------------------------------

    public static void registerCoreModules(ModuleManager mm) {
        // Database (essential — the plugin doesn't work without the DB)
        mm.register(new PluginModule("Database", "infrastructure/database", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                // SQLITE INIT
                DatabaseManager.connect();
                DatabaseInit.init();
                ConsoleLogger.info("[SQLITE] Database initialized successfully.");

                // Vote Manager (load votes from the DB)
                VoteManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                // Cancel all vote timers
                VoteManager.shutdown();
                try {
                    DatabaseManager.close();
                } catch (Exception e) {
                    ConsoleLogger.warn("[DatabaseModule] Close error: " + e.getMessage());
                }
            }
        });

        // Core (essential — base systems: tasks, commands, common listeners)
        mm.register(new SimpleModule("Core", "infrastructure/core", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // TASK MANAGER & COMMANDS
                TaskManager.init(main);
                CommandRegistrar.init(main);

                // GENERAL LISTENERS
                var pm = main.getServer().getPluginManager();
                pm.registerEvents(new BlockPlaceListener(), main);
                pm.registerEvents(new BlockBreakListener(), main);
                pm.registerEvents(new MultimeterListener(), main);
                pm.registerEvents(new PluginHideListener(), main);
                pm.registerEvents(new ServerBrandListener(), main);
                pm.registerEvents(new ShokerListener(), main);
                pm.registerEvents(new GunListener(), main);
                pm.registerEvents(new BlazingSwordListener(), main);
                pm.registerEvents(new ElectricTridentListener(), main);
                pm.registerEvents(new ShulkerBulletListener(), main);
                pm.registerEvents(FishingListener.getInstance(), main);

                BlockFrictionListener.init();
                pm.registerEvents(new BlockFrictionListener(), main);

                AutoCraftManager.init(main);
            }
        });
    }

    // --------------------------------------------------------------------------
    // SYSTEM
    // --------------------------------------------------------------------------

    public static void registerSystem(ModuleManager mm) {
        // Datapack
        mm.register(new SimpleModule("Datapack", "infrastructure/core", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                DatapackInstaller.init((Main) plugin);
                DatapackInstaller.getInstance().install((Main) plugin);
                // Success is logged inside DatapackInstaller.install()
            }
        });

        // Power
        mm.register(new SimpleModule("Power", "infrastructure/core", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                PowerManager.init();
                main.getServer().getPluginManager().registerEvents(new PowerInterceptListener(), main);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                PowerManager.reloadConfig();
            }
        });
    }

    // --------------------------------------------------------------------------
    // ENERGY
    // --------------------------------------------------------------------------

    public static void registerEnergy(ModuleManager mm) {
        // Cable
        mm.register(new SimpleModule("Cable", "energy/transfer/cable", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                CableNetwork.init();
            }
        });

        // Basic Generator
        mm.register(new PluginModule("Basic Generator", "energy/generation/basic", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                GeneratorManager.init();
                TaskManager.getInstance().startGeneratorTask((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                TaskManager.getInstance().stopGeneratorTask();
                GeneratorManager.shutdown();
            }
        });

        // Reactor
        mm.register(new PluginModule("Reactor", "energy/generation/reactor", true) {
            private ReactorListener reactorListener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                ReactorManager.init();
                reactorListener = new ReactorListener();
                main.getServer().getPluginManager().registerEvents(reactorListener, main);
                TaskManager.getInstance().startReactorTask(main);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                TaskManager.getInstance().stopReactorTask();
                if (reactorListener != null) {
                    HandlerList.unregisterAll(reactorListener);
                    reactorListener = null;
                }
                ReactorManager.shutdown();
            }
        });

        // Electric Furnace
        mm.register(new PluginModule("Electric Furnace", "energy/machines/furnace", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ElectricFurnaceManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                ElectricFurnaceManager.shutdown();
            }
        });

        // Battery Drain
        mm.register(new PluginModule("Battery Drain", "energy/storage/battery", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                TaskManager.getInstance().startBatteryTask((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                TaskManager.getInstance().stopBatteryTask();
            }
        });

        // Battery Multi
        mm.register(new PluginModule("Battery Multi", "energy/storage/battery", false) {
            private org.bukkit.scheduler.BukkitTask tickTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BatteryManager.init();
                tickTask = Bukkit.getScheduler().runTaskTimer((Main) plugin, () -> {
                    BatteryManager.tick();
                }, 0L, 1L);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (tickTask != null) {
                    tickTask.cancel();
                    tickTask = null;
                }
                // Markers persist in world files, no save needed
            }
        });

        // Light Multi
        mm.register(new PluginModule("Light Multi", "energy/consumption/light", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                LightManager.init();
                TaskManager.getInstance().startLightTask((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                TaskManager.getInstance().stopLightTask();
                // Markers persist in world files, no save needed
            }
        });
    }

    // --------------------------------------------------------------------------
    // ENERGY MACHINES (Assembler + Workbench — registered after the energy group)
    // --------------------------------------------------------------------------

    public static void registerEnergyMachines(ModuleManager mm) {
        // Custom recipe gating — custom items craft in the vanilla Crafter block,
        // workbench/2x2 only show the recipe book preview (the old "Item Assembler"
        // structure and its energy requirement were removed).
        mm.register(new PluginModule("Energy Workbench", "energy/machines/workbench", true) {
            private EnergyCraftingListener craftingListener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                craftingListener = new EnergyCraftingListener();
                main.getServer().getPluginManager().registerEvents(craftingListener, main);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (craftingListener != null) {
                    HandlerList.unregisterAll(craftingListener);
                    craftingListener = null;
                }
            }
        });
    }

    // --------------------------------------------------------------------------
    // CRAFTING (registered before Sudo)
    // --------------------------------------------------------------------------

    public static void registerCrafting(ModuleManager mm) {
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
                LeadShieldCraftListener.init();
                HealthMeterCraftListener.init();
                OreFinderCraftListener.init();
                MobFinderCraftListener.init();
                PortableRadarCraftListener.init();
                MetalDetectorCraftListener.init();
                EnderChestCraftListener.init();
                ConcreteBucketCraftListener.init();
                ChunkLoaderCraftListener.init();
                StructureIntegrityCraftListener.init();
                RecipeRegistry.init();

                // Register craft event listeners
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
                pm.registerEvents(new LeadShieldCraftListener(), main);
                pm.registerEvents(new HealthMeterCraftListener(), main);
                pm.registerEvents(new OreFinderCraftListener(), main);
                pm.registerEvents(new MobFinderCraftListener(), main);
                pm.registerEvents(new PortableRadarCraftListener(), main);
                pm.registerEvents(new MetalDetectorCraftListener(), main);
                pm.registerEvents(new EnderChestCraftListener(), main);
                pm.registerEvents(new ScannerItemListener(), main);
                pm.registerEvents(new MetalDetectorListener(), main);
                pm.registerEvents(new ConcreteBucketCraftListener(), main);
                pm.registerEvents(new ChunkLoaderCraftListener(), main);
                pm.registerEvents(new ChunkLoaderItemListener(), main);
                pm.registerEvents(new StructureIntegrityCraftListener(), main);
                ConcreteBucketManager.init(main);

                ConsoleLogger.info("[CraftingModule] ✔ Recipes initialized.");
            }
        });
    }

    // --------------------------------------------------------------------------
    // MECHANICS
    // --------------------------------------------------------------------------

    public static void registerMechanics(ModuleManager mm) {
        // Radiation
        mm.register(new SimpleModule("Radiation", "mechanics/environment/radiation", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                RadiationManager.init();
            }
        });

        // Lightning
        mm.register(new SimpleModule("Lightning", "mechanics/environment/lightning", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                LightningManager.init();
            }
        });

        // Auth
        mm.register(new SimpleModule("Auth", "mechanics/security/auth", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                AuthManager.init();
                main.getServer().getPluginManager().registerEvents(new AuthListener(), main);
                ConsoleLogger.info("[AuthModule] ✔ Auth system initialized.");
            }
        });
    }

    // --------------------------------------------------------------------------
    // SUDO (GitHub-style, registered after CraftingModule)
    // --------------------------------------------------------------------------

    public static void registerSudo(ModuleManager mm) {
        mm.register(new SimpleModule("Sudo", "mechanics/security/sudo", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                if (!main.getConfig().getBoolean("features.sudo.enabled", true)) {
                    ConsoleLogger.info("[SudoModule] Sudo mode is disabled in config (features.sudo.enabled: false).");
                    return;
                }
                SudoManager.init();
                main.getServer().getPluginManager().registerEvents(new SudoCommandInterceptor(), main);
                main.getServer().getPluginManager().registerEvents(new SudoQuitListener(), main);
                ConsoleLogger.info("[SudoModule] ✔ Sudo mode initialized.");
            }
        });
    }

    /**
     * Clears a player's sudo state on quit (sessions, cooldowns, pending commands).
     */
    private static class SudoQuitListener implements Listener {
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            SudoManager manager = SudoManager.getInstance();
            if (manager != null) {
                manager.removePlayer(event.getPlayer().getUniqueId());
            }
        }
    }

    // --------------------------------------------------------------------------
    // FEATURES
    // --------------------------------------------------------------------------

    public static void registerFeatures(ModuleManager mm) {
        // Attributes
        mm.register(new SimpleModule("Attributes", "mechanics/features/attributes", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                AttributesManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                AttributesManager.reloadConfig();
            }
        });

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

        // BlockDmg
        mm.register(new SimpleModule("BlockDmg", "mechanics/features/block_damage", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BlockDmgManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                BlockDmgManager.reloadConfig();
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

        // BoostedCobweb
        mm.register(new SimpleModule("BoostedCobweb", "mechanics/features/cobweb_boost", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BoostedCobwebManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                BoostedCobwebManager.reloadConfig();
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

        // Magnet
        mm.register(new SimpleModule("Magnet", "mechanics/environment/magnet", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                MagnetManager.init(main);
                main.getServer().getPluginManager().registerEvents(new MagnetEventListener(), main);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                MagnetConfig.reloadConfig();
            }
        });

        // ModeProtect
        mm.register(new SimpleModule("ModeProtect", "mechanics/features/mode_protect", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ModeProtectManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ModeProtectManager.reloadConfig();
            }
        });

        // TerracotaSpeed
        mm.register(new SimpleModule("TerracotaSpeed", "mechanics/features/terracotta_speed", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                TerracotaSpeedManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                TerracotaSpeedManager.reloadConfig();
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

        // Integrity
        mm.register(new SimpleModule("Integrity", "mechanics/features/integrity", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                IntegrityManager.init(main);
                main.getServer().getPluginManager().registerEvents(new IntegrityListener(), main);
                main.getServer().getPluginManager().registerEvents(new IntegrityCombineListener(), main);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                IntegrityManager.reloadConfig();
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

        // Beyond Space — reach the block placement limit
        mm.register(new SimpleModule("BeyondSpace", "mechanics/features/beyond_space", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BeyondSpaceListener.register((Main) plugin);
            }
        });

        // Hit, hit, to pieces! — break a bedrock block
        mm.register(new SimpleModule("BedrockBreak", "mechanics/features/bedrock_break", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BedrockBreakListener.register((Main) plugin);
            }
        });

        // Kaboom! — kill a mob after dealing 1,000 mace damage
        mm.register(new SimpleModule("Kaboom", "mechanics/features/kaboom", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                KaboomListener.register((Main) plugin);
            }
        });

        // Where is the Earth's core here? — reach the lower placement limit
        mm.register(new SimpleModule("EarthCore", "mechanics/features/earth_core", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EarthCoreListener.register((Main) plugin);
            }
        });

        // Something's not right here... — online while the server is overloaded
        mm.register(new SimpleModule("ServerOverload", "mechanics/features/server_overload", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ServerOverloadListener.register((Main) plugin);
            }
        });

        // The Woodcutter at Full Throttle — timed challenge (7,200 wood in 1 hour)
        mm.register(new SimpleModule("WoodcutterChallenge", "mechanics/features/woodcutter_challenge", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                WoodcutterChallenge.register((Main) plugin);
            }
        });

        // Let me teleport! — timed challenge (60 ender pearl teleports in 1 minute)
        mm.register(new SimpleModule("EnderPearlChallenge", "mechanics/features/ender_pearl_challenge", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EnderPearlChallenge.register((Main) plugin);
            }
        });

        // A Netherite King — netherite block in inventory
        mm.register(new SimpleModule("NetheriteKing", "mechanics/features/netherite_king", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                NetheriteKingListener.register((Main) plugin);
            }
        });

        // java.lang.OutOfMemoryError — RAM usage at 100%
        mm.register(new SimpleModule("OutOfMemory", "mechanics/features/out_of_memory", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                OutOfMemoryListener.register((Main) plugin);
            }
        });

        // The server has not responding! — main thread frozen 10+ seconds
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

        // DeathLogger — records every player death to deaths.log + console (debug; off by default)
        mm.register(new SimpleModule("DeathLogger", "mechanics/features/death_logger", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                com.ultimateimprovments.listener.DeathLogger.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                com.ultimateimprovments.listener.DeathLogger.reloadConfig();
            }
        });

        // UnbreakableBreaker
        mm.register(new SimpleModule("UnbreakableBreaker", "mechanics/features/unbreakable_breaker", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                UnbreakableBreakerManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                UnbreakableBreakerManager.reloadConfig();
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

        // EnderChest
        mm.register(new SimpleModule("EnderChest", "mechanics/features/ender_chest", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EnderChestManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                EnderChestManager.reloadConfig();
            }
        });

        // GlassBreak
        mm.register(new SimpleModule("GlassBreak", "mechanics/features/glass_break", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                GlassBreakManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                GlassBreakManager.reloadConfig();
            }
        });

        // ShieldSlowness
        mm.register(new SimpleModule("ShieldSlowness", "mechanics/features/shield_slowness", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ShieldSlownessManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ShieldSlownessManager.reloadConfig();
            }
        });

        // CreativeItemValidator
        mm.register(new PluginModule("CreativeItemValidator", "mechanics/features/creativeitem", false) {
            private CreativeItemValidator listener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                CreativeItemValidator.init((Main) plugin);
                listener = CreativeItemValidator.getInstance();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (listener != null) {
                    HandlerList.unregisterAll(listener);
                    listener = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                CreativeItemValidator.reloadConfig();
            }
        });

        // ContainerTrigger
        mm.register(new SimpleModule("ContainerTrigger", "mechanics/features/container_trigger", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ContainerTriggerManager.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ContainerTriggerManager.reloadConfig();
            }
        });

        // Vanish
        mm.register(new SimpleModule("Vanish", "mechanics/features/vanish", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                VanishManager.init();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                VanishManager.reloadConfig();
            }
        });

        // Notes
        mm.register(new SimpleModule("Notes", "mechanics/features/notes", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                NotesManager.init();
            }
        });

        // MinecartSpeed
        mm.register(new PluginModule("MinecartSpeed", "mechanics/features/minecart_speed", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                MinecartSpeedManager.init((Main) plugin);
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
                // Reset the singleton + cancel the watcher task, otherwise after
                // /ui reload the old watcher either stays alive (duplicate),
                // or init() with its guard won't restart it at all.
                WirelessRedstoneManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                WirelessRedstoneManager.reloadConfig();
            }
        });
    }

    // --------------------------------------------------------------------------
    // ECONOMY (registered before AOEEnchantment)
    // --------------------------------------------------------------------------

    public static void registerEconomy(ModuleManager mm) {
        // Economy — currency system (core + Vault + PAPI)
        mm.register(new SimpleModule("Economy", "economy", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                // 1. Core
                EconomyManager.init();

                // 2. Vault integration (only if Vault is installed)
                if (PluginHook.check("Vault", "Economy")) {
                    new VaultIntegration(plugin);
                }

                // 3. Events
                var pm = plugin.getServer().getPluginManager();
                pm.registerEvents(new PlayerJoinListener(), plugin);
                pm.registerEvents(new IncomeListener(), plugin);

                // 4. PAPI expansion
                try {
                    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        new EconomyPlaceholderExpansion().register();
                        ConsoleLogger.info("[Economy] PlaceholderAPI expansion registered.");
                    }
                } catch (NoClassDefFoundError | Exception e) {
                    ConsoleLogger.info("[Economy] PlaceholderAPI not found — placeholders disabled.");
                }

            }
        });
    }

    // --------------------------------------------------------------------------
    // AOE ENCHANTMENT (registered after EconomyModule)
    // --------------------------------------------------------------------------

    public static void registerAOEEnchantment(ModuleManager mm) {
        // AoE (Area of Effect) Enchantment: REAL data-driven enchantment
        // (ui:aoe, registered by the UI-Datapack) + PDC mirror failsafe.
        mm.register(new SimpleModule("AOEEnchantment", "enchantment/aoe", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener
                main.getServer().getPluginManager().registerEvents(new EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[AoE] Max level: 255 | Radius = level | Tools: pickaxe, shovel, axe, hoe");
                ConsoleLogger.info("[AoE] Sneak to disable AoE for precise mining");
            }
        });
    }

    // --------------------------------------------------------------------------
    // AUTOSMELT ENCHANTMENT (registered after AOEEnchantment)
    // --------------------------------------------------------------------------

    public static void registerAutoSmeltEnchantment(ModuleManager mm) {
        // AutoSmelt: REAL data-driven enchantment (ui:autosmelt, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Smelts block drops.
        mm.register(new SimpleModule("AutoSmeltEnchantment", "enchantment/autosmelt", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener (smelts drops)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.autosmelt.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.autosmelt.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[AutoSmelt] Level: 1 | Tools: pickaxe, shovel, axe, hoe");
            }
        });
    }

    // --------------------------------------------------------------------------
    // VEINMINER ENCHANTMENT (registered after AutoSmeltEnchantment)
    // --------------------------------------------------------------------------

    public static void registerVeinMinerEnchantment(ModuleManager mm) {
        // VeinMiner: REAL data-driven enchantment (ui:veinminer, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Breaks whole ore veins.
        mm.register(new SimpleModule("VeinMinerEnchantment", "enchantment/veinminer", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener (flood-fills the ore vein)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.veinminer.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.veinminer.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[VeinMiner] Level: 1 | Tool: pickaxe | Mines whole ore veins");
                ConsoleLogger.info("[VeinMiner] Sneak to disable VeinMiner for precise mining");
            }
        });
    }

    // --------------------------------------------------------------------------
    // TREECAPITATOR ENCHANTMENT (registered after VeinMinerEnchantment)
    // --------------------------------------------------------------------------

    public static void registerTreeCapitatorEnchantment(ModuleManager mm) {
        // TreeCapitator: REAL data-driven enchantment (ui:treecapitator, registered
        // by the UI-Datapack, max level 1) + PDC mirror failsafe. Fells whole trees.
        mm.register(new SimpleModule("TreeCapitatorEnchantment", "enchantment/treecapitator", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener (flood-fills the tree trunk)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.treecapitator.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.treecapitator.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[TreeCapitator] Level: 1 | Tool: axe | Fells whole trees");
                ConsoleLogger.info("[TreeCapitator] Sneak to disable TreeCapitator for precise cutting");
            }
        });
    }

    // --------------------------------------------------------------------------
    // FLIGHT ENCHANTMENT (registered after TreeCapitatorEnchantment)
    // --------------------------------------------------------------------------

    public static void registerFlightEnchantment(ModuleManager mm) {
        // Flight: REAL data-driven enchantment (ui:flight, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Fly like Creative
        // while the enchanted chestplate is worn.
        mm.register(new SimpleModule("FlightEnchantment", "enchantment/flight", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Flight listener (grants/revokes allowFlight) + periodic sweep
                com.ultimateimprovments.enchantment.flight.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.flight.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Flight] Level: 1 | Item: chestplate | Fly like Creative while worn");
            }
        });
    }

    // --------------------------------------------------------------------------
    // MAGNET ENCHANTMENT (registered after FlightEnchantment)
    // --------------------------------------------------------------------------

    public static void registerMagnetEnchantment(ModuleManager mm) {
        // Magnet: REAL data-driven enchantment (ui:magnet, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Attracts freshly
        // dropped items to the player (works with AoE/VeinMiner/TreeCapitator drops).
        mm.register(new SimpleModule("MagnetEnchantment", "enchantment/magnet", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Attraction listener (pull sweep every tick)
                com.ultimateimprovments.enchantment.magnet.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.magnet.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Magnet] Level: 1 | Tools: pickaxe, shovel, axe, hoe | Pull: 0.5 blk/s");
            }
        });
    }

    // --------------------------------------------------------------------------
    // IGNITING ENCHANTMENT (registered after MagnetEnchantment)
    // --------------------------------------------------------------------------

    public static void registerIgnitingEnchantment(ModuleManager mm) {
        // Igniting: REAL data-driven enchantment (ui:igniting, registered by
        // the UI-Datapack, levels 1-255) + PDC mirror failsafe. Armor ignites the
        // attacker for level seconds when the wearer is hit.
        mm.register(new SimpleModule("IgnitingEnchantment", "enchantment/igniting", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Damage listener (ignites attackers of armored wearers)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.igniting.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.igniting.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Igniting] Levels: 1-255 | Armor: helmet, chestplate, leggings, boots");
                ConsoleLogger.info("[Igniting] Attackers of the wearer are set on fire for level seconds");
            }
        });
    }

    // --------------------------------------------------------------------------
    // LEVITATION ENCHANTMENT (registered after IgnitingEnchantment)
    // --------------------------------------------------------------------------

    public static void registerLevitationEnchantment(ModuleManager mm) {
        // Levitation: REAL data-driven enchantment (ui:levitation, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Holding the jump
        // key while the enchanted chestplate is worn gently lifts the player up.
        mm.register(new SimpleModule("LevitationEnchantment", "enchantment/levitation", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Jetpack listener (periodic jump-key sweep)
                com.ultimateimprovments.enchantment.levitation.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.levitation.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Levitation] Level: 1 | Item: chestplate | Jump key = gentle jetpack while worn");
            }
        });
    }

    // --------------------------------------------------------------------------
    // SELF-DESTRUCT ENCHANTMENT (registered after LevitationEnchantment)
    // --------------------------------------------------------------------------

    public static void registerSelfDestructEnchantment(ModuleManager mm) {
        // SelfDestruct: REAL data-driven curse (ui:self_destruct, registered by
        // the UI-Datapack, max level 1, in the #minecraft:curse tag → red tooltip)
        // + PDC mirror failsafe. 30s silent countdown → 19 damage to the holder.
        mm.register(new SimpleModule("SelfDestructEnchantment", "enchantment/selfdestruct", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Countdown engine (sweep every tick) + quit cleanup
                com.ultimateimprovments.enchantment.selfdestruct.EnchantmentListener.register(main);

                // 2. Inventory lock — the cursed item can't be removed during the timer
                main.getServer().getPluginManager().registerEvents(
                        new com.ultimateimprovments.enchantment.selfdestruct.InventoryLockListener(), main);

                // 3. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.selfdestruct.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[SelfDestruct] Level: 1 | Item: any | 30s silent timer → 19 damage to the holder + item destroyed");
            }
        });
    }

    // --------------------------------------------------------------------------
    // DEGRADATION ENCHANTMENT (registered after SelfDestructEnchantment)
    // --------------------------------------------------------------------------

    public static void registerDegradationEnchantment(ModuleManager mm) {
        // Degradation: REAL data-driven curse (ui:degradation, registered by
        // the UI-Datapack, levels 1-255, in the #minecraft:curse tag → red tooltip)
        // + PDC mirror failsafe. Every second a cursed item with durability loses
        // level durability points; when durability runs out the item breaks.
        mm.register(new SimpleModule("DegradationEnchantment", "enchantment/degradation", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Durability drain engine (sweep every second) + quit cleanup
                com.ultimateimprovments.enchantment.degradation.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.degradation.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Degradation] Levels: 1-255 | Item: any with durability | Spends "
                        + "integrity as after level uses per second while in a player's inventory");
            }
        });
    }

    // --------------------------------------------------------------------------
    // ATTACK AOE ENCHANTMENT (registered after DegradationEnchantment)
    // --------------------------------------------------------------------------

    public static void registerAttackAoeEnchantment(ModuleManager mm) {
        // Attack AoE: REAL data-driven enchantment (ui:attack_aoe, registered by
        // the UI-Datapack, levels 1-255) + PDC mirror failsafe. Hitting one entity
        // damages every living entity in a (2·level+1)³ cube around the victim
        // with the same force. Sneaking disables it for precise attacks.
        mm.register(new SimpleModule("AttackAoeEnchantment", "enchantment/attackaoe", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Damage listener (cleaves entities around the victim)
                main.getServer().getPluginManager().registerEvents(
                        new com.ultimateimprovments.enchantment.attackaoe.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.attackaoe.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[AttackAoE] Levels: 1-255 | Weapons: swords, axes | Radius: "
                        + "(2·level+1)³ cube (level 1 → 3×3, level 2 → 5×5, ...)");
                ConsoleLogger.info("[AttackAoE] Hit one entity → all entities in the radius take the same damage");
                ConsoleLogger.info("[AttackAoE] Sneak to disable AoE for precise single-target attacks");
            }
        });
    }

    // --------------------------------------------------------------------------
    // ITEM STEALING ENCHANTMENT (registered after AttackAoeEnchantment)
    // --------------------------------------------------------------------------

    public static void registerItemStealingEnchantment(ModuleManager mm) {
        // Item Stealing: REAL data-driven enchantment (ui:item_stealing, registered
        // by the UI-Datapack, max level 1) + PDC mirror failsafe. Hooking a player
        // with the enchanted fishing rod and reeling in steals the item from his
        // hand instead of pulling him; empty hands → normal pull.
        mm.register(new SimpleModule("ItemStealingEnchantment", "enchantment/itemstealing", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Fishing listener (steal the held item instead of pulling the player)
                main.getServer().getPluginManager().registerEvents(
                        new com.ultimateimprovments.enchantment.itemstealing.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.itemstealing.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[ItemStealing] Level: 1 | Item: fishing rod | Hooking a player and reeling in "
                        + "steals the item from his hand (empty hands → normal pull)");
            }
        });
    }

    // --------------------------------------------------------------------------
    // REPAIRING ENCHANTMENT (registered after ItemStealingEnchantment)
    // --------------------------------------------------------------------------

    public static void registerRepairingEnchantment(ModuleManager mm) {
        // Repairing: REAL data-driven enchantment (ui:repairing, registered by
        // the UI-Datapack, levels 1-255) + PDC mirror failsafe. Every second an
        // enchanted item with durability restores level × 0.1% of its integrity
        // (level 1 → 0.1%/s, level 255 → 25.5%/s) while in a player's inventory.
        mm.register(new SimpleModule("RepairingEnchantment", "enchantment/repairing", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Integrity repair engine (sweep every second)
                com.ultimateimprovments.enchantment.repairing.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.repairing.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Repairing] Levels: 1-255 | Item: any with durability | Restores "
                        + "level × 0.1% integrity every level seconds (0.1%/s average) while in a player's inventory");
            }
        });
    }

    // --------------------------------------------------------------------------
    // TURRET (end crystal turrets)
    // --------------------------------------------------------------------------

    public static void registerTurret(ModuleManager mm) {
        mm.register(new PluginModule("Turret", "combat/turret", false) {
            private BukkitTask tickTask;
            private TurretListener listener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                TurretManager.init();
                listener = new TurretListener();
                main.getServer().getPluginManager().registerEvents(listener, main);
                tickTask = Bukkit.getScheduler().runTaskTimer(main, TurretManager.getInstance()::tick, 0L, 1L);
                ConsoleLogger.info("[Turret] End crystal turrets initialized (16³ range, 1 dmg/tick, line of sight required).");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (tickTask != null) {
                    tickTask.cancel();
                    tickTask = null;
                }
                if (listener != null) {
                    HandlerList.unregisterAll(listener);
                    listener = null;
                }
                TurretManager.shutdown();
            }
        });
    }

    // --------------------------------------------------------------------------
    // PROTECTION
    // --------------------------------------------------------------------------

    public static void registerProtection(ModuleManager mm) {
        // RedstoneGuard
        mm.register(new SimpleModule("RedstoneGuard", "infrastructure/server", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                RedstoneGuard.init(main);
                main.getServer().getPluginManager().registerEvents(new RedstoneGuardListener(), main);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                RedstoneGuard.reload();
                EmergencyEntitiesKill.reload();
                ServerOverloadWarning.reload();
            }
        });

        // PacketGuard
        mm.register(new SimpleModule("PacketGuard", "infrastructure/server", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                PacketGuard.init((Main) plugin);
            }
        });

        // ProxyServer
        mm.register(new SimpleModule("ProxyServer", "infrastructure/server", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ProxyServerListener.init((Main) plugin);
            }
        });
    }

    // --------------------------------------------------------------------------
    // UTILITY
    // --------------------------------------------------------------------------

    public static void registerUtility(ModuleManager mm) {
        // ChatFilter
        mm.register(new PluginModule("ChatFilter", "infrastructure/listeners", false) {
            private ChatFilterManager filterManager;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                filterManager = new ChatFilterManager();
                main.getServer().getPluginManager().registerEvents(filterManager, main);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (filterManager != null) {
                    HandlerList.unregisterAll(filterManager);
                    filterManager = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ChatFilterManager.reloadConfigStatic();
            }
        });

        // Chat
        mm.register(new PluginModule("Chat", "chat", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ChatManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                ChatManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ChatManager.reload();
            }
        });

        // VoidProtection
        mm.register(new SimpleModule("VoidProtection", "infrastructure/listeners", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                main.getServer().getPluginManager().registerEvents(new VoidProtectionListener(), main);
            }
        });
    }

    // --------------------------------------------------------------------------
    // DISPLAY
    // --------------------------------------------------------------------------

    public static void registerDisplay(ModuleManager mm) {
        // Tab
        mm.register(new PluginModule("Tab", "tab", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                TabManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                TabManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                TabManager.reload();
            }
        });

        // Scoreboard
        mm.register(new PluginModule("Scoreboard", "scoreboard", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ScoreboardManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                ScoreboardManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ScoreboardManager.reload();
            }
        });

        // BossBar
        mm.register(new PluginModule("BossBar", "infrastructure/bossbar", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                BossBarManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                BossBarManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                BossBarManager.reload();
            }
        });
    }

    // --------------------------------------------------------------------------
    // UTILITY (BotProtection — registered after registerUtility)
    // --------------------------------------------------------------------------

    public static void registerBotProtection(ModuleManager mm) {
        mm.register(new PluginModule("BotProtection", "mechanics/security/botprotect", false) {
            private BotProtectionListener listener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                this.listener = new BotProtectionListener(main);
                main.getServer().getPluginManager().registerEvents(listener, main);
                ConsoleLogger.info("[BotProtection] ✔ Anti-bot system initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (listener != null) {
                    HandlerList.unregisterAll(listener);
                    this.listener = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                if (listener != null) {
                    listener.loadConfig();
                }
            }
        });
    }

    // --------------------------------------------------------------------------
    // DISPLAY (MOTD — registered after registerDisplay)
    // --------------------------------------------------------------------------

    public static void registerMOTD(ModuleManager mm) {
        mm.register(new PluginModule("MOTD", "infrastructure/listeners/motd", false) {
            private MOTDListener listener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                this.listener = new MOTDListener();
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (listener != null) {
                    // ⛔ MUST unsubscribe from Bukkit events,
                    // otherwise a duplicate listener lingers on every /ui reload
                    HandlerList.unregisterAll(listener);
                    this.listener = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                // On reload: disable() → onDisable() nulls the listener.
                // A new listener is created in onInit() after onReloadConfig() is called,
                // hence the null check here — the icon loads in the new listener's constructor.
                if (listener != null) {
                    listener.loadIcon();
                }
            }
        });
    }

    // --------------------------------------------------------------------------
    // SECURITY (Punish and StructureIntegrity — AntiCheat stays a separate class)
    // Order in PluginStartup: registerPunish → new AntiCheatModule() → registerStructureIntegrity
    // --------------------------------------------------------------------------

    public static void registerPunish(ModuleManager mm) {
        // Punish — punishments, whitelist and blacklist
        mm.register(new SimpleModule("Punish", "infrastructure/punish", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // Initialize the managers
                // Whitelist and Blacklist register their own events

                // Register the punishment listener
                var pm = main.getServer().getPluginManager();
                pm.registerEvents(new PunishJoinListener(), main);

                // Purge kick records older than 24h at startup, then once per hour
                // (kicks are logged as active=1 and never expire on their own).
                // The guard prevents duplicate timers if the module re-initializes.
                Bukkit.getScheduler().runTaskAsynchronously(main, PunishmentManager::deleteOldKicks);
                if (!kickCleanupScheduled) {
                    kickCleanupScheduled = true;
                    Bukkit.getScheduler().runTaskTimerAsynchronously(main, PunishmentManager::deleteOldKicks,
                            20L * 60 * 60, 20L * 60 * 60); // first run after 1h, then every 1h
                }

                ConsoleLogger.info("[PunishModule] Punishment, Whitelist & Blacklist systems initialized.");
            }
        });
    }

    /** Guards against scheduling the kick cleanup timer twice on module re-init. */
    private static boolean kickCleanupScheduled = false;

    public static void registerStructureIntegrity(ModuleManager mm) {
        // StructureIntegrity — structure integrity indicator (ender chests)
        mm.register(new PluginModule("StructureIntegrity", "mechanics/features/structure_integrity", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // Initialize manager (starts ticker)
                StructureIntegrityManager.init(main);

                // Register craft listener
                StructureIntegrityCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new StructureIntegrityCraftListener(), main);

                // Register interaction listener
                main.getServer().getPluginManager().registerEvents(new StructureIntegrityListener(), main);

                ConsoleLogger.info("[StructureIntegrityModule] ✔ Initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                StructureIntegrityManager mgr = StructureIntegrityManager.getInstance();
                if (mgr != null) {
                    mgr.shutdown();
                }
            }
        });
    }

    // --------------------------------------------------------------------------
    // PARTICLE ACCELERATOR (registered before OmniscannerModule)
    // --------------------------------------------------------------------------

    public static void registerParticle(ModuleManager mm) {
        mm.register(new PluginModule("ParticleAccelerator", "mechanics/particle", false) {
            private BukkitTask movementTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                ParticleAcceleratorManager.init(main);

                // Movement task every tick
                movementTask = new ParticleMovementTask().runTaskTimer(main, 20L, 1L);

                // Register crafting recipes (Item Assembler only)
                ParticleRingCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleRingCraftListener(), main);

                ParticleEngineCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleEngineCraftListener(), main);

                ParticleSensorCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleSensorCraftListener(), main);

                ParticleInjectorCraftListener.init();
                main.getServer().getPluginManager().registerEvents(new ParticleInjectorCraftListener(), main);

                ConsoleLogger.info("[ParticleModule] ✔ Particle accelerator system initialized.");
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
    }

    // --------------------------------------------------------------------------
    // BACKGROUND
    // --------------------------------------------------------------------------

    public static void registerBackground(ModuleManager mm) {
        // Tasks
        mm.register(new PluginModule("Tasks", "infrastructure/core", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                TaskManager.getInstance().startAll((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                TaskManager.getInstance().stopAll();
            }
        });

        // AutoSave
        mm.register(new PluginModule("AutoSave", "infrastructure/database", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                AsyncAutoSaveManager.init((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                AsyncAutoSaveManager.shutdown();

                // Save all systems synchronously on shutdown
                AsyncAutoSaveManager.saveAllNow();
            }
        });

        // UpdateChecker
        mm.register(new SimpleModule("UpdateChecker", "updatechecker", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                UpdateChecker.checkAsync();
            }
        });

        // Leash
        mm.register(new PluginModule("Leash", "mechanics/features/leash", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                LeashManager.init((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                LeashManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                LeashManager.reloadConfig();
            }
        });

        // ElytraBoost
        mm.register(new SimpleModule("ElytraBoost", "mechanics/features/elytra_boost", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                ElytraBoostManager.init(main);
                main.getServer().getPluginManager().registerEvents(new ChestplateFlightListener(), main);
                main.getServer().getPluginManager().registerEvents(new NetheriteUpgradeListener(), main);
                main.getServer().getPluginManager().registerEvents(new TotemChargeListener(), main);
                TotemChargeListener.startPeriodicLoreCheck();
            }
        });

        // AutoBroadcast
        mm.register(new PluginModule("AutoBroadcast", "infrastructure/auto_broadcast", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                AutoBroadcastManager.getInstance().start((Main) plugin);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                AutoBroadcastManager.getInstance().stop();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                AutoBroadcastManager.getInstance().reload();
            }
        });
    }
}
