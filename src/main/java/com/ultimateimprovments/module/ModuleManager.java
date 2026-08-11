package com.ultimateimprovments.module;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import java.util.*;

/**
 * ModuleManager — оркестратор модулей плагина.
 * <p>
 * Регистрирует, инициализирует, останавливает и перезагружает модули.
 * Каждый модуль инициализируется в защищённом try-catch, поэтому
 * ошибка в одном модуле не ломает остальные.
 */
public class ModuleManager {

    private static ModuleManager instance;
    private final List<PluginModule> modules = new ArrayList<>();
    private final Map<String, PluginModule> moduleMap = new HashMap<>();
    private Main plugin;

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
        modules.add(module);
        moduleMap.put(module.getName(), module);
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
     * Включает модуль по имени. Возвращает true если успешно.
     */
    public boolean enableModule(String name) {
        PluginModule m = moduleMap.get(name);
        if (m == null) return false;
        if (m.isEnabled()) return true; // уже включён
        return m.initialize(plugin);
    }

    /**
     * Отключает модуль по имени. Возвращает true если успешно.
     */
    public boolean disableModule(String name) {
        PluginModule m = moduleMap.get(name);
        if (m == null) return false;
        if (!m.isEnabled()) return true; // уже выключен
        m.disable(plugin);
        return true;
    }

    // =========================
    // STATIC HELPERS
    // =========================

    /** Удобный статический метод для инициализации одного модуля. */
    public static boolean initModule(Main plugin, PluginModule module) {
        if (instance != null) {
            instance.register(module);
        }
        return module.initialize(plugin);
    }
}
