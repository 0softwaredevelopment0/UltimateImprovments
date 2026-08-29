package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import java.util.*;

/**
 * ModuleManager — orchestrator of the plugin's modules.
 * <p>
 * Registers, initializes, stops and reloads modules.
 * Each module is initialized in a guarded try-catch, so
 * an error in one module does not break the others.
 */
public class ModuleManager {

    private static ModuleManager instance;
    private final List<PluginModule> modules = new ArrayList<>();
    private final Map<String, PluginModule> moduleMap = new HashMap<>();
    private Main plugin;

    /**
     * Optional gate installed by the UI-Datapack plugin. When present, modules
     * bound to a disabled datapack part ({@code datapack.modules.*: false}) are
     * skipped at registration. When UI-Datapack is not installed, no gate is
     * active and all modules are registered.
     */
    public interface DatapackGate {
        /** @return the datapack part a module is bound to, or null if none */
        String partForModule(String moduleName);

        /** Whether the datapack part is enabled (master toggle included). */
        boolean isPartEnabled(String part);
    }

    private DatapackGate datapackGate;

    public static void init(Main plugin) {
        instance = new ModuleManager();
        instance.plugin = plugin;
    }

    public static ModuleManager getInstance() {
        return instance;
    }

    // =========================
    // MODULE REGISTRATION
    // =========================

    public void register(PluginModule module) {
        if (moduleMap.containsKey(module.getName())) {
            ConsoleLogger.warn("[ModuleManager] Module '" + module.getName() + "' already registered!");
            return;
        }

        // Datapack module gating: if a datapack part is disabled, skip the code
        // modules bound to it (custom enchantments, achievement listeners, ...).
        // The gate is provided by the UI-Datapack plugin (see setDatapackGate).
        if (datapackGate != null) {
            String datapackPart = datapackGate.partForModule(module.getName());
            if (datapackPart != null && !datapackGate.isPartEnabled(datapackPart)) {
                ConsoleLogger.info("[ModuleManager] Skipping module '" + module.getName()
                        + "' — datapack module '" + datapackPart
                        + "' is disabled (datapack.modules." + datapackPart + ": false).");
                return;
            }
        }

        modules.add(module);
        moduleMap.put(module.getName(), module);
    }

    /**
     * Installs the datapack gate (called by UI-Datapack at startup, before
     * UI-Other registers its modules).
     */
    public void setDatapackGate(DatapackGate gate) {
        this.datapackGate = gate;
    }

    /** Removes the datapack gate (called by UI-Datapack on shutdown). */
    public void clearDatapackGate() {
        this.datapackGate = null;
    }

    // =========================
    // INIT ALL
    // =========================

    public void initAll() {
        ConsoleLogger.info("");
        ConsoleLogger.info("[Modules] Enabling " + modules.size() + " modules...");

        int succeeded = 0;
        int failed = 0;
        StringBuilder failedNames = new StringBuilder();

        for (PluginModule module : modules) {
            boolean ok = module.initialize(plugin);
            if (ok) {
                succeeded++;
            } else {
                failed++;
                if (failedNames.length() > 0) failedNames.append(", ");
                failedNames.append(module.getName());
                if (module.isEssential()) {
                    ConsoleLogger.error("");
                    ConsoleLogger.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    ConsoleLogger.error("! ESSENTIAL MODULE FAILED: " + module.getName());
                    ConsoleLogger.error("! Reason: " + module.getDisableReason());
                    ConsoleLogger.error("! Plugin may not function correctly!");
                    ConsoleLogger.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    ConsoleLogger.error("");
                }
            }
        }

        if (failed > 0) {
            ConsoleLogger.warn("[Modules] Enabled " + succeeded + "/" + modules.size()
                    + ", failed: " + failedNames + " \u26A0");
        } else {
            ConsoleLogger.info("[Modules] All " + modules.size() + " modules enabled.");
        }
        ConsoleLogger.info("");
    }

    // =========================
    // SHUTDOWN ALL (reverse order)
    // =========================

    public void shutdownAll() {
        ConsoleLogger.info("[Modules] Disabling " + modules.size() + " modules...");
        int failed = 0;
        for (int i = modules.size() - 1; i >= 0; i--) {
            if (!modules.get(i).disable(plugin)) failed++;
        }
        if (failed > 0) {
            ConsoleLogger.warn("[Modules] Disabled " + (modules.size() - failed) + "/" + modules.size()
                    + ", " + failed + " module(s) errored. \u26A0");
        } else {
            ConsoleLogger.info("[Modules] All modules disabled.");
        }
    }

    // =========================
    // RELOAD CONFIGS
    // =========================

    public void reloadAllConfigs() {
        ConsoleLogger.info("[ModuleManager] Reloading configs...");
        for (PluginModule module : modules) {
            module.reloadConfig(plugin);
        }
    }

    // =========================
    // QUERIES
    // =========================

    public PluginModule getModule(String name) {
        return moduleMap.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T extends PluginModule> T getModule(Class<T> clazz) {
        for (PluginModule m : modules) {
            if (clazz.isInstance(m)) return (T) m;
        }
        return null;
    }

    public boolean isModuleEnabled(String name) {
        PluginModule m = moduleMap.get(name);
        return m != null && m.isEnabled();
    }

    public List<PluginModule> getModules() {
        return new ArrayList<>(modules);
    }

    public boolean hasFailedModules() {
        for (PluginModule m : modules) {
            if (!m.isEnabled()) return true;
        }
        return false;
    }

    // =========================
    // ENABLE / DISABLE SINGLE MODULE
    // =========================

    /**
     * Enables a module by name. Returns true on success.
     */
    public boolean enableModule(String name) {
        PluginModule m = moduleMap.get(name);
        if (m == null) return false;
        if (m.isEnabled()) return true; // already enabled
        return m.initialize(plugin);
    }

    /**
     * Disables a module by name. Returns true on success.
     */
    public boolean disableModule(String name) {
        PluginModule m = moduleMap.get(name);
        if (m == null) return false;
        if (!m.isEnabled()) return true; // already disabled
        m.disable(plugin);
        return true;
    }

    // =========================
    // STATIC HELPERS
    // =========================

    /** Convenient static method for initializing a single module. */
    public static boolean initModule(Main plugin, PluginModule module) {
        if (instance != null) {
            instance.register(module);
        }
        return module.initialize(plugin);
    }
}
