package com.mcplugin.module;

import com.mcplugin.economy.EconomyManager;
import com.mcplugin.economy.EconomyPlaceholderExpansion;
import com.mcplugin.economy.VaultIntegration;
import com.mcplugin.economy.listeners.IncomeListener;
import com.mcplugin.economy.listeners.PlayerJoinListener;
import com.mcplugin.hook.PluginHook;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль валютной системы.
 * <p>
 * Инициализирует:
 * <ul>
 *   <li>{@link EconomyManager} — ядро + БД</li>
 *   <li>{@link VaultIntegration} — Vault Economy провайдер</li>
 *   <li>{@link PlayerJoinListener} — дефолтный баланс при первом входе</li>
 *   <li>{@link IncomeListener} — заработок с блоков/мобов</li>
 *   <li>{@link EconomyPlaceholderExpansion} — PAPI плейсхолдеры</li>
 * </ul>
 */
public final class EconomyModule extends PluginModule {

    public EconomyModule() {
        super("Economy", "economy", false);
    }

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

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Vault и PAPI регистрируются автоматически — ничего не делаем
    }
}
