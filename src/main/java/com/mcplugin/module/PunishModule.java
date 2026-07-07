package com.mcplugin.module;

import com.mcplugin.core.Main;
import com.mcplugin.punish.PunishJoinListener;
import com.mcplugin.punish.PunishmentManager;
import com.mcplugin.whitelist.BlacklistManager;
import com.mcplugin.whitelist.WhitelistManager;
import com.mcplugin.util.ConsoleLogger;

/**
 * 🛡 PunishModule — система наказаний, вайтлиста и блэклиста.
 * <p>
 * Регистрирует:
 * <ul>
 *   <li>{@link PunishmentManager} — баны, муты, кики, варны</li>
 *   <li>{@link PunishJoinListener} — проверка при входе</li>
 *   <li>{@link WhitelistManager} — кастомный вайтлист</li>
 *   <li>{@link BlacklistManager} — чёрный список</li>
 * </ul>
 */
public class PunishModule extends PluginModule {

    public PunishModule() {
        super("Punish", "infrastructure/punish", false);
    }

    @Override
    protected void onInit(org.bukkit.plugin.java.JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // Инициализируем менеджеры
        // Whitelist и Blacklist регистрируют свои события сами

        // Регистрируем слушатель наказаний
        var pm = main.getServer().getPluginManager();
        pm.registerEvents(new PunishJoinListener(), main);

        ConsoleLogger.info("[PunishModule] Punishment, Whitelist & Blacklist systems initialized.");
    }

    @Override
    protected void onDisable(org.bukkit.plugin.java.JavaPlugin plugin) {
        // Очистка не требуется
    }
}
