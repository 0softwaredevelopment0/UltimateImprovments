package com.ultimateimprovments.core;

import com.ultimateimprovments.addon.AddonListener;
import com.ultimateimprovments.addon.AddonRegistry;
import com.ultimateimprovments.config.ConfigCrashSalvage;
import com.ultimateimprovments.config.ConfigIntegrityValidator;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.module.CoreModules;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.VersionCheckModule;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
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

    /**
     * Resets the JVM-wide JAR file cache (JDK 14+: {@code JarFile.reset()}).
     * After a hot JAR replacement ({@code /ui updatejar} / {@code /ui swapjar})
     * the on-disk file changes while old cached {@code JarFile} handles may
     * still exist; reopening them throws a fatal {@code ZipError}. Resetting
     * the cache before the re-enable phase of {@code /ui reload} forces the
     * JVM to re-read the fresh JAR from disk. Safe no-op on older JDKs.
     */
    public static void clearJarFileCaches() {
        try {
            java.lang.reflect.Method reset = java.util.jar.JarFile.class
                    .getDeclaredMethod("reset");
            reset.setAccessible(true);
            reset.invoke(null);
            ConsoleLogger.info("[Reload] JarFile cache reset (post-update safety).");
        } catch (Exception ignored) {
            // JDK < 14 or reset() unavailable — no-op
        }
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
        ConsoleLogger.info("  UltimateImprovments v" + plugin.getDescription().getVersion());
        ConsoleLogger.info("  Data: plugins/" + UltimateDirs.DIR_NAME);
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

        // Shared family folder: plugins/UltimateImprovments (one folder, one DB,
        // per-addon configs — see UltimateDirs / AddonConfigManager).
        com.ultimateimprovments.util.FileLogger.ensureDirectory(
                UltimateDirs.base(), "UltimateImprovments");
        com.ultimateimprovments.util.FileLogger.ensureDirectory(
                UltimateDirs.configsDir(), "Configs");

        loadConfigFile();
        ConfigIntegrityValidator.validate(plugin);
        MessageUtil.reloadPrefix();
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

        // /ui addon — part of the addon system (Core = main, rest = addons).
        try {
            com.ultimateimprovments.command.SubCommandRegistry.getInstance()
                    .register(new com.ultimateimprovments.command.subcommands.AddonSubcommand());
        } catch (Exception e) {
            ConsoleLogger.warn("[Addons] Failed to register /ui addon: " + e.getMessage());
        }

        // /ui lang — global ru/en switch for all addon messages.
        try {
            com.ultimateimprovments.command.SubCommandRegistry.getInstance()
                    .register(new com.ultimateimprovments.command.subcommands.LangSubcommand());
        } catch (Exception e) {
            ConsoleLogger.warn("[Lang] Failed to register /ui lang: " + e.getMessage());
        }

        // Addon discovery: Core is the mandatory host, everything else is an
        // addon recognized by the plugin.yml marker (addon-for: UI-Core).
        // Addons enable AFTER Core (depend: [UI-Core]) and register themselves
        // via AddonListener; this startup pass catches anything already loaded.
        AddonRegistry.discover();
        plugin.getServer().getPluginManager().registerEvents(new AddonListener(), plugin);
    }

    private void loadConfigFile() {
        // Composite backend: Main.reloadConfig() runs UltimateDirs.migrateLegacyFolders()
        // + AddonConfigManager.init(), which generate and load every
        // configs/UI-<Addon>.toml. Broken lines are salvaged, missing keys are
        // repaired from the bundled fragments.
        try {
            plugin.reloadConfig();
        } catch (Exception e) {
            // A broken file after salvage must not kill the startup — missing keys
            // fall back to the bundled fragment defaults.
            ConsoleLogger.warn("[Config] Could not load configs: " + e.getMessage());
        }
    }

    private void checkJavaVersion() {
        try {
            plugin.getClass().getClassLoader().loadClass(
                    "com.ultimateimprovments.util.FileLogger");
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
