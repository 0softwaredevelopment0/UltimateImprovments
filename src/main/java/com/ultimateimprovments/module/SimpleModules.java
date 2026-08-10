package com.ultimateimprovments.module;

import com.ultimateimprovments.broadcast.AutoBroadcastManager;
import com.ultimateimprovments.chat.ChatManager;
import com.ultimateimprovments.command.PowerManager;
import com.ultimateimprovments.command.vote.VoteManager;
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
import com.ultimateimprovments.energy.machines.assembler.AssemblerListener;
import com.ultimateimprovments.energy.machines.assembler.AssemblerManager;
import com.ultimateimprovments.energy.machines.assembler.AssemblerTask;
import com.ultimateimprovments.energy.machines.furnace.ElectricFurnaceManager;
import com.ultimateimprovments.energy.machines.workbench.EnergyCraftingListener;
import com.ultimateimprovments.energy.machines.workbench.EnergyWorkbenchManager;
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
import com.ultimateimprovments.mechanics.crafting.ChunkLoaderCraftListener;
import com.ultimateimprovments.mechanics.crafting.ConcreteBucketCraftListener;
import com.ultimateimprovments.mechanics.crafting.EnderChestCraftListener;
import com.ultimateimprovments.mechanics.crafting.EntityLocatorCraftListener;
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
 * SimpleModules — реестр всех простых (тонких) модулей-обёрток.
 * <p>
 * Раньше каждый такой модуль жил в отдельном файле (~60 файлов по 17-77 строк):
 * конструктор + вызов {@code XxxManager.init()} в {@code onInit} и пустой
 * {@code onDisable}. Теперь все они собраны в этом одном файле как анонимные
 * классы, сгруппированные по доменам — ровно в том порядке, в котором их
 * регистрировал {@code PluginStartup}.
 * <p>
 * Модули с нетривиальной логикой (Core, Database, Economy, AntiCheat, ...)
 * остались отдельными классами.
 * <p>
 * ВАЖНО: эти модули регистрируются вручную через {@code PluginStartup} —
 * авто-сканирование (ModuleScanner) их не найдёт, т.к. это анонимные классы.
 */
public final class SimpleModules {

    private SimpleModules() {}

    /**
     * База для большинства модулей: onDisable — no-op.
     * Модули с очисткой/остановкой задач переопределяют onDisable
     * и наследуют PluginModule напрямую.
     */
    private abstract static class SimpleModule extends PluginModule {
        SimpleModule(String name, String path, boolean essential) {
            super(name, path, essential);
        }

        @Override
        protected void onDisable(JavaPlugin plugin) {}
    }

    // ==========================================================================
    // 🧩 ГРУППЫ РЕГИСТРАЦИИ (порядок = порядок из PluginStartup)
    // ==========================================================================

    // --------------------------------------------------------------------------
    // CORE (Database + Core — регистрируются сразу после VersionCheckModule)
    // --------------------------------------------------------------------------

