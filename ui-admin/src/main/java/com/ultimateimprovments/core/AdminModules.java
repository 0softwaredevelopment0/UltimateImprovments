package com.ultimateimprovments.core;

import com.ultimateimprovments.economy.EconomyManager;
import com.ultimateimprovments.economy.EconomyPlaceholderExpansion;
import com.ultimateimprovments.economy.VaultIntegration;
import com.ultimateimprovments.economy.listeners.IncomeListener;
import com.ultimateimprovments.economy.listeners.PlayerJoinListener;
import com.ultimateimprovments.hook.PluginHook;
import com.ultimateimprovments.mechanics.features.updater.UpdateChecker;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.SimpleModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AdminModules — registration of the admin addon's modules (economy + updater).
 * Moved out of ui-other's SimpleModules.
 */
public final class AdminModules {

    private AdminModules() {}

    public static void registerAll(ModuleManager mm) {
        // Economy — currency system (core + Vault + PAPI)
        mm.register(new SimpleModule("Economy", "economy", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                EconomyManager.init();

                if (PluginHook.check("Vault", "Economy")) {
                    new VaultIntegration(plugin);
                }

                var pm = plugin.getServer().getPluginManager();
                pm.registerEvents(new PlayerJoinListener(), plugin);
                pm.registerEvents(new IncomeListener(), plugin);

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

        // UpdateChecker
        mm.register(new SimpleModule("UpdateChecker", "updatechecker", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                UpdateChecker.checkAsync();
            }
        });
    }
}
