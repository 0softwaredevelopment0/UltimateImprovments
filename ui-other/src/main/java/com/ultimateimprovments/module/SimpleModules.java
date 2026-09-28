package com.ultimateimprovments.module;

import com.ultimateimprovments.broadcast.AutoBroadcastManager;
import com.ultimateimprovments.command.PowerManager;
import com.ultimateimprovments.command.vote.VoteManager;
import com.ultimateimprovments.core.CommandRegistrar;
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
import com.ultimateimprovments.energy.consumption.light.LightManager;
import com.ultimateimprovments.energy.generation.basic.GeneratorManager;
import com.ultimateimprovments.energy.generation.reactor.ReactorListener;
import com.ultimateimprovments.energy.generation.reactor.ReactorManager;
import com.ultimateimprovments.energy.machines.furnace.ElectricFurnaceManager;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.hook.PluginHook;
import com.ultimateimprovments.listener.BlockBreakListener;
import com.ultimateimprovments.listener.BlockPlaceListener;
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
import com.ultimateimprovments.mechanics.crafting.EntityLocatorCraftListener;
import com.ultimateimprovments.mechanics.crafting.GlassSwordCraftListener;
import com.ultimateimprovments.mechanics.crafting.HealthMeterCraftListener;
import com.ultimateimprovments.mechanics.crafting.HeavyCoreCraftListener;
import com.ultimateimprovments.mechanics.crafting.HazmatCraftListener;
import com.ultimateimprovments.mechanics.crafting.LeadIngotCraftListener;
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
import com.ultimateimprovments.mechanics.environment.sunburn.SunburnManager;
import com.ultimateimprovments.mechanics.features.blocks.BlockDmgManager;
import com.ultimateimprovments.mechanics.features.blocks.BoostedCobwebManager;
import com.ultimateimprovments.mechanics.features.blocks.ContainerTriggerManager;
import com.ultimateimprovments.mechanics.features.blocks.EnderChestManager;
import com.ultimateimprovments.mechanics.features.blocks.GlassBreakManager;
import com.ultimateimprovments.mechanics.features.blocks.TerracotaSpeedManager;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseListener;
import com.ultimateimprovments.mechanics.features.collapse.BlockCollapseManager;
import com.ultimateimprovments.mechanics.features.creativeitem.CreativeItemValidator;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityLoreCleanupListener;
import com.ultimateimprovments.mechanics.features.integrity.ItemDurabilityUtil;
import com.ultimateimprovments.mechanics.features.integrity.LowDurabilityWarningListener;
import com.ultimateimprovments.mechanics.features.integrity.PiercingListener;
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
 * Modules with non-trivial logic (Core, Database, Economy)
 * remain separate classes.
 * <p>
 * IMPORTANT: these modules are registered manually via {@code PluginStartup} —
 * auto-scanning (ModuleScanner) won't find them because they're anonymous classes.
 */
public final class SimpleModules {

    private SimpleModules() {}

    // ==========================================================================
    // 🧩 REGISTRATION GROUPS (order = PluginStartup order)
    // ==========================================================================

    // --------------------------------------------------------------------------
    // CORE (Database + Core — registered right after VersionCheckModule)
    // --------------------------------------------------------------------------