    public static void registerCoreModules(ModuleManager mm) {
        // Database (essential — без БД плагин не работает)
        mm.register(new PluginModule("Database", "infrastructure/database", true) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                // SQLITE INIT
                DatabaseManager.connect();
                DatabaseInit.init();
                ConsoleLogger.info("[SQLITE] Database initialized successfully.");

                // Vote Manager (загрузить голосования из БД)
                VoteManager.init();
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                // Отменяем все таймеры голосований
                VoteManager.shutdown();
                try {
                    DatabaseManager.close();
                } catch (Exception e) {
                    ConsoleLogger.warn("[DatabaseModule] Close error: " + e.getMessage());
                }
            }
        });

        // Core (essential — базовые системы: задачи, команды, общие слушатели)
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
                // Marker'ы сохраняются в world-файлах, save не нужен
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
                // Marker'ы сохраняются в world-файлах, save не нужен
            }
        });
    }

    // --------------------------------------------------------------------------
    // ENERGY MACHINES (Assembler + Workbench — регистрируются после энерго-группы)
    // --------------------------------------------------------------------------

    public static void registerEnergyMachines(ModuleManager mm) {
        // Assembler (essential — CRAFTER + рамка наверху, авто-крафт раз в 2 тика)
        mm.register(new PluginModule("Assembler", "energy/machines/assembler", true) {
            private BukkitTask assemblerTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                AssemblerManager.init();

                // ⚠ Paper 1.21.4+: BukkitRunnable нельзя передавать в Scheduler.runTaskTimer()
                assemblerTask = new AssemblerTask().runTaskTimer((Main) plugin, 40L, 2L);

                ConsoleLogger.info("[AssemblerModule] ✔ Assembler system initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (assemblerTask != null) {
                    assemblerTask.cancel();
                    assemblerTask = null;
                }
                AssemblerManager.shutdown();
            }
        });

        // Energy Workbench (essential)
        mm.register(new PluginModule("Energy Workbench", "energy/machines/workbench", true) {
            private EnergyCraftingListener craftingListener;
            private BukkitTask lockTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                EnergyWorkbenchManager.init();
                craftingListener = new EnergyCraftingListener();
                main.getServer().getPluginManager().registerEvents(craftingListener, main);
                main.getServer().getPluginManager().registerEvents(new EnergyWorkbenchManager.RedstoneListener(), main);

                // Блокируем авто-крафт CRAFTER по редстоуну каждый тик
                lockTask = Bukkit.getScheduler().runTaskTimer(main, () -> {
                    EnergyWorkbenchManager.maintainLocks();
                }, 0L, 1L);

                // Заряжаем буферы Assembler'ов от соседних кабелей каждые 2 тика
                Bukkit.getScheduler().runTaskTimer(main, () -> {
                    EnergyWorkbenchManager.chargeAllBuffers();
                }, 0L, 2L);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (craftingListener != null) {
                    HandlerList.unregisterAll(craftingListener);
                    craftingListener = null;
                }
                if (lockTask != null) {
                    lockTask.cancel();
                    lockTask = null;
                }
            }
        });
    }

    // --------------------------------------------------------------------------
    // CRAFTING (регистрируется до Sudo)
    // --------------------------------------------------------------------------

    public static void registerCrafting(ModuleManager mm) {
        // Crafting (essential — крафты ключевая механика плагина)
        mm.register(new SimpleModule("Crafting", "mechanics/crafting", true) {
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
                pm.registerEvents(new AssemblerListener(), main);
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
    // SUDO (GitHub-style, регистрируется после CraftingModule)
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
     * Очищает sudo-состояние игрока при выходе (сессии, кулдауны, pending-команды).
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
                // Сбрасываем синглтон + отменяем таск-наблюдатель, иначе после
                // /ui reload старый наблюдатель либо останется висеть (дубликат),
                // либо init() с guard'ом не перезапустит его вовсе.
                WirelessRedstoneManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                WirelessRedstoneManager.reloadConfig();
            }
        });
    }

    // --------------------------------------------------------------------------
    // ECONOMY (регистрируется до AOEEnchantment)
    // --------------------------------------------------------------------------

    public static void registerEconomy(ModuleManager mm) {
        // Economy — валютная система (ядро + Vault + PAPI)
        mm.register(new SimpleModule("Economy", "economy", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                // 1. Ядро
                EconomyManager.init();

                // 2. Vault интеграция (только если Vault установлен)
                if (PluginHook.check("Vault", "Economy")) {
                    new VaultIntegration(plugin);
                }

                // 3. События
                var pm = plugin.getServer().getPluginManager();
                pm.registerEvents(new PlayerJoinListener(), plugin);
                pm.registerEvents(new IncomeListener(), plugin);

                // 4. PAPI расширение
                try {
                    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        new EconomyPlaceholderExpansion().register();
                        ConsoleLogger.info("[Economy] PlaceholderAPI expansion registered.");
                    }
                } catch (NoClassDefFoundError | Exception e) {
                    ConsoleLogger.info("[Economy] PlaceholderAPI not found — placeholders disabled.");
                }

                ConsoleLogger.info("[Economy] Module initialized.");
            }
        });
    }

    // --------------------------------------------------------------------------
    // AOE ENCHANTMENT (регистрируется после EconomyModule)
    // --------------------------------------------------------------------------

    public static void registerAOEEnchantment(ModuleManager mm) {
        // AoE (Area of Effect) Enchantment: REAL data-driven enchantment
        // (minecraft:aoe, registered by the UI-Datapack) + PDC mirror failsafe.
        mm.register(new SimpleModule("AOEEnchantment", "enchantment/aoe", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener
                main.getServer().getPluginManager().registerEvents(new EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[AoE] Enchantment module initialized.");
                ConsoleLogger.info("[AoE] Max level: 255 | Radius = level | Tools: pickaxe, shovel, axe, hoe");
                ConsoleLogger.info("[AoE] Sneak to disable AoE for precise mining");
            }
        });
    }

    // --------------------------------------------------------------------------
    // AUTOSMELT ENCHANTMENT (регистрируется после AOEEnchantment)
    // --------------------------------------------------------------------------

    public static void registerAutoSmeltEnchantment(ModuleManager mm) {
        // AutoSmelt: REAL data-driven enchantment (minecraft:autosmelt, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Smelts block drops.
        mm.register(new SimpleModule("AutoSmeltEnchantment", "enchantment/autosmelt", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener (smelts drops)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.autosmelt.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.autosmelt.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[AutoSmelt] Enchantment module initialized.");
                ConsoleLogger.info("[AutoSmelt] Level: 1 | Tools: pickaxe, shovel, axe, hoe");
            }
        });
    }

    // --------------------------------------------------------------------------
    // VEINMINER ENCHANTMENT (регистрируется после AutoSmeltEnchantment)
    // --------------------------------------------------------------------------

    public static void registerVeinMinerEnchantment(ModuleManager mm) {
        // VeinMiner: REAL data-driven enchantment (minecraft:veinminer, registered by
        // the UI-Datapack, max level 1) + PDC mirror failsafe. Breaks whole ore veins.
        mm.register(new SimpleModule("VeinMinerEnchantment", "enchantment/veinminer", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener (flood-fills the ore vein)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.veinminer.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.veinminer.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[VeinMiner] Enchantment module initialized.");
                ConsoleLogger.info("[VeinMiner] Level: 1 | Tool: pickaxe | Mines whole ore veins");
                ConsoleLogger.info("[VeinMiner] Sneak to disable VeinMiner for precise mining");
            }
        });
    }

    // --------------------------------------------------------------------------
    // TREECAPITATOR ENCHANTMENT (регистрируется после VeinMinerEnchantment)
    // --------------------------------------------------------------------------

    public static void registerTreeCapitatorEnchantment(ModuleManager mm) {
        // TreeCapitator: REAL data-driven enchantment (minecraft:treecapitator, registered
        // by the UI-Datapack, max level 1) + PDC mirror failsafe. Fells whole trees.
        mm.register(new SimpleModule("TreeCapitatorEnchantment", "enchantment/treecapitator", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break listener (flood-fills the tree trunk)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.treecapitator.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.treecapitator.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[TreeCapitator] Enchantment module initialized.");
                ConsoleLogger.info("[TreeCapitator] Level: 1 | Tool: axe | Fells whole trees");
                ConsoleLogger.info("[TreeCapitator] Sneak to disable TreeCapitator for precise cutting");
            }
        });
    }

    // --------------------------------------------------------------------------
    // FLIGHT ENCHANTMENT (регистрируется после TreeCapitatorEnchantment)
    // --------------------------------------------------------------------------

    public static void registerFlightEnchantment(ModuleManager mm) {
        // Flight: REAL data-driven enchantment (minecraft:flight, registered by
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

                ConsoleLogger.info("[Flight] Enchantment module initialized.");
                ConsoleLogger.info("[Flight] Level: 1 | Item: chestplate | Fly like Creative while worn");
            }
        });
    }

    // --------------------------------------------------------------------------
    // MAGNET ENCHANTMENT (регистрируется после FlightEnchantment)
    // --------------------------------------------------------------------------

    public static void registerMagnetEnchantment(ModuleManager mm) {
        // Magnet: REAL data-driven enchantment (minecraft:magnet, registered by
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

                ConsoleLogger.info("[Magnet] Enchantment module initialized.");
                ConsoleLogger.info("[Magnet] Level: 1 | Tools: pickaxe, shovel, axe, hoe | Pull: 0.5 blk/s");
            }
        });
    }

    // --------------------------------------------------------------------------
    // IGNITING ENCHANTMENT (регистрируется после MagnetEnchantment)
    // --------------------------------------------------------------------------

    public static void registerIgnitingEnchantment(ModuleManager mm) {
        // Igniting: REAL data-driven enchantment (minecraft:igniting, registered by
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

                ConsoleLogger.info("[Igniting] Enchantment module initialized.");
                ConsoleLogger.info("[Igniting] Levels: 1-255 | Armor: helmet, chestplate, leggings, boots");
                ConsoleLogger.info("[Igniting] Attackers of the wearer are set on fire for level seconds");
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
    // UTILITY (BotProtection — регистрируется после registerUtility)
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
    // DISPLAY (MOTD — регистрируется после registerDisplay)
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
                    // ⛔ ОБЯЗАТЕЛЬНО отписываемся от Bukkit событий,
                    // иначе при каждом /ui reload будет висеть дубликат listener'а
                    HandlerList.unregisterAll(listener);
                    this.listener = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                // При reload: disable() → onDisable() обнуляет listener.
                // Новый listener создаётся в onInit() после вызова onReloadConfig(),
                // поэтому здесь проверка на null — иконка загрузится в конструкторе нового listener.
                if (listener != null) {
                    listener.loadIcon();
                }
            }
        });
    }

    // --------------------------------------------------------------------------
    // SECURITY (Punish и StructureIntegrity — AntiCheat остаётся отдельным классом)
    // Порядок в PluginStartup: registerPunish → new AntiCheatModule() → registerStructureIntegrity
    // --------------------------------------------------------------------------

    public static void registerPunish(ModuleManager mm) {
        // Punish — наказания, вайтлист и блэклист
        mm.register(new SimpleModule("Punish", "infrastructure/punish", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // Инициализируем менеджеры
                // Whitelist и Blacklist регистрируют свои события сами

                // Регистрируем слушатель наказаний
                var pm = main.getServer().getPluginManager();
                pm.registerEvents(new PunishJoinListener(), main);

                ConsoleLogger.info("[PunishModule] Punishment, Whitelist & Blacklist systems initialized.");
            }
        });
    }

    public static void registerStructureIntegrity(ModuleManager mm) {
        // StructureIntegrity — индикатор целостности структур (эндер-сундуки)
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
    // PARTICLE ACCELERATOR (регистрируется до OmniscannerModule)
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

                // Сохраняем все системы синхронно при выключении
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
