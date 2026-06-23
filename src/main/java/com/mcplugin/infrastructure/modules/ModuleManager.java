package com.mcplugin.infrastructure.modules;

import com.mcplugin.infrastructure.core.Main;

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
            plugin.getLogger().warning("[ModuleManager] Module '" + module.getName() + "' already registered!");
            return;
        }
        modules.add(module);
        moduleMap.put(module.getName(), module);
    }

    // =========================
    // INIT ALL
    // =========================

    public void initAll() {
        plugin.getLogger().info("");
        plugin.getLogger().info("===========================================");
        plugin.getLogger().info("  Initializing modules...");
        plugin.getLogger().info("===========================================");
        plugin.getLogger().info("");

        int succeeded = 0;
        int failed = 0;

        for (PluginModule module : modules) {
            boolean ok = module.initialize(plugin);
            if (ok) {
                succeeded++;
            } else {
                failed++;
                if (module.isEssential()) {
                    plugin.getLogger().severe("");
                    plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().severe("! ESSENTIAL MODULE FAILED: " + module.getName());
                    plugin.getLogger().severe("! Reason: " + module.getDisableReason());
                    plugin.getLogger().severe("! Plugin may not function correctly!");
                    plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().severe("");
                }
            }
        }

        plugin.getLogger().info("");
        plugin.getLogger().info("===========================================");
        plugin.getLogger().info("  Modules: " + succeeded + " OK, " + failed + " failed"
                + (failed > 0 ? " \u26A0" : ""));
        plugin.getLogger().info("===========================================");
        plugin.getLogger().info("");
    }

    // =========================
    // SHUTDOWN ALL (reverse order)
    // =========================

    public void shutdownAll() {
        plugin.getLogger().info("[ModuleManager] Shutting down all modules...");
        for (int i = modules.size() - 1; i >= 0; i--) {
            modules.get(i).disable(plugin);
        }
    }

    // =========================
    // RELOAD CONFIGS
    // =========================

    public void reloadAllConfigs() {
        plugin.getLogger().info("[ModuleManager] Reloading configs...");
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
