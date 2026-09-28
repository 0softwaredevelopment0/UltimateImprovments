package com.ultimateimprovments.core;

import com.ultimateimprovments.mechanics.features.player.AttributesManager;
import com.ultimateimprovments.mechanics.features.player.ElytraBoostManager;
import com.ultimateimprovments.mechanics.features.player.LeashManager;
import com.ultimateimprovments.mechanics.features.player.ModeProtectManager;
import com.ultimateimprovments.mechanics.features.player.ShieldSlownessManager;
import com.ultimateimprovments.mechanics.features.player.VanishManager;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.module.SimpleModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlayerModules — registration of the player addon's modules (armor/trim effects,
 * attributes, vanish, elytra boost, leash, mode-protect, shield slowness,
 * join-invulnerable). Moved out of ui-other's SimpleModules.
 */
public final class PlayerModules {

    private PlayerModules() {}

    public static void registerAll(ModuleManager mm) {
        // ArmorEffects
        mm.register(new SimpleModule("ArmorEffects", "mechanics/features/player/armor_effects", false) {
            private Object task;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.init();
                plugin.getServer().getPluginManager().registerEvents(
                        com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.getInstance(), plugin);
                task = new com.ultimateimprovments.mechanics.features.player.ArmorEffectsTask()
                        .runTaskTimer(plugin, 20L, 20L); // 1s heartbeat, manager owns unit intervals
                ConsoleLogger.info("[ArmorEffects] Configurable potion effects for armor sets.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (task instanceof org.bukkit.scheduler.BukkitTask bt) bt.cancel();
                task = null;
                if (com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.getInstance() != null) {
                    org.bukkit.event.HandlerList.unregisterAll(
                            com.ultimateimprovments.mechanics.features.player.ArmorEffectsManager.getInstance());
                }
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
                plugin.getServer().getPluginManager().registerEvents(
                        com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.getInstance(), plugin);
                task = new com.ultimateimprovments.mechanics.features.player.TrimEffectsTask()
                        .runTaskTimer(plugin, 20L, 20L); // 1s heartbeat, manager owns unit intervals
                ConsoleLogger.info("[ArmorTrimEffects] Configurable potion effects for armor TRIM materials.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (task instanceof org.bukkit.scheduler.BukkitTask bt) bt.cancel();
                task = null;
                if (com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.getInstance() != null) {
                    org.bukkit.event.HandlerList.unregisterAll(
                            com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.getInstance());
                }
                com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.shutdown();
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                com.ultimateimprovments.mechanics.features.player.TrimEffectsManager.reloadConfig();
            }
        });

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
                ElytraBoostManager.init((Main) plugin);
            }
        });
    }
}
