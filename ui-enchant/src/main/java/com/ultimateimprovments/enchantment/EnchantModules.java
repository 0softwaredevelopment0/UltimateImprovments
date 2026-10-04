package com.ultimateimprovments.enchantment;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.enchantment.aoe.EnchantmentListener;
import com.ultimateimprovments.enchantment.aoe.EnchantmentSyncListener;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.SimpleModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * EnchantModules - registration of every custom-enchantment module.
 * Moved out of ui-other's SimpleModules so the enchantment addon owns its
 * own registrations (UI-Enchant).
 */
public final class EnchantModules {

    private EnchantModules() {}

    /** Registers every enchantment module. */
    public static void registerAll(ModuleManager mm) {
        registerAOEEnchantment(mm);
        registerAutoSmeltEnchantment(mm);
        registerVeinMinerEnchantment(mm);
        registerTreeCapitatorEnchantment(mm);
        registerFlightEnchantment(mm);
        registerMagnetEnchantment(mm);
        registerIgnitingEnchantment(mm);
        registerLevitationEnchantment(mm);
        registerSelfDestructEnchantment(mm);
        registerDegradationEnchantment(mm);
        registerCurseTrioEnchantments(mm);
        registerAttackAoeEnchantment(mm);
        registerItemStealingEnchantment(mm);
        registerRepairingEnchantment(mm);
        registerLavaWalkerEnchantment(mm);
        registerContainerStealingEnchantment(mm);
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

                ConsoleLogger.info("[AoE] Max level: 8 | Radius = level (capped at 8) | Tools: pickaxe, shovel, axe, hoe");
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

                ConsoleLogger.info("[AutoSmelt] Levels: 1-10 | level N = N×10% smelt chance | Tools: pickaxe, shovel, axe, hoe");
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
    // BLUNTING / VULNERABILITY / DISAPPEARANCE ENCHANTMENTS
    // --------------------------------------------------------------------------

    public static void registerCurseTrioEnchantments(ModuleManager mm) {
        // Curse of Blunting: REAL data-driven enchantment (ui:blunting, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. Sharpness
        // reversed — the held weapon deals level × 0.5 LESS melee damage.
        mm.register(new SimpleModule("BluntingEnchantment", "enchantment/blunting", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Damage-reduction listener (melee hits with a blunted weapon)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.blunting.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.blunting.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[CurseOfBlunting] Levels: 1-10 | Weapons+tools (copper incl.) | −" + "0.5 dmg per level");
            }
        });

        // Vulnerability: REAL data-driven curse (ui:vulnerability, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. Protection
        // reversed — the wearer takes level × 0.75 MORE damage.
        mm.register(new SimpleModule("VulnerabilityEnchantment", "enchantment/vulnerability", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Damage-amplification listener (any damage to the wearer)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.vulnerability.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.vulnerability.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Vulnerability] Levels: 1-10 | Armor (copper incl.) | +0.75 dmg taken per level");
            }
        });

        // Disappearance: REAL data-driven curse (ui:disappearance, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. The item rolls a
        // level × 0.01% vanish chance every second (10 → 0.1%/s).
        mm.register(new SimpleModule("DisappearanceEnchantment", "enchantment/disappearance", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Vanish engine (per-second roll, silent removal)
                com.ultimateimprovments.enchantment.disappearance.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.disappearance.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Disappearance] Levels: 1-10 | Any durability item | Vanish: level × 0.01% per second");
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

                ConsoleLogger.info("[Magnet] Levels: 1-10 | Tools: pickaxe, shovel, axe, hoe | Radius: level×2 (max 20) | Pull: 1.0 blk/s");
            }
        });
    }

    // --------------------------------------------------------------------------
    // IGNITING ENCHANTMENT (registered after MagnetEnchantment)
    // --------------------------------------------------------------------------

    public static void registerIgnitingEnchantment(ModuleManager mm) {
        // Igniting: REAL data-driven enchantment (ui:igniting, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. Armor ignites the
        // attacker for level seconds when the wearer is hit.
        mm.register(new SimpleModule("IgnitingEnchantment", "enchantment/igniting", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Damage listener (ignites attackers of armored wearers)
                main.getServer().getPluginManager().registerEvents(new com.ultimateimprovments.enchantment.igniting.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.igniting.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Igniting] Levels: 1-10 | Armor: helmet, chestplate, leggings, boots");
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
        // the UI-Datapack, max level 1, NOT in #minecraft:curse so the table can
        // offer it; the description JSON gives the red tooltip)
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
        // the UI-Datapack, levels 1-10, NOT in #minecraft:curse so the table can
        // offer it; the description JSON gives the red tooltip)
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

                ConsoleLogger.info("[Degradation] Levels: 1-10 | Item: any with durability | Spends "
                        + "integrity as after level uses per second while in a player's inventory");
            }
        });
    }

    // --------------------------------------------------------------------------
    // ATTACK AOE ENCHANTMENT (registered after DegradationEnchantment)
    // --------------------------------------------------------------------------

    public static void registerAttackAoeEnchantment(ModuleManager mm) {
        // Attack AoE: REAL data-driven enchantment (ui:attack_aoe, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. Hitting one entity
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

                ConsoleLogger.info("[AttackAoE] Levels: 1-10 | Weapons: swords, axes | Radius: "
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

                ConsoleLogger.info("[ItemStealing] Levels: 1-10 | level N = N×10% steal chance | Item: fishing rod | "
                        + "A successful roll throws the item out of the hooked player toward the fisher (picked up after the throw; failed roll / empty hands → normal pull)");
                ConsoleLogger.info("[ItemStealing] Permission required to steal: ui.enchant.itemstealing.steal "
                        + "(toggle enchant.item_stealing_require_permission in UI-Other.toml)");
            }
        });
    }

    // --------------------------------------------------------------------------
    // REPAIRING ENCHANTMENT (registered after ItemStealingEnchantment)
    // --------------------------------------------------------------------------

    public static void registerRepairingEnchantment(ModuleManager mm) {
        // Repairing: REAL data-driven enchantment (ui:repairing, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. Every `level`
        // seconds an enchanted item with durability restores exactly `level`
        // durability POINTS while in a player's inventory.
        mm.register(new SimpleModule("RepairingEnchantment", "enchantment/repairing", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Integrity repair engine (sweep every second)
                com.ultimateimprovments.enchantment.repairing.EnchantmentListener.register(main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.repairing.EnchantmentSyncListener.register(main);

                ConsoleLogger.info("[Repairing] Levels: 1-10 | Item: any with durability | Restores "
                        + "`level` durability points per second, every second (higher level = faster)");
            }
        });
    }

    // --------------------------------------------------------------------------
    // LAVA WALKER ENCHANTMENT (registered after ContainerStealingEnchantment)
    // --------------------------------------------------------------------------

    public static void registerLavaWalkerEnchantment(ModuleManager mm) {
        // Lava Walker: REAL data-driven enchantment (ui:lava_walker, registered by
        // the UI-Datapack, levels 1-10) + PDC mirror failsafe. Frost Walker for
        // LAVA: lava under the wearer's feet temporarily turns into obsidian,
        // radius = level (1 → 1×1, 2 → 3×3, ..., hard-capped at 10 → 19×19);
        // created blocks melt back to lava after 20-45 s (frosted-ice style).
        mm.register(new SimpleModule("LavaWalkerEnchantment", "enchantment/lavawalker", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                // 1. Move listener (lava → obsidian conversion) + melt sweep + melt store
                com.ultimateimprovments.enchantment.lavawalker.EnchantmentListener.register((Main) plugin);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.lavawalker.EnchantmentSyncListener.register((Main) plugin);

                ConsoleLogger.info("[LavaWalker] Levels: 1-10 | Item: boots | Radius: level (cap "
                        + com.ultimateimprovments.enchantment.lavawalker.Enchantment.MAX_RADIUS
                        + ") | 1 durability per conversion pass | Melt delay: 20-45s (frosted-ice style, persists across restarts)");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                com.ultimateimprovments.enchantment.lavawalker.EnchantmentListener.shutdown();
            }
        });
    }

    public static void registerContainerStealingEnchantment(ModuleManager mm) {
        // Container Stealing: REAL data-driven enchantment (ui:container_stealing,
        // registered by the UI-Datapack, max level 1) + PDC mirror failsafe.
        // Breaking a container drops a single container that retains its contents;
        // placing it restores them.
        mm.register(new SimpleModule("ContainerStealingEnchantment", "enchantment/container_stealing", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;

                // 1. Block break / block place listener (store + restore contents)
                main.getServer().getPluginManager().registerEvents(
                        new com.ultimateimprovments.enchantment.containerstealing.EnchantmentListener(), main);

                // 2. PDC failsafe sync listener + periodic scan
                com.ultimateimprovments.enchantment.containerstealing.EnchantmentSyncListener.register(main);
                ConsoleLogger.info("[ContainerStealing] Levels: 1-10 | level N = N×10% steal chance | Tools: pickaxe, shovel, axe, hoe | "
                        + "Breaking a container drops it with its contents inside");
            }
        });
    }
}
