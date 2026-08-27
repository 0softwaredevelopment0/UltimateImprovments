package com.ultimateimprovments.core;

import com.ultimateimprovments.config.ConfigCrashSalvage;
import com.ultimateimprovments.config.ConfigIntegrityValidator;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.module.CoreModules;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.VersionCheckModule;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.PlaceholderResolver;

/**
 * PluginStartup — core-only initialization.
 * Feature modules are loaded by UI-Other (UIOther.java).
 */
public class PluginStartup {

    private final Main plugin;
    private static boolean startupPerformed = false;

    public PluginStartup(Main plugin) {
        this.plugin = plugin;
    }

    public void startupPlugin() {
        if (startupPerformed) {
            ConsoleLogger.warn("[Startup] Already performed!");
            try { new PluginShutdown(plugin).shutdownPlugin(); }
            catch (Exception e) { ConsoleLogger.warn("[Startup] Reset: " + e.getMessage()); }
        }
        startupPerformed = true;

        ConsoleLogger.init();
        com.ultimateimprovments.util.AuthCommandLogFilter.register();

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-Core v" + plugin.getDescription().getVersion());
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("");

        checkJavaVersion();
        initInfrastructure();
        initModuleSystem();
        printBanner();
        ConsoleLogger.success("[PLUGIN] UI-Core enabled!");
    }

    private void initInfrastructure() {
        Permissions.registerAll();
        com.ultimateimprovments.util.FileLogger.ensureDirectory(plugin.getDataFolder(), "DataFolder");
        loadConfigFile();
        ConfigIntegrityValidator.validate(plugin);
        DatapackModules.init(plugin);
        MessagesManager.init(plugin);
        PlaceholderResolver.init();

        if (PlaceholderResolver.isPapiAvailable()) {
            try {
                var exp = new com.ultimateimprovments.hook.UIPlaceholderExpansion();
                if (exp.register()) ConsoleLogger.info("[PAPI] Expansion registered");
            } catch (Throwable t) {
                ConsoleLogger.warn("[PAPI] Failed: " + t.getMessage());
            }
        }

        Keys.init(plugin);
        ConsoleLogger.info("[Init] Infrastructure ready.");
    }

    private void initModuleSystem() {
        ModuleManager.init(plugin);
        var mm = ModuleManager.getInstance();
        mm.register(new VersionCheckModule());
        CoreModules.registerAll(mm);
        mm.initAll();
        ConsoleLogger.info("[Init] Core modules ready.");
    }

    private void loadConfigFile() {
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        boolean existed = configFile.exists();
        plugin.saveDefaultConfig();
        if (!existed) ConsoleLogger.info("[Config] Created: config.yml");

        if (!ConfigCrashSalvage.salvage(plugin)) {
            ConfigCrashSalvage.backupWholeFile(plugin);
            if (configFile.exists()) configFile.delete();
            plugin.saveDefaultConfig();
        }
        try { plugin.reloadConfig(); }
        catch (Exception e) {
            if (configFile.exists()) configFile.delete();
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
        }
    }

    private void checkJavaVersion() {
        try {
            plugin.getClass().getClassLoader().loadClass(
                    "com.ultimateimprovments.core.DatapackInstaller");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("major version")) ConsoleLogger.warn("  Java version may be incompatible!");
        } catch (ClassNotFoundException ignored) {}
    }

    private void printBanner() {
        ConsoleLogger.info("");
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("  UI-Core v" + plugin.getDescription().getVersion());
        ConsoleLogger.info("  Server: " + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
        ConsoleLogger.info("==================================================");
        ConsoleLogger.info("");
    }

    public static void resetStartupFlag() {
        startupPerformed = false;
    }
}