    public static void registerCoreModules(ModuleManager mm) {
        // NOTE: The "Database" module is already registered by CoreModules.registerAll()
        // in UI-Core's PluginStartup. We must NOT re-register it here (ModuleManager
        // silently skips duplicates). VoteManager.init() is called separately below
        // after all core modules are initialized.

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
                pm.registerEvents(new ShulkerBulletListener(), main);
                pm.registerEvents(FishingListener.getInstance(), main);

                BlockFrictionListener.init();
                pm.registerEvents(new BlockFrictionListener(), main);
            }
        });
    }

    /**
     * Initializes subsystems that depend on the Database module but live in UI-Other.
     * Must be called AFTER all core modules are initialized (Database is ready).
     * Previously VoteManager.init() was embedded in a duplicate Database module
     * registration which was silently skipped by ModuleManager.
     */
    public static void initPostCoreSubsystems() {
        VoteManager.init();
    }

    // --------------------------------------------------------------------------
    // SYSTEM
    // --------------------------------------------------------------------------

    public static void registerSystem(ModuleManager mm) {
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
                HazmatCraftListener.init();
                com.ultimateimprovments.mechanics.crafting.DosimeterCraftListener.init();
                HealthMeterCraftListener.init();
                OreFinderCraftListener.init();
                MobFinderCraftListener.init();
                PortableRadarCraftListener.init();
                MetalDetectorCraftListener.init();
                ConcreteBucketCraftListener.init();
                ChunkLoaderCraftListener.init();
                StructureIntegrityCraftListener.init();
                HeavyCoreCraftListener.init();
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
                pm.registerEvents(new com.ultimateimprovments.mechanics.crafting.DosimeterCraftListener(), main);
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
                com.ultimateimprovments.mechanics.environment.radiation.HazmatManager.init((Main) plugin);
                double msPerUnit = ((Main) plugin).getConfig()
                        .getDouble("radiation.dosimeter_ms_per_unit", 1.0);
                com.ultimateimprovments.mechanics.environment.radiation.DosimeterTask
                        .init((Main) plugin, msPerUnit);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                RadiationManager.getInstance().reloadConfig();
                com.ultimateimprovments.mechanics.environment.radiation.HazmatManager
                        .reloadConfig((Main) plugin);
            }
        });

        // Sunburn — without this module registration SunburnManager.init() was
        // never called (listener not registered, config not loaded), so the
        // SunburnTask ticked into a null manager every tick and the feature
        // silently did nothing.
        mm.register(new SimpleModule("Sunburn", "mechanics/environment/sunburn", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                SunburnManager.init();
            }
        });

        // ArmorEffects
        mm.register(new SimpleModule("ArmorEffects", "mechanics/features/player/armor_effects", false) {
            private Object task;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.init();
                task = new com.ultimateimprovments.mechanics.features.player.ArmorEffectsTask()
                        .runTaskTimer(plugin, 20L, 20L); // 1s heartbeat, manager owns unit intervals
                ConsoleLogger.info("[ArmorEffects] Configurable potion effects for armor sets.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (task instanceof org.bukkit.scheduler.BukkitTask bt) bt.cancel();
                task = null;
                com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.reloadConfig();
            }
        });

        // ArmorTrimEffects
        mm.register(new SimpleModule("ArmorTrimEffects", "mechanics/features/player/armor_trim_effects", false) {
            private Object task;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.init();
                task = new com.ultimateimprovments.mechanics.features.player.TrimEffectsTask()
                        .runTaskTimer(plugin, 20L, 20L); // 1s heartbeat, manager owns unit intervals
                ConsoleLogger.info("[ArmorTrimEffects] Configurable potion effects for armor TRIM materials.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (task instanceof org.bukkit.scheduler.BukkitTask bt) bt.cancel();
                task = null;
                com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.reloadConfig();
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

        // Join Invulnerable Reset (defensive immortal-player fix; disabled by default)
        mm.register(new SimpleModule("JoinInvulnerableReset", "mechanics/features/join_invulnerable_reset", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                com.ultimateimprovments.mechanics.features.player.JoinInvulnerableReset.init((Main) plugin);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                com.ultimateimprovments.mechanics.features.player.JoinInvulnerableReset.reloadConfig();
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

        // Durability (thin vanilla layer)
        mm.register(new SimpleModule("Integrity", "mechanics/features/integrity", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                ItemDurabilityUtil.init(main);
                PiercingListener.init(main);
                LowDurabilityWarningListener.init(main);
                IntegrityLoreCleanupListener.init(main);
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                ItemDurabilityUtil.reloadConfig();
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
            protected void onDisable(JavaPlugin plugin) {
                UnbreakableBreakerManager.shutdown();
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
    // SECURITY (Punish and StructureIntegrity)
    // Order in PluginStartup: registerPunish → registerStructureIntegrity
    // --------------------------------------------------------------------------

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

                // Register crafting recipes
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
